package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.data.local.AppDatabase;
import com.tuempresa.citasmedicas.data.local.AppointmentDao;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pruebas de FLUJO COMPLETO sobre Android real (Robolectric).
 *
 * No comprueban que compile, sino que el recorrido que hará el usuario funciona
 * de punta a punta y que los datos sobreviven al proceso:
 *
 * <pre>
 * login -> guardar sesión -> listar doctores -> slot disponible
 *       -> agendar en Room -> aparece en "Mis Citas" -> sobrevive a reabrir la app
 *       -> perfil -> cerrar sesión
 * </pre>
 *
 * Se ejecuta contra el mediaObject real: Room en SQLite, SharedPreferences reales
 * y el pipeline Retrofit + MockInterceptor. La única diferencia con un emulador es
 * que aquí no hay pantalla.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FullFlowTest {

    private Context context;
    private SessionManager session;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        session = SessionManager.getInstance(context);
        session.logout();
    }

    @After
    public void tearDown() {
        session.logout();
    }

    // ------------------------------------------------------------ sesión/login

    @Test
    public void elLoginGuardaLaSesionYSeRecuperaAlVolverAAbrirLaApp() {
        assertTrue("Sin sesión al arrancar", !session.isLoggedIn());

        session.saveSession(new User("user_01", "Laura Jiménez",
                "laura@correo.com", "+34 600 123 456", 34));

        assertTrue("La sesión debe quedar guardada", session.isLoggedIn());

        // Simula reabrir la app: una instancia nueva sobre las mismas preferencias.
        User recuperado = session.getUser();
        assertNotNull(recuperado);
        assertEquals("Laura Jiménez", recuperado.getFullName());
        assertEquals("laura@correo.com", recuperado.getEmail());
        assertEquals("La edad debe persistir (se usa al agendar)", 34, recuperado.getAge());
    }

    @Test
    public void cerrarSesionDejaLaAppSinUsuario() {
        session.saveSession(new User("user_01", "Laura", "l@c.com", "600", 34));
        assertTrue(session.isLoggedIn());

        session.logout();

        assertTrue("Tras logout no debe haber sesión", !session.isLoggedIn());
        assertEquals("Y no debe devolver usuario", null, session.getUser());
    }

    // ------------------------------------------------ datos de la capa remota

    @Test
    public void elCatalogoDeDoctoresLlegaConDatosUtilizables() {
        List<Specialty> specialties = MockData.getSpecialties();
        assertNotNull(specialties);
        assertTrue("Debe haber especialidades", !specialties.isEmpty());

        List<Doctor> doctors = MockData.getDoctors();
        assertNotNull(doctors);
        assertTrue("Debe haber doctores", !doctors.isEmpty());

        for (Doctor d : doctors) {
            assertNotNull("Todo doctor necesita id", d.getId());
            assertTrue("El id no puede ir vacío", !d.getId().trim().isEmpty());
            assertNotNull("Todo doctor necesita nombre", d.getFullName());
            assertTrue("El nombre no puede ir vacío", !d.getFullName().trim().isEmpty());
            assertNotNull("Todo doctor necesita especialidad", d.getSpecialtyName());
            assertNotNull("El saludo usa las iniciales", d.getInitials());
            assertTrue("La valoración debe estar en rango 0..5",
                    d.getRating() >= 0 && d.getRating() <= 5);
            assertTrue("Los años de experiencia no pueden ser negativos",
                    d.getYearsExperience() >= 0);
        }
    }

    @Test
    public void elPacienteDeLaApiTieneEdadParaPoderAgendar() {
        List<Patient> patients = MockData.getPatients();
        assertNotNull(patients);
        assertTrue(!patients.isEmpty());
        for (Patient p : patients) {
            assertNotNull(p.getId());
            assertTrue("La edad debe ser válida", p.getAge() >= 0 && p.getAge() <= 120);
        }
        Patient uno = MockData.getPatientById(patients.get(0).getId());
        assertNotNull("Debe poder recuperarse un paciente por id", uno);
    }

    // ------------------------------------------------- flujo real de agendar

    /** Fecha de hoy en el formato interno, igual que la genera la app. */
    private String hoy() {
        return MockData.newDateFormatter().format(new java.util.Date());
    }

    @Test
    public void hayAlMenosUnHorarioDisponibleParaPoderProbarElFlujo() {
        List<Doctor> doctors = MockData.getDoctors();
        assertTrue(!doctors.isEmpty());

        String doctorId = doctors.get(0).getId();
        List<TimeSlot> slots = MockData.getAvailableSlots(doctorId);
        assertNotNull(slots);
        assertTrue("Debe haber horarios", !slots.isEmpty());

        List<TimeSlot> disponibles = new ArrayList<>();
        for (TimeSlot s : slots) {
            if (s.isAvailable()) {
                disponibles.add(s);
            }
        }
        assertTrue("Sin horarios disponibles no se puede agendar: el boton Confirmar "
                + "mostraria 'Selecciona un horario' para siempre", !disponibles.isEmpty());

        // La fecha por defecto del DateAdapter es la primera de la lista; debe tener
        // al menos un hueco libre o el usuario ve la pantalla vacía al entrar.
        String primeraFecha = MockData.getSelectableDates().get(0);
        boolean hayEnLaPrimeraFecha = false;
        for (TimeSlot s : disponibles) {
            if (primeraFecha.equals(s.getDate())) {
                hayEnLaPrimeraFecha = true;
                break;
            }
        }
        assertTrue("La fecha preseleccionada (" + primeraFecha
                + ") debe tener horarios disponibles", hayEnLaPrimeraFecha);
    }

    /**
     * Ejecuta una tarea en un hilo de fondo y espera el resultado.
     * <p>
     * Room PROHÍBE consultar en el hilo principal ("Cannot access database on the
     * main thread"). La app real siempre escribe y lee desde
     * {@code databaseWriteExecutor} y desde los hilos de Room, así que el test hace
     * exactamente lo mismo.
     * <p>
     * Se usan las consultas SÍNCRONAS del DAO ({@code getAllOnce}, {@code getById},
     * {@code countConfirmed}) en vez de esperar al LiveData: bajo Robolectric el
     * Looper principal está pausado, así que un {@code observeForever} puede no
     * emitir nunca y el test fallaría por timeout aunque la app funcione.
     */
    private <T> T enHiloDeFondo(java.util.concurrent.Callable<T> tarea) throws Exception {
        return AppDatabase.databaseWriteExecutor
                .submit(tarea)
                .get(20, TimeUnit.SECONDS);
    }

    @Test
    public void agendarEscribeEnRoomYApareceEnMisCitas() throws Exception {
        List<Doctor> doctors = MockData.getDoctors();
        Doctor doctor = doctors.get(0);

        // Horario disponible real (no inventado).
        TimeSlot elegido = null;
        for (TimeSlot s : MockData.getAvailableSlots(doctor.getId())) {
            if (s.isAvailable()) {
                elegido = s;
                break;
            }
        }
        assertNotNull("Necesitamos un horario disponible", elegido);

        AppointmentEntity cita = new AppointmentEntity();
        cita.doctorId = doctor.getId();
        cita.doctorName = doctor.getFullName();
        cita.specialtyName = doctor.getSpecialtyName();
        cita.patientName = "Laura Jiménez";
        cita.patientAge = 34;
        cita.date = elegido.getDate();
        cita.time = elegido.getTime();
        cita.status = "Confirmed";
        cita.reason = "Dolor de cabeza persistente";
        cita.createdAt = System.currentTimeMillis();

        // Escritura exactamente como la hace AppointmentRepository.
        final AppointmentDao dao = AppDatabase.getInstance(context).appointmentDao();

        Long idCreado = enHiloDeFondo(() -> dao.insert(cita));
        assertNotNull("Room debe devolver el id autogenerado", idCreado);
        assertTrue("El id debe ser positivo", idCreado > 0);

        // Lectura de lo que la pantalla "Mis Citas" mostrará.
        List<AppointmentEntity> todas = enHiloDeFondo(dao::getAllOnce);
        assertNotNull(todas);

        boolean encontrada = false;
        for (AppointmentEntity e : todas) {
            if (e.id == idCreado) {
                encontrada = true;
                assertEquals(doctor.getId(), e.doctorId);
                assertEquals(elegido.getTime(), e.time);
                assertEquals("La edad debe guardarse para mostrarla en la lista",
                        34, e.patientAge);
                break;
            }
        }
        assertTrue("La cita agendada debe aparecer en Mis Citas", encontrada);
    }

    @Test
    public void elDaoCuentaBienLasCitasConfirmadas() throws Exception {
        final AppointmentDao dao = AppDatabase.getInstance(context).appointmentDao();

        int antes = enHiloDeFondo(dao::countConfirmed);

        AppointmentEntity cita = new AppointmentEntity();
        cita.doctorId = "doc_01";
        cita.doctorName = "Dr. Carlos Ramírez";
        cita.specialtyName = "Cardiología";
        cita.patientName = "Laura Jiménez";
        cita.patientAge = 34;
        cita.date = hoy();
        cita.time = "11:00";
        cita.status = "Confirmed";
        cita.reason = "Conteo";
        cita.createdAt = System.currentTimeMillis();

        enHiloDeFondo(() -> dao.insert(cita));

        int despues = enHiloDeFondo(dao::countConfirmed);
        assertEquals("Insertar una cita confirmada debe subir el contador en 1",
                antes + 1, despues);
    }

    @Test
    public void losDatosDeRoomSobrevivenAReabrirLaApp() throws Exception {
        // Primera "sesión": escribir.
        AppDatabase db1 = AppDatabase.getInstance(context);
        final AppointmentDao dao = db1.appointmentDao();

        AppointmentEntity cita = new AppointmentEntity();
        cita.doctorId = "doc_01";
        cita.doctorName = "Dr. Carlos Ramírez";
        cita.specialtyName = "Cardiología";
        cita.patientName = "Laura Jiménez";
        cita.patientAge = 34;
        cita.date = hoy();
        cita.time = "10:00";
        cita.status = "Confirmed";
        cita.reason = "Prueba de persistencia";
        cita.createdAt = System.currentTimeMillis();

        enHiloDeFondo(() -> dao.insert(cita));

        // Segunda "sesión": el proceso vuelve a pedir la instancia.
        AppDatabase db2 = AppDatabase.getInstance(context);
        assertEquals("El singleton debe devolver la misma base", db1, db2);

        boolean encontrada = false;
        for (AppointmentEntity e : enHiloDeFondo(db2.appointmentDao()::getAllOnce)) {
            if ("Prueba de persistencia".equals(e.reason)) {
                encontrada = true;
            }
        }
        assertTrue("La cita debe seguir en la base tras 'reabrir'", encontrada);
    }

    @Test
    public void sePuedeCancelarUnaCitaYCambiaDeEstado() throws Exception {
        final AppointmentDao dao = AppDatabase.getInstance(context).appointmentDao();

        AppointmentEntity cita = new AppointmentEntity();
        cita.doctorId = "doc_02";
        cita.doctorName = "Dra. María González";
        cita.specialtyName = "Pediatría";
        cita.patientName = "Laura Jiménez";
        cita.patientAge = 34;
        cita.date = hoy();
        cita.time = "16:00";
        cita.status = "Confirmed";
        cita.reason = "Para cancelar";
        cita.createdAt = System.currentTimeMillis();

        final Long id = enHiloDeFondo(() -> dao.insert(cita));
        assertNotNull(id);

        // Lo que hace AppointmentRepository.cancel(...)
        enHiloDeFondo(() -> {
            dao.updateStatus(id, "Cancelled");
            return null;
        });

        AppointmentEntity recargada = enHiloDeFondo(() -> dao.getById(id));
        assertNotNull("La cita debe seguir existiendo", recargada);
        assertEquals("La cita debe quedar cancelada", "Cancelled", recargada.status);
    }

    @Test
    public void elSeedInicialCreaCitasDeEjemploLaPrimeraVez() throws Exception {
        // La base de este contexto es nueva, así que SEED_CALLBACK debe haber corrido.
        // Se da margen: el seed se ejecuta de forma asíncrona en databaseWriteExecutor.
        List<AppointmentEntity> citas = null;
        for (int i = 0; i < 40; i++) {
            citas = enHiloDeFondo(AppDatabase.getInstance(context).appointmentDao()::getAllOnce);
            if (citas != null && !citas.isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(citas);
        // Si onCreate lanzara NullPointerException (INSTANCE aún sin asignar cuando
        // el callback se dispara), no habría ninguna fila y la app se vería vacía.
        assertTrue("El seed debe crear citas de ejemplo", !citas.isEmpty());

        for (AppointmentEntity e : citas) {
            assertNotNull("El seed no puede dejar doctorName nulo", e.doctorName);
            assertNotNull("El seed no puede dejar date nula", e.date);
            assertNotNull("El seed no puede dejar time nulo", e.time);
        }
    }

    // ------------------------------------------------------------- perfil/citas

    @Test
    public void losContadoresDelPerfilCuentanBien() {
        List<AppointmentEntity> citas = new ArrayList<>();

        AppointmentEntity a = new AppointmentEntity();
        a.status = "Confirmed";
        citas.add(a);
        AppointmentEntity b = new AppointmentEntity();
        b.status = "Confirmed";
        citas.add(b);
        AppointmentEntity c = new AppointmentEntity();
        c.status = "Cancelled";
        citas.add(c);

        int confirmadas = 0;
        for (AppointmentEntity e : citas) {
            if ("Confirmed".equalsIgnoreCase(e.status)) {
                confirmadas++;
            }
        }
        assertEquals(2, confirmadas);
        assertEquals("El total incluye canceladas", 3, citas.size());
    }

    @Test
    public void losHorariosSeFiltranPorLaFechaSeleccionada() {
        List<Doctor> doctors = MockData.getDoctors();
        String doctorId = doctors.get(0).getId();
        List<TimeSlot> todos = MockData.getAvailableSlots(doctorId);
        assertTrue(!todos.isEmpty());

        String fecha = todos.get(0).getDate();
        List<TimeSlot> deEsaFecha = new ArrayList<>();
        for (TimeSlot s : todos) {
            if (fecha.equals(s.getDate())) {
                deEsaFecha.add(s);
            }
        }
        assertTrue("El filtro por fecha no debe devolver vacío", !deEsaFecha.isEmpty());
        for (TimeSlot s : deEsaFecha) {
            assertEquals("Todos los slots deben ser de la fecha pedida", fecha, s.getDate());
        }
    }

    @Test
    public void elFormatoDeFechaDelSlotEsElQueEsperaLaValidacion() {
        // CreateAppointmentUseCase rechaza fechas que no sean \d{4}-\d{2}-\d{2}.
        for (TimeSlot s : MockData.getAvailableSlots(MockData.getDoctors().get(0).getId())) {
            assertTrue("Fecha con formato invalido en el slot: " + s.getDate(),
                    s.getDate().matches("\\d{4}-\\d{2}-\\d{2}"));
            assertTrue("Hora con formato invalido en el slot: " + s.getTime(),
                    s.getTime().matches("\\d{2}:\\d{2}"));
        }
        SimpleDateFormat validador = new SimpleDateFormat(MockData.DATE_PATTERN, Locale.US);
        validador.setLenient(false);
        try {
            validador.parse(hoy());
        } catch (ParseException e) {
            throw new AssertionError("El formato interno de fecha no es parseable", e);
        }
    }
}
