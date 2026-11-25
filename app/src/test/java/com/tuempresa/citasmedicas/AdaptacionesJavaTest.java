package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.data.remote.ApiClient;
import com.tuempresa.citasmedicas.domain.usecase.CreateAppointmentUseCase;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.TimeSlot;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas de robustez de las ADAPTACIONES a Java puro.
 *
 * Estas adaptaciones (singleton manual en vez de Hilt, callbacks en vez de
 * corrutinas, LiveData en vez de StateFlow) no cambian el comportamiento
 * observable, pero sí introducen concurrencia real: hay tres hilos en juego
 * (principal, el de OkHttp dentro del MockInterceptor y el de Room). Aquí se
 * comprueba que esa concurrencia no corrompe datos ni crea instancias dobles.
 */
public class AdaptacionesJavaTest {

    // -------------------------------------------------- hilos de fecha (real)

    /**
     * El bug original: {@code MockData.DATE_FORMAT} era un SimpleDateFormat
     * estático compartido entre el hilo de OkHttp (MockInterceptor), el hilo
     * principal (DoctorDetailActivity) y databaseWriteExecutor (AppDatabase).
     * SimpleDateFormat NO es thread-safe: con uso concurrente devuelve fechas
     * corruptas de forma intermitente.
     */
    @Test
    public void formatearFechasEnParalelo_noCorrompeElResultado() throws Exception {
        final int hilos = 12;
        final int repeticiones = 300;
        final CountDownLatch salida = new CountDownLatch(1);
        final CountDownLatch terminados = new CountDownLatch(hilos);
        final ConcurrentLinkedQueue<String> resultados = new ConcurrentLinkedQueue<>();
        final AtomicInteger errores = new AtomicInteger();

        for (int h = 0; h < hilos; h++) {
            new Thread(() -> {
                try {
                    salida.await();
                    for (int i = 0; i < repeticiones; i++) {
                        // Cada llamada crea su propio formateador: nunca se comparte.
                        String date = MockData.newDateFormatter().format(new java.util.Date());
                        resultados.add(date);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errores.incrementAndGet();
                } finally {
                    terminados.countDown();
                }
            }).start();
        }

        salida.countDown();
        assertTrue("Los hilos no terminaron a tiempo",
                terminados.await(20, TimeUnit.SECONDS));
        assertEquals("Ningun hilo debe fallar", 0, errores.get());
        assertEquals(hilos * repeticiones, resultados.size());

        // Todas las fechas deben ser válidas y del mismo día.
        SimpleDateFormat validador = new SimpleDateFormat(MockData.DATE_PATTERN,
                java.util.Locale.US);
        validador.setLenient(false);
        Set<String> distintas = new HashSet<>();
        for (String date : resultados) {
            try {
                validador.parse(date);
            } catch (ParseException e) {
                throw new AssertionError("Fecha corrupta generada en concurrencia: " + date);
            }
            distintas.add(date);
        }
        assertEquals("Todas las fechas concurrentes deben ser el mismo dia", 1, distintas.size());
    }

    @Test
    public void newDateFormatter_devuelveInstanciasIndependientes() {
        SimpleDateFormat a = MockData.newDateFormatter();
        SimpleDateFormat b = MockData.newDateFormatter();
        assertNotSame("No debe reutilizar una instancia compartida", a, b);
        assertEquals(MockData.DATE_PATTERN, a.toPattern());
        assertEquals("yyyy-MM-dd", MockData.DATE_PATTERN);
    }

    @Test
    public void mockData_sigueGenerandoFechasValidasTrasElCambio() {
        List<String> fechas = MockData.getSelectableDates();
        assertNotNull(fechas);
        assertFalse("Debe haber fechas seleccionables", fechas.isEmpty());

        SimpleDateFormat validador = new SimpleDateFormat(MockData.DATE_PATTERN,
                java.util.Locale.US);
        validador.setLenient(false);
        for (String fecha : fechas) {
            try {
                validador.parse(fecha);
            } catch (ParseException e) {
                throw new AssertionError("Fecha invalida: " + fecha);
            }
        }
    }

    @Test
    public void losSlotsDeUnDoctorSiguenSiendoCoherentes() {
        List<TimeSlot> slots = MockData.getAvailableSlots("doc_01");
        assertNotNull(slots);
        assertFalse(slots.isEmpty());

        SimpleDateFormat validador = new SimpleDateFormat(MockData.DATE_PATTERN,
                java.util.Locale.US);
        validador.setLenient(false);
        for (TimeSlot slot : slots) {
            assertNotNull(slot.getDoctorId());
            assertEquals("doc_01", slot.getDoctorId());
            try {
                validador.parse(slot.getDate());
            } catch (ParseException e) {
                throw new AssertionError("Slot con fecha invalida: " + slot.getDate());
            }
            assertTrue("Hora con formato HH:mm", slot.getTime().matches("\\d{2}:\\d{2}"));
        }
    }

    // --------------------------------------- singleton manual (en vez de Hilt)

    @Test
    public void apiClient_devuelveSiempreLaMismaInstancia() {
        assertNotNull(ApiClient.getRetrofit());
        assertSame("El singleton manual debe ser estable",
                ApiClient.getRetrofit(), ApiClient.getRetrofit());
        assertEquals(ApiClient.getRetrofit().baseUrl().toString(),
                ApiClient.getRetrofit().baseUrl().toString());
        assertNotNull(ApiClient.getApi());
        // Los proxies dinamicos de Retrofit delegan equals() en el handler, asi que
        // assertEquals daria falso incluso con el mismo objeto: hay que usar assertSame.
        assertSame("El servicio tambien se cachea", ApiClient.getApi(), ApiClient.getApi());
    }

    @Test
    public void apiClient_esSeguroBajoAccesoSinConcurrenciaPrevia() throws Exception {
        final int hilos = 8;
        final CountDownLatch salida = new CountDownLatch(1);
        final CountDownLatch terminados = new CountDownLatch(hilos);
        final ConcurrentLinkedQueue<Object> vistos = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < hilos; i++) {
            new Thread(() -> {
                try {
                    salida.await();
                    vistos.add(ApiClient.getApi());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminados.countDown();
                }
            }).start();
        }
        salida.countDown();
        assertTrue(terminados.await(15, TimeUnit.SECONDS));

        // Un unico objeto compartido: si el double-checked locking fallara,
        // aparecerian varias instancias aqui.
        Set<Object> unicos = new HashSet<>(new ArrayList<>(vistos));
        assertEquals("No debe haber servicios duplicados", 1, unicos.size());
    }

    // ------------------------- validacion de fecha sin java.time (API 26)

    private Appointment citaValida(String date) {
        Appointment a = new Appointment();
        a.setDoctorId("doc_01");
        a.setDate(date);
        a.setTime("10:00");
        a.setPatientAge(34);
        return a;
    }

    @Test
    public void unaFechaDeHoyEsValidayUnaPasadaNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String hoy = sdf.format(cal.getTime());

        cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        String ayer = sdf.format(cal.getTime());

        cal.add(java.util.Calendar.DAY_OF_MONTH, 2);
        String manana = sdf.format(cal.getTime());

        // La comparacion es lexicografica porque yyyy-MM-dd ya ordena cronologicamente.
        assertEquals("Una cita de hoy es valida",
                null, CreateAppointmentUseCase.validate(citaValida(hoy)));
        assertNotNull("Una cita de ayer debe rechazarse",
                CreateAppointmentUseCase.validate(citaValida(ayer)));
        assertEquals("Una cita de manana es valida",
                null, CreateAppointmentUseCase.validate(citaValida(manana)));
    }

    @Test
    public void laComparacionLexicograficaEquivaleAlOrdenCronologico() {
        String[][] casos = {
                {"2024-01-31", "2024-02-01", "-1"},
                {"2024-12-31", "2025-01-01", "-1"},
                {"2025-03-09", "2025-03-10", "-1"},
                {"2025-03-10", "2025-03-10", "0"},
                {"2025-03-11", "2025-03-10", "1"},
                {"2023-12-31", "2024-01-01", "-1"},
        };
        for (String[] c : casos) {
            int esperado = Integer.parseInt(c[2]);
            int real = Integer.signum(c[0].compareTo(c[1]));
            assertEquals("compareTo debe coincidir con el orden cronologico: "
                    + c[0] + " vs " + c[1], esperado, real);
        }
    }
}
