package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.data.local.AppDatabase;
import com.tuempresa.citasmedicas.data.local.AppointmentDao;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.util.AuthValidator;
import com.tuempresa.citasmedicas.util.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pruebas de las tres correcciones pedidas tras probar la app en el teléfono:
 * <ol>
 *   <li>El <b>expediente</b> del paciente (teléfono, grupo sanguíneo, aseguradora y
 *       alergias) lo escribe el usuario; no se genera aleatoriamente.</li>
 *   <li>En <b>"Mis Citas"</b> cada paciente ve <u>sólo</u> sus citas. Antes aparecían
 *       las de otro paciente ("Laura Jiménez") porque la tabla se leía completa y la
 *       base se sembraba con citas de ejemplo a su nombre.</li>
 *   <li>En <b>Perfil</b> se muestran los datos del usuario en sesión. Antes se pedía
 *       siempre el paciente {@code pat_01} del mock, que es Laura Jiménez.</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PerfilYCitasPorUsuarioTest {

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Base de datos en memoria: cada prueba parte de un estado limpio.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        SessionManager.getInstance(context).logout();
    }

    @After
    public void tearDown() {
        db.close();
        SessionManager.getInstance(context).logout();
    }

    // ───────────────── 1. Registro con expediente escrito a mano ─────────────────

    @Test
    public void elRegistroConservaElExpedienteQueEscribeElUsuario() {
        User user = registrar(
                "Ali", "Hassan", "ali@correo.com", 25,
                "+34 611 22 33 44", "O+", "Sanitas", "Penicilina");

        assertEquals("El nombre debe conservarse", "Ali", user.getFirstName());
        assertEquals("Los apellidos deben conservarse", "Hassan", user.getLastName());
        assertEquals("El nombre completo debe unir ambos",
                "Ali Hassan", user.getFullName());
        assertEquals("El teléfono debe ser el que escribió el usuario, no uno inventado",
                "+34 611 22 33 44", user.getPhone());
        assertEquals("El grupo sanguíneo debe ser el escrito", "O+", user.getBloodType());
        assertEquals("La aseguradora debe ser la escrita", "Sanitas", user.getInsurance());
        assertEquals("Las alergias deben ser las escritas",
                "Penicilina", user.getAllergies());
    }

    @Test
    public void elTelefonoYaNoSeInventaAlNoEscribirlo() {
        User user = registrar("Ana", "Pérez", "ana@correo.com", 30, "", "", "", "");
        assertTrue("Sin teléfono escrito, el campo debe quedar vacío (antes era +34 600 123 456)",
                user.getPhone() == null || user.getPhone().isEmpty());
    }

    @Test
    public void cadaCuentaTieneSuPropioIdDeUsuario() {
        User uno = registrar("Ana", "Pérez", "ana@correo.com", 30, "611222333", "A+", "", "");
        User otro = registrar("Luis", "Gómez", "luis@correo.com", 40, "644555666", "B-", "", "");
        assertFalse("Dos cuentas distintas no pueden compartir el id de sesión: "
                        + "compartirían las citas",
                uno.getId().equals(otro.getId()));
    }

    @Test
    public void elExpedienteSobreviveAlCerrarSesionYVolverAEntrar() {
        User original = registrar("Ali", "Hassan", "ali@correo.com", 25,
                "+34 611 22 33 44", "O+", "Sanitas", "Penicilina");
        SessionManager.getInstance(context).saveSession(original);

        User recuperado = SessionManager.getInstance(context).getUser();
        assertNotNull(recuperado);
        assertEquals("El teléfono debe persistir", "+34 611 22 33 44", recuperado.getPhone());
        assertEquals("El grupo sanguíneo debe persistir", "O+", recuperado.getBloodType());
        assertEquals("La aseguradora debe persistir", "Sanitas", recuperado.getInsurance());
        assertEquals("Las alergias deben persistir", "Penicilina", recuperado.getAllergies());
        assertTrue("El expediente debe considerarse completo",
                SessionManager.getInstance(context).hasMedicalRecord());
    }

    @Test
    public void elTelefonoSeValidaYRechazaLoQueNoEsUnNumero() {
        assertTrue(AuthValidator.isValidPhone("+34 611 22 33 44"));
        assertTrue(AuthValidator.isValidPhone("611223344"));
        assertTrue(AuthValidator.isValidPhone("611-22-33-44"));
        assertFalse("Las letras no son un teléfono", AuthValidator.isValidPhone("abcdefgh"));
        assertFalse("Vacío no es un teléfono", AuthValidator.isValidPhone("   "));
        assertFalse("Dos dígitos no son un teléfono", AuthValidator.isValidPhone("12"));
        assertNotNull("Debe dar un mensaje de error útil",
                AuthValidator.validateMedicalRecord(""));
    }

    // ───────────────── 2. Cada paciente ve sólo sus citas ─────────────────

    @Test
    public void cadaUsuarioVeUnicamenteSusCitas() throws Exception {
        final AppointmentDao dao = db.appointmentDao();

        enHiloDeFondo(() -> dao.insert(cita("uid_ali", "Ali Hassan")));
        enHiloDeFondo(() -> dao.insert(cita("uid_ali", "Ali Hassan")));
        enHiloDeFondo(() -> dao.insert(cita("uid_luis", "Luis Gómez")));

        List<AppointmentEntity> deAli = enHiloDeFondo(() -> dao.getByOwnerOnce("uid_ali"));
        List<AppointmentEntity> deLuis = enHiloDeFondo(() -> dao.getByOwnerOnce("uid_luis"));

        assertEquals("Ali debe ver exactamente sus 2 citas", 2, deAli.size());
        assertEquals("Luis debe ver exactamente su 1 cita", 1, deLuis.size());

        for (AppointmentEntity e : deAli) {
            assertEquals("Ninguna cita de Ali puede ser de otro dueño", "uid_ali", e.ownerId);
            assertFalse("Ali no debe ver la cita de Luis",
                    "Luis Gómez".equals(e.patientName));
        }
    }

    @Test
    public void elFlujoObservableTambienFiltraPorUsuario() throws Exception {
        final AppointmentDao dao = db.appointmentDao();
        enHiloDeFondo(() -> dao.insert(cita("uid_ali", "Ali Hassan")));
        enHiloDeFondo(() -> dao.insert(cita("uid_luis", "Luis Gómez")));

        LiveData<List<AppointmentEntity>> flujo = dao.observeByOwner("uid_ali");
        assertNotNull("Debe existir un flujo observable por dueño", flujo);
        // La consulta SQL es la garantía: se comprueba sobre el código fuente.
        String fuente = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/local/AppointmentDao.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/local/AppointmentDao.java");
        assertTrue("El DAO debe filtrar por owner_id en la consulta observable",
                fuente.contains("WHERE owner_id = :ownerId"));
        assertFalse("El DAO no debe exponer un 'observeAll' sin filtrar: "
                        + "eso era lo que mostraba las citas de todos",
                fuente.contains("observeAll"));
    }

    @Test
    public void lasCitasDeEjemploDeOtraPersonaYaNoSeSiembran() throws Exception {
        String base = sinComentarios(leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/local/AppDatabase.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/local/AppDatabase.java"));
        assertFalse("No debe quedar ningún sembrado de citas de ejemplo",
                base.contains("seedIfEmpty"));
        assertFalse("No debe quedar el método que creaba las citas de ejemplo",
                base.contains("demoAppointments"));
        assertFalse("Ninguna cita debe asignarse a nombre de Laura: eso era el sembrado",
                base.contains("patientName = \"Laura"));
        // El nombre sí puede aparecer, pero sólo en el DELETE de la migración, que es
        // justamente lo que elimina las filas heredadas de versiones anteriores.
        if (base.contains("Laura Jiménez")) {
            assertTrue("El nombre sólo puede aparecer para borrar las citas heredadas",
                    base.contains("DELETE FROM appointments WHERE patient_name = 'Laura Jiménez'"));
        }
        assertTrue("La migración debe borrar las citas de ejemplo heredadas",
                base.contains("MIGRACION_2_3"));

        // Y una base recién creada debe estar vacía: nada de citas de otros.
        List<AppointmentEntity> todas = enHiloDeFondo(() -> db.appointmentDao().getAllOnce());
        assertTrue("Una instalación nueva no debe traer citas de otros pacientes",
                todas.isEmpty());
    }

    @Test
    public void unaCitaSeGuardaConElDuenoDeLaSesion() {
        User user = registrar("Ali", "Hassan", "ali@correo.com", 25,
                "611223344", "O+", "Sanitas", "Penicilina");
        SessionManager.getInstance(context).saveSession(user);
        assertEquals("La sesión debe exponer el id del usuario",
                user.getId(), SessionManager.getInstance(context).getUserId());

        Appointment cita = new Appointment();
        cita.setDoctorId("doc_01");
        cita.setDoctorName("Dr. Carlos Ramírez");
        cita.setPatientName(user.getFullName());
        cita.setOwnerId(user.getId());

        AppointmentEntity entidad =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toEntity(cita);
        assertEquals("El dueño debe llegar a Room", user.getId(), entidad.ownerId);

        Appointment vuelta =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toModel(entidad);
        assertEquals("El dueño debe sobrevivir al mapeo", user.getId(), vuelta.getOwnerId());
    }

    // ───────────────── 3. El perfil no pide el paciente de otro ─────────────────

    @Test
    public void elPerfilUsaLaSesionYNoUnPacienteFijo() throws Exception {
        String perfil = sinComentarios(leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/view/main/ProfileFragment.java",
                "app/src/main/java/com/tuempresa/citasmedicas/view/main/ProfileFragment.java"));
        assertFalse("El perfil ya no debe pedir el paciente 'pat_01' (Laura Jiménez)",
                perfil.contains("pat_01"));
        assertFalse("El perfil no debe cargar un paciente ajeno",
                perfil.contains("loadPatient("));
        assertTrue("El perfil debe pintar el expediente desde la sesión",
                perfil.contains("renderPatientFromSession"));

        String detalle = sinComentarios(leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/view/appointment/DoctorDetailActivity.java",
                "app/src/main/java/com/tuempresa/citasmedicas/view/appointment/DoctorDetailActivity.java"));
        assertFalse("La pantalla de agendar tampoco debe usar 'pat_01'",
                detalle.contains("pat_01"));
        assertTrue("Debe tomar el nombre del usuario en sesión",
                detalle.contains("sessionUser"));
    }

    @Test
    public void elPerfilMuestraLosCamposDelExpedienteDelUsuario() throws Exception {
        String perfil = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/view/main/ProfileFragment.java",
                "app/src/main/java/com/tuempresa/citasmedicas/view/main/ProfileFragment.java");
        // Los cinco campos visibles del expediente deben salir del usuario.
        assertTrue(perfil.contains("getPhone()"));
        assertTrue(perfil.contains("getBloodType()"));
        assertTrue(perfil.contains("getInsurance()"));
        assertTrue(perfil.contains("getAllergies()"));
        assertTrue(perfil.contains("getAge()"));
    }

    @Test
    public void elRegistroPideElExpedienteEnLaPantalla() throws Exception {
        String layout = leerFuente(
                "src/main/res/layout/activity_login.xml",
                "app/src/main/res/layout/activity_login.xml");
        assertTrue("El formulario debe pedir apellidos", layout.contains("etLastName"));
        assertTrue("El formulario debe pedir teléfono", layout.contains("etPhone"));
        assertTrue("El formulario debe pedir grupo sanguíneo",
                layout.contains("etBloodType"));
        assertTrue("El formulario debe pedir aseguradora", layout.contains("etInsurance"));
        assertTrue("El formulario debe pedir alergias", layout.contains("etAllergies"));

        String actividad = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/view/auth/LoginActivity.java",
                "app/src/main/java/com/tuempresa/citasmedicas/view/auth/LoginActivity.java");
        assertTrue("La pantalla debe enviar el expediente al ViewModel",
                actividad.contains("viewModel.register(name, textoDe(etLastName), email"));
    }

    // ───────────────────────── Utilidades ─────────────────────────

    /** Registra un usuario con AuthRepository y devuelve el resultado. */
    private User registrar(String nombre, String apellidos, String email, int edad,
                           String telefono, String sangre, String seguro, String alergias) {
        final AtomicReference<User> resultado = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        new AuthRepository().register(
                new AuthRequest(nombre, apellidos, email, "123456", edad, telefono,
                        sangre, seguro, alergias),
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User data) {
                        resultado.set(data);
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        ShadowLooper.idleMainLooper();
        assertNull("El registro no debe fallar: " + error.get(), error.get());
        assertNotNull("Debe devolverse el usuario creado", resultado.get());
        return resultado.get();
    }

    private AppointmentEntity cita(String ownerId, String patientName) {
        AppointmentEntity e = new AppointmentEntity();
        e.doctorId = "doc_01";
        e.doctorName = "Dr. Carlos Ramírez";
        e.specialtyName = "Cardiología";
        e.patientName = patientName;
        e.patientAge = 30;
        e.date = "2026-01-15";
        e.time = "10:00";
        e.status = "Confirmed";
        e.reason = "Chequeo";
        e.createdAt = System.currentTimeMillis();
        e.ownerId = ownerId;
        return e;
    }

    /** Ejecuta una tarea de Room fuera del hilo principal. */
    private <T> T enHiloDeFondo(Callable<T> tarea) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> futuro = executor.submit(tarea);
            return futuro.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    /** Lee un archivo fuente probando varias rutas relativas posibles. */
    private String leerFuente(String... rutas) throws Exception {
        for (String ruta : rutas) {
            File f = new File(ruta);
            if (f.exists()) {
                return new String(Files.readAllBytes(Paths.get(ruta)),
                        StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * Quita los comentarios de un fuente Java.
     * <p>
     * Necesario porque los comentarios de estas clases <b>explican</b> el fallo
     * corregido y por tanto mencionan "pat_01" o "Laura Jiménez". Sin este filtro el
     * test se dispararía con el propio texto que documenta el arreglo.
     */
    private String sinComentarios(String fuente) {
        String sinBloques = fuente.replaceAll("(?s)/\\*.*?\\*/", " ");
        return sinBloques.replaceAll("//[^\\n]*", " ");
    }
}
