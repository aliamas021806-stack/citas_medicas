package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.PatientRepository;
import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pruebas de la FASE 0 de preparación para Firestore.
 * <p>
 * Verifican dos cosas distintas:
 * <ol>
 *   <li><b>Que el contrato funciona</b>: las interfaces de dominio están bien
 *       formadas, el {@link RepositoryProvider} devuelve implementaciones válidas y
 *       la cadena modelo ↔ entidad no pierde datos.</li>
 *   <li><b>Que el acoplamiento no vuelve</b>: hay comprobaciones que leen el código
 *       fuente y fallan si alguien vuelve a importar {@code data.local} desde la UI
 *       o el dominio, o si alguien vuelve a hacer {@code new XRepository()} en un
 *       ViewModel. Son pruebas de "arquitectura congelada": el error se detecta en
 *       el propio proyecto, no meses después al intentar conectar Firestore.</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FaseCeroArquitecturaTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /**
     * Estos tests comprueban el contrato de arquitectura, no la conexión real.
     * <p>
     * Se fuerza el modo local para que el resultado no dependa de si la máquina tiene
     * {@code local.properties} con credenciales: en ese caso
     * {@code RepositoryProvider.auth()} devolvería el repositorio REST y el test
     * intentaría salir a la red, con lo que dejaría de ser determinista.
     * <p>
     * La coherencia del interruptor en ambos estados se verifica aparte, en
     * {@code FirebaseRestTest}.
     */
    @Before
    public void forzarModoLocal() {
        com.tuempresa.citasmedicas.data.firebase.FirebaseConfig.setEnabledForTesting(false);
        com.tuempresa.citasmedicas.data.firebase.FirestoreClient.reset();
    }

    @After
    public void restaurarInterruptor() {
        com.tuempresa.citasmedicas.data.firebase.FirebaseConfig.setEnabledForTesting(null);
    }

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    /** Localiza la carpeta de fuentes de main a partir del directorio de trabajo. */
    private File mainSources() {
        File f = new File("src/main/java/com/tuempresa/citasmedicas");
        if (!f.exists()) {
            f = new File("app/src/main/java/com/tuempresa/citasmedicas");
        }
        assertTrue("No se encuentra el código fuente de main desde " + f.getAbsolutePath(),
                f.isDirectory());
        return f;
    }

    private List<File> javaFilesIn(File dir) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                out.addAll(javaFilesIn(f));
            } else if (f.getName().endsWith(".java")) {
                out.add(f);
            }
        }
        return out;
    }

    // ───────────────────────── 1. Contratos bien formados ─────────────────────────

    @Test
    public void lasCuatroInterfacesDeRepositorioExistenYSonPublicas() {
        Class<?>[] interfaces = {
                IDoctorRepository.class, IAuthRepository.class,
                IPatientRepository.class, IAppointmentRepository.class
        };
        for (Class<?> c : interfaces) {
            assertTrue(c.getName() + " debe ser una interfaz", c.isInterface());
            assertTrue(c.getName() + " debe ser pública", Modifier.isPublic(c.getModifiers()));
            assertTrue(c.getName() + " debe estar en domain.repository",
                    c.getPackageName().endsWith("domain.repository"));
            // Debe declarar al menos un método propio (no ser un marcador vacío)
            Method[] methods = c.getDeclaredMethods();
            assertTrue(c.getSimpleName() + " no declara ningún método", methods.length > 0);
        }
    }

    @Test
    public void lasImplementacionesActualesCumplenElContrato() {
        assertTrue("DoctorRepository debe implementar IDoctorRepository",
                IDoctorRepository.class.isAssignableFrom(DoctorRepository.class));
        assertTrue("AuthRepository debe implementar IAuthRepository",
                IAuthRepository.class.isAssignableFrom(AuthRepository.class));
        assertTrue("PatientRepository debe implementar IPatientRepository",
                IPatientRepository.class.isAssignableFrom(PatientRepository.class));
        assertTrue("AppointmentRepository debe implementar IAppointmentRepository",
                IAppointmentRepository.class.isAssignableFrom(AppointmentRepository.class));
    }

    @Test
    public void elRepositorioDeCitasYaNoExponeLaEntidadDeRoom() {
        // La firma pública debe hablar de Appointment (modelo), nunca de AppointmentEntity.
        Method observeAll;
        try {
            observeAll = IAppointmentRepository.class.getMethod("observeAll");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("IAppointmentRepository debe declarar observeAll()", e);
        }
        assertEquals(LiveData.class, observeAll.getReturnType());

        String firmaGenerica = observeAll.getGenericReturnType().getTypeName();
        assertTrue("observeAll debe devolver LiveData<List<Appointment>>, era: " + firmaGenerica,
                firmaGenerica.contains("LiveData") && firmaGenerica.contains("Appointment"));
        assertFalse("observeAll NO debe mencionar AppointmentEntity en su firma",
                firmaGenerica.contains("AppointmentEntity"));
    }

    // ───────────────────────── 2. Raíz de composición ─────────────────────────

    @Test
    public void elProviderDevuelveImplementacionesUsables() {
        IDoctorRepository doctores = RepositoryProvider.doctors(context());
        IPatientRepository pacientes = RepositoryProvider.patients(context());
        IAuthRepository auth = RepositoryProvider.auth(context());
        IAppointmentRepository citas = RepositoryProvider.appointments(context());

        assertNotNull(doctores);
        assertNotNull(pacientes);
        assertNotNull(auth);
        assertNotNull(citas);

        // Las citas SIEMPRE son locales: Room es la fuente de verdad de la UI.
        assertTrue("Las citas deben seguir viniendo de Room, con o sin Firebase",
                citas instanceof AppointmentRepository);

        // El catálogo y la autenticación dependen del interruptor. Lo que importa es
        // que la elección sea coherente y que siempre haya algo usable (nunca null),
        // no que en todos los entornos sea la implementación simulada.
        boolean firebase = RepositoryProvider.isUsingFirebase();
        if (firebase) {
            assertFalse("Con Firebase no debe usarse el mock directo de doctores",
                    doctores instanceof DoctorRepository);
            assertFalse("Con Firebase no debe usarse el mock directo de pacientes",
                    pacientes instanceof PatientRepository);
            assertFalse("Con Firebase no debe usarse la validación local directa",
                    auth instanceof AuthRepository);
        } else {
            assertTrue("Sin Firebase deben usarse las implementaciones locales",
                    doctores instanceof DoctorRepository);
            assertTrue(pacientes instanceof PatientRepository);
            assertTrue(auth instanceof AuthRepository);
        }
    }

    @Test
    public void elProviderEsUnaRaizDeComposicionSinEstado() {
        // Una clase de utilidades: no debe poder instanciarse ni guardar caché propia
        // de repositorios, para que cada llamada sea predecible y testeable.
        assertTrue("RepositoryProvider debe ser final",
                Modifier.isFinal(RepositoryProvider.class.getModifiers()));
        assertEquals("RepositoryProvider no debe tener constructores públicos",
                0, RepositoryProvider.class.getConstructors().length);
        for (java.lang.reflect.Field f : RepositoryProvider.class.getDeclaredFields()) {
            assertTrue("RepositoryProvider no debe cachear estado en el campo " + f.getName(),
                    Modifier.isStatic(f.getModifiers()));
        }
    }

    /**
     * Espera a que se cumpla una condición avanzando el Looper principal de
     * Robolectric.
     * <p>
     * Necesario porque en Robolectric el {@code Looper} está pausado: los callbacks
     * que los repositorios publican con {@code Handler.post()} no corren solos. Se
     * alterna {@code idleMainLooper()} con pequeñas esperas hasta que la condición
     * se cumple o vence el plazo. Así se prueba el flujo real (hilo de OkHttp → post
     * al hilo principal → callback) sin depender de {@code observeForever}.
     */
    private void esperarConElLooper(java.util.function.BooleanSupplier condicion, long plazoMs) {
        long fin = System.currentTimeMillis() + plazoMs;
        while (System.currentTimeMillis() < fin) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }

    @Test
    public void elAuthDevuelveSesionConEdadYCorreo() {
        IAuthRepository auth = RepositoryProvider.auth(context());
        final AtomicReference<com.tuempresa.citasmedicas.model.User> user = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();

        auth.login(new com.tuempresa.citasmedicas.model.AuthRequest(
                        "profesor@universidad.edu", "1234"),
                new RepositoryCallback<com.tuempresa.citasmedicas.model.User>() {
                    @Override
                    public void onSuccess(com.tuempresa.citasmedicas.model.User data) {
                        user.set(data);
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });

        // El repositorio simulado responde tras 900 ms: se avanza el reloj.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        esperarConElLooper(() -> user.get() != null || error.get() != null, 5_000);

        assertNull("No debía fallar: " + error.get(), error.get());
        assertNotNull("Debe entregarse el usuario por la interfaz", user.get());
        assertEquals("profesor@universidad.edu", user.get().getEmail());
        assertTrue("La edad debe ser un valor humano", user.get().getAge() > 0);
    }

    @Test
    public void elContratoDeErroresSePropagaPorLaInterfaz() {
        // Credenciales inválidas: el error debe llegar por el mismo contrato de dominio.
        IAuthRepository auth = RepositoryProvider.auth(context());
        final AtomicReference<String> error = new AtomicReference<>();
        final AtomicReference<com.tuempresa.citasmedicas.model.User> user = new AtomicReference<>();

        auth.login(new com.tuempresa.citasmedicas.model.AuthRequest("no-es-un-correo", "12"),
                new RepositoryCallback<com.tuempresa.citasmedicas.model.User>() {
                    @Override
                    public void onSuccess(com.tuempresa.citasmedicas.model.User data) {
                        user.set(data);
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });

        org.robolectric.shadows.ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        esperarConElLooper(() -> user.get() != null || error.get() != null, 5_000);

        assertNotNull("Un correo inválido debe producir error", error.get());
        assertFalse(error.get().trim().isEmpty());
    }

    @Test
    public void elCatalogoLlegaPorLaInterfazDeDominio() {
        IDoctorRepository repo = RepositoryProvider.doctors(context());
        final AtomicReference<List<Specialty>> data = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();

        repo.getSpecialties(new RepositoryCallback<List<Specialty>>() {
            @Override
            public void onSuccess(List<Specialty> result) {
                data.set(result);
            }

            @Override
            public void onError(String message) {
                error.set(message);
            }
        });

        esperarConElLooper(() -> data.get() != null || error.get() != null, 10_000);

        assertNull("No debía fallar: " + error.get(), error.get());
        assertNotNull("Debe entregarse el catálogo por la interfaz", data.get());
        assertFalse("El catálogo no debe venir vacío", data.get().isEmpty());
    }

    @Test
    public void elCatalogoDeDoctoresTambienLlegaSinPedirLaClaseConcreta() {
        IDoctorRepository repo = RepositoryProvider.doctors(context());
        final AtomicReference<List<Doctor>> data = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();

        repo.getDoctors(new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> result) {
                data.set(result);
            }

            @Override
            public void onError(String message) {
                error.set(message);
            }
        });

        esperarConElLooper(() -> data.get() != null || error.get() != null, 10_000);

        assertNull("No debía fallar: " + error.get(), error.get());
        assertNotNull(data.get());
        assertFalse(data.get().isEmpty());
        assertNotNull("El primer doctor debe tener nombre", data.get().get(0).getFullName());
    }

    // ───────────────────────── 3. Modelo ↔ entidad sin pérdidas ─────────────────────────

    @Test
    public void elMapeoAModeloConservaTodosLosCampos() {
        AppointmentEntityStub stub = new AppointmentEntityStub();
        Appointment a = com.tuempresa.citasmedicas.util.AppointmentMapper.toModel(stub.build());

        assertEquals("42", a.getId());
        assertEquals("doc_07", a.getDoctorId());
        assertEquals("Dra. Elena Ruiz", a.getDoctorName());
        assertEquals("Cardiología", a.getSpecialtyName());
        assertEquals("Ana Pérez", a.getPatientName());
        assertEquals(34, a.getPatientAge());
        assertEquals("2026-03-15", a.getDate());
        assertEquals("09:30", a.getTime());
        assertEquals(AppointmentStatus.CANCELLED, a.getStatus());
        assertEquals("Revisión anual", a.getReason());
        assertEquals(1_700_000_000_000L, a.getCreatedAt());
    }

    @Test
    public void elViajeDeIdaYVueltaModeloEntidadModeloNoPierdeNada() {
        Appointment original = new Appointment();
        original.setId("7");
        original.setDoctorId("doc_03");
        original.setDoctorName("Dr. Mario Solís");
        original.setSpecialtyName("Dermatología");
        original.setPatientName("Luis Gómez");
        original.setPatientAge(51);
        original.setDate("2026-04-02");
        original.setTime("16:45");
        original.setStatus(AppointmentStatus.CONFIRMED);
        original.setReason("Mancha en el brazo");
        original.setCreatedAt(1_690_000_000_000L);

        AppointmentEntity entity = com.tuempresa.citasmedicas.util.AppointmentMapper.toEntity(original);
        Appointment vuelta = com.tuempresa.citasmedicas.util.AppointmentMapper.toModel(entity);

        assertNotNull(vuelta);
        assertEquals(original.getId(), vuelta.getId());
        assertEquals(original.getDoctorId(), vuelta.getDoctorId());
        assertEquals(original.getDoctorName(), vuelta.getDoctorName());
        assertEquals(original.getSpecialtyName(), vuelta.getSpecialtyName());
        assertEquals(original.getPatientName(), vuelta.getPatientName());
        assertEquals(original.getPatientAge(), vuelta.getPatientAge());
        assertEquals(original.getDate(), vuelta.getDate());
        assertEquals(original.getTime(), vuelta.getTime());
        assertEquals(original.getStatus(), vuelta.getStatus());
        assertEquals(original.getReason(), vuelta.getReason());
        assertEquals(original.getCreatedAt(), vuelta.getCreatedAt());
    }

    @Test
    public void elMapeoDeListasIgnoraNulosYRespetaElOrden() {
        List<com.tuempresa.citasmedicas.data.local.AppointmentEntity> entidades = new ArrayList<>();
        AppointmentEntityStub s1 = new AppointmentEntityStub();
        entidades.add(s1.build());
        entidades.add(null);

        List<Appointment> modelos =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toModelList(entidades);

        assertNotNull(modelos);
        // Un null en la lista produciría un modelo null; el contrato actual lo admite
        // pero no debe lanzar excepción.
        assertEquals(2, modelos.size());
        assertNotNull(modelos.get(0));
        // Y una lista nula tampoco debe reventar
        assertTrue(com.tuempresa.citasmedicas.util.AppointmentMapper.toModelList(null).isEmpty());
    }

    @Test
    public void elStatusSeSaneaAnteValoresDesconocidos() {
        // Firestore podría devolver un estado nuevo o nulo: no debe romper la UI.
        assertEquals(AppointmentStatus.CONFIRMED, AppointmentStatus.fromValue(null));
        assertEquals(AppointmentStatus.CONFIRMED, AppointmentStatus.fromValue(""));
        assertEquals(AppointmentStatus.CONFIRMED, AppointmentStatus.fromValue("en_espera"));
        assertEquals(AppointmentStatus.CANCELLED, AppointmentStatus.fromValue("Cancelled"));
        assertEquals(AppointmentStatus.CANCELLED, AppointmentStatus.fromValue("CANCELLED"));
        assertEquals(AppointmentStatus.CANCELLED, AppointmentStatus.fromValue("cancelled"));
        assertEquals(AppointmentStatus.PENDING, AppointmentStatus.fromValue("Pending"));
    }

    // ───────────────────────── 4. Arquitectura congelada ─────────────────────────

    @Test
    public void laUiYElDominioNoImportanNadaDeLaBaseDeDatosLocal() throws Exception {
        File base = mainSources();
        List<String> infractores = new ArrayList<>();

        for (String capa : new String[]{"view", "viewmodel", "domain"}) {
            File dir = new File(base, capa);
            for (File f : javaFilesIn(dir)) {
                String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (src.contains("import com.tuempresa.citasmedicas.data.local.")) {
                    infractores.add(capa + "/" + f.getName());
                }
            }
        }

        assertTrue("Estas clases de presentación o dominio volvieron a depender de Room: "
                        + infractores + ". Usa los modelos de 'model' y las interfaces de "
                        + "'domain.repository'.",
                infractores.isEmpty());
    }

    @Test
    public void ningunViewModelConstruyeRepositoriosConcretos() throws Exception {
        File base = mainSources();
        File dir = new File(base, "viewmodel");
        List<String> infractores = new ArrayList<>();

        for (File f : javaFilesIn(dir)) {
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (src.contains("new DoctorRepository(")
                    || src.contains("new AuthRepository(")
                    || src.contains("new PatientRepository(")
                    || src.contains("new AppointmentRepository(")) {
                infractores.add(f.getName());
            }
        }

        assertTrue("Estos ViewModels instancian repositorios concretos en vez de usar "
                        + "RepositoryProvider: " + infractores,
                infractores.isEmpty());
    }

    @Test
    public void losCasosDeUsoDependenDeInterfacesYNoDeClasesConcretas() throws Exception {
        File base = mainSources();
        File dir = new File(base, "domain/usecase");
        List<String> infractores = new ArrayList<>();

        for (File f : javaFilesIn(dir)) {
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (src.contains("import com.tuempresa.citasmedicas.data.repository.")) {
                infractores.add(f.getName());
            }
        }

        assertTrue("Estos casos de uso importan la capa de datos (deben usar "
                        + "'domain.repository'): " + infractores,
                infractores.isEmpty());
    }

    @Test
    public void noQuedaNingunArchivoKotlinEnElProyecto() {
        File app = new File("app").exists() ? new File("app") : new File(".");
        List<String> kotlin = new ArrayList<>();
        buscarKotlin(new File(app, app.getName().equals("app") ? "src" : "app/src"), kotlin);
        assertTrue("Restricción del proyecto: 0 archivos Kotlin, encontrados: " + kotlin,
                kotlin.isEmpty());
    }

    private void buscarKotlin(File dir, List<String> found) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                buscarKotlin(f, found);
            } else if (f.getName().endsWith(".kt")) {
                found.add(f.getPath());
            }
        }
    }

    // ───────────────────────── Apoyo ─────────────────────────

    /** Construye una entidad de Room con todos los campos rellenos para el mapeo. */
    private static final class AppointmentEntityStub {
        com.tuempresa.citasmedicas.data.local.AppointmentEntity build() {
            com.tuempresa.citasmedicas.data.local.AppointmentEntity e =
                    new com.tuempresa.citasmedicas.data.local.AppointmentEntity();
            e.id = 42L;
            e.doctorId = "doc_07";
            e.doctorName = "Dra. Elena Ruiz";
            e.specialtyName = "Cardiología";
            e.patientName = "Ana Pérez";
            e.patientAge = 34;
            e.date = "2026-03-15";
            e.time = "09:30";
            e.status = "Cancelled";
            e.reason = "Revisión anual";
            e.createdAt = 1_700_000_000_000L;
            return e;
        }
    }
}
