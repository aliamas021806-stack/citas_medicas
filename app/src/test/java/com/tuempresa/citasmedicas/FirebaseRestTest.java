package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.data.firebase.FirebaseAuthApi;
import com.tuempresa.citasmedicas.data.firebase.FirebaseAuthFallback;
import com.tuempresa.citasmedicas.data.firebase.FirebaseErrors;
import com.tuempresa.citasmedicas.data.firebase.FirebaseConfig;
import com.tuempresa.citasmedicas.data.firebase.FirestoreApi;
import com.tuempresa.citasmedicas.data.firebase.FirestoreMapper;
import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pruebas de la capa de Firebase por REST.
 * <p>
 * No hacen peticiones de red reales (no hay credenciales en el repositorio). Lo que
 * verifican es justo lo que puede romperse sin darse cuenta:
 * <ul>
 *   <li>Que <b>sin configuración</b> la app sigue en modo mock (nada cambia para el
 *       profesor ni para la CI).</li>
 *   <li>Que el <b>mapeo de Firestore</b> maneja correctamente el formato extraño de
 *       la API ({@code stringValue}, {@code integerValue} como texto, campos
 *       ausentes, rutas de documento completas).</li>
 *   <li>Que construir el documento a enviar produce el JSON que Firestore espera.</li>
 *   <li>Que la restricción de <b>Java puro</b> se mantiene.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FirebaseRestTest {

    // ──────────────── Interruptor: los dos estados son válidos ────────────────
    //
    // Ojo: estos tests corren en dos escenarios distintos y ambos deben pasar.
    //   a) En el repositorio (CI y APK del profesor): no hay local.properties con
    //      claves -> FIREBASE_ENABLED = false -> modo mock.
    //   b) En una máquina con credenciales: FIREBASE_ENABLED = true -> Firebase.
    // Por eso se comprueba la COHERENCIA entre el interruptor y lo que devuelve el
    // provider, en lugar de asumir un estado concreto.

    @Test
    public void elInterruptorYElProviderSiempreConcuerdan() {
        boolean encendido = FirebaseConfig.isEnabled();

        assertEquals("isUsingFirebase debe reflejar el mismo interruptor",
                encendido, RepositoryProvider.isUsingFirebase());

        if (encendido) {
            assertTrue("Con credenciales, describeDataSource debe nombrar Firebase",
                    RepositoryProvider.describeDataSource().startsWith("Firebase"));
        } else {
            assertEquals("Sin credenciales, describeDataSource debe ser Mock",
                    "Mock", RepositoryProvider.describeDataSource());
        }
    }

    @Test
    public void laDescripcionDelModoNuncaEstaVacia() {
        String descripcion = RepositoryProvider.describeDataSource();
        assertNotNull(descripcion);
        assertFalse("El chip de Perfil no debe quedar en blanco", descripcion.trim().isEmpty());
    }

    @Test
    public void elProviderSiempreDevuelveUnAuthUsable() {
        // Con o sin Firebase debe haber un repositorio de auth; null rompería el login.
        assertNotNull(RepositoryProvider.auth(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void elProviderSiempreDevuelveCatalogoUsable() {
        assertNotNull(RepositoryProvider.doctors());
        assertNotNull(RepositoryProvider.patients());
    }

    @Test
    public void enModoMockElProviderDevuelveLasImplementacionesLocales() {
        // Sólo aplica cuando NO hay credenciales (el caso de la CI).
        org.junit.Assume.assumeFalse(
                "Este test sólo verifica el modo mock", FirebaseConfig.isEnabled());

        assertTrue("Sin credenciales debe usarse AuthRepository (simulado)",
                RepositoryProvider.auth(RuntimeEnvironment.getApplication())
                        instanceof AuthRepository);
        assertTrue("Sin credenciales debe usarse DoctorRepository (mock)",
                RepositoryProvider.doctors() instanceof DoctorRepository);
    }

    @Test
    public void enModoFirebaseElCatalogoVaEnvueltoEnRespaldoLocal() {
        // Sólo aplica cuando SÍ hay credenciales. Lo importante: el repositorio que
        // llega a la UI NO es directamente el local, sino un decorador que intenta
        // Firestore y cae a los datos locales si algo falla. Así una base sin crear o
        // unas reglas mal puestas no dejan las pantallas en blanco.
        org.junit.Assume.assumeTrue(
                "Este test sólo verifica el modo Firebase", FirebaseConfig.isEnabled());

        assertFalse("Con credenciales el catálogo no debe ser el mock directo: "
                        + "debe ir envuelto con respaldo local",
                RepositoryProvider.doctors()
                        instanceof com.tuempresa.citasmedicas.data.repository.DoctorRepository);
        assertFalse("Con credenciales los pacientes no deben ser el mock directo",
                RepositoryProvider.patients()
                        instanceof com.tuempresa.citasmedicas.data.repository.PatientRepository);
    }

    @Test
    public void elRepositorioDeCitasNuncaSeSustituyePorFirestore() {
        // Room es la fuente de verdad de la UI: debe seguir siendo local siempre.
        Object repo = RepositoryProvider.appointments(RuntimeEnvironment.getApplication());
        assertNotNull(repo);
        assertTrue("El repositorio de citas debe seguir siendo el de Room",
                repo instanceof com.tuempresa.citasmedicas.data.repository.AppointmentRepository);
    }

    @Test
    public void lasUrlsSeConstruyenConElFormatoQueExigeFirebase() {
        // Aunque no haya credenciales, el formato debe ser correcto para cuando las haya.
        String fs = FirebaseConfig.firestoreBaseUrl();
        assertTrue("Firestore exige la barra final en baseUrl()", fs.endsWith("/"));
        assertTrue(fs.startsWith("https://firestore.googleapis.com/v1/projects/"));
        assertTrue("Debe incluir la base de datos por defecto",
                fs.contains("/databases/(default)/documents/"));

        String auth = FirebaseConfig.authBaseUrl();
        assertTrue(auth.endsWith("/"));
        assertEquals("https://identitytoolkit.googleapis.com/v1/", auth);
    }

    @Test
    public void losNombresDeColeccionSonLosEsperados() {
        assertEquals("doctors", FirebaseConfig.COLLECTION_DOCTORS);
        assertEquals("specialties", FirebaseConfig.COLLECTION_SPECIALTIES);
        assertEquals("patients", FirebaseConfig.COLLECTION_PATIENTS);
        assertEquals("appointments", FirebaseConfig.COLLECTION_APPOINTMENTS);
    }

    // ───────────────────────── Mapeo de Firestore ─────────────────────────

    /** Construye un documento de Firestore con la forma real de la API. */
    private FirestoreApi.FirestoreDocument documento(String id, Map<String, FirestoreApi.Value> campos) {
        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.name = "projects/demo-proyecto/databases/(default)/documents/doctors/" + id;
        doc.fields = campos;
        return doc;
    }

    private Map<String, FirestoreApi.Value> campos() {
        return new HashMap<>();
    }

    @Test
    public void elIdSeExtraeDeLaRutaCompletaDelDocumento() {
        FirestoreApi.FirestoreDocument doc = documento("doc_01", campos());
        assertEquals("doc_01", FirestoreMapper.documentId(doc));
        // Casos límite: no debe lanzar excepción
        assertEquals("", FirestoreMapper.documentId(null));
        FirestoreApi.FirestoreDocument sinNombre = new FirestoreApi.FirestoreDocument();
        assertEquals("", FirestoreMapper.documentId(sinNombre));
    }

    @Test
    public void unDoctorSeMapeaConTodosSusCampos() {
        Map<String, FirestoreApi.Value> f = campos();
        f.put("fullName", FirestoreApi.ofString("Dra. Elena Ruiz"));
        f.put("specialtyId", FirestoreApi.ofString("sp_cardio"));
        f.put("specialtyName", FirestoreApi.ofString("Cardiología"));
        f.put("bio", FirestoreApi.ofString("15 años de experiencia"));
        f.put("rating", FirestoreApi.ofDouble(4.8));
        // Firestore devuelve los enteros como TEXTO
        f.put("yearsExperience", FirestoreApi.ofInteger(15));
        f.put("age", FirestoreApi.ofInteger(46));
        f.put("consultationFee", FirestoreApi.ofDouble(60.0));

        Doctor d = FirestoreMapper.toDoctor(documento("doc_01", f));
        assertNotNull(d);
        assertEquals("doc_01", d.getId());
        assertEquals("Dra. Elena Ruiz", d.getFullName());
        assertEquals("sp_cardio", d.getSpecialtyId());
        assertEquals("Cardiología", d.getSpecialtyName());
        assertEquals(4.8, d.getRating(), 0.001);
        assertEquals("integerValue viene como texto y debe convertirse a 15",
                15, d.getYearsExperience());
        assertEquals(46, d.getAge());
        assertEquals(60.0, d.getConsultationFee(), 0.001);
    }

    @Test
    public void unDoctorIncompletoNoRompeElMapeo() {
        // Sólo el nombre: el resto de campos ausentes deben tomar valores de reserva.
        Map<String, FirestoreApi.Value> f = campos();
        f.put("fullName", FirestoreApi.ofString("Dr. Mínimo"));

        Doctor d = FirestoreMapper.toDoctor(documento("doc_x", f));
        assertNotNull(d);
        assertEquals("Dr. Mínimo", d.getFullName());
        assertEquals("", d.getSpecialtyId());
        assertEquals("", d.getSpecialtyName());
        assertEquals(0.0, d.getRating(), 0.001);
        assertEquals(0, d.getYearsExperience());
        assertEquals(0, d.getAge());
    }

    @Test
    public void unDocumentoSinCamposTampocoRompe() {
        FirestoreApi.FirestoreDocument doc = documento("doc_vacio", null);
        Doctor d = FirestoreMapper.toDoctor(doc);
        assertNotNull(d);
        assertEquals("doc_vacio", d.getId());
        assertEquals("", d.getFullName());
    }

    @Test
    public void unaEspecialidadAceptaElEmojiConDistintosNombresDeCampo() {
        Map<String, FirestoreApi.Value> conIconEmoji = campos();
        conIconEmoji.put("name", FirestoreApi.ofString("Cardiología"));
        conIconEmoji.put("iconEmoji", FirestoreApi.ofString("❤️"));
        Specialty s1 = FirestoreMapper.toSpecialty(documento("sp_1", conIconEmoji));
        assertNotNull(s1);
        assertEquals("❤️", s1.getIconEmoji());

        Map<String, FirestoreApi.Value> conIcon = campos();
        conIcon.put("name", FirestoreApi.ofString("Pediatría"));
        conIcon.put("icon", FirestoreApi.ofString("🧒"));
        assertNotNull(FirestoreMapper.toSpecialty(documento("sp_2", conIcon)));
        assertEquals("🧒", FirestoreMapper.toSpecialty(documento("sp_2", conIcon)).getIconEmoji());
    }

    @Test
    public void unPacienteSeMapeaConSuEdad() {
        Map<String, FirestoreApi.Value> f = campos();
        f.put("fullName", FirestoreApi.ofString("Ana Pérez"));
        f.put("age", FirestoreApi.ofInteger(34));
        f.put("phone", FirestoreApi.ofString("+34 600 111 222"));
        f.put("bloodType", FirestoreApi.ofString("O+"));
        f.put("insurance", FirestoreApi.ofString("Sanitas"));
        f.put("allergies", FirestoreApi.ofString("Penicilina"));

        Patient p = FirestoreMapper.toPatient(documento("pat_01", f));
        assertNotNull(p);
        assertEquals("pat_01", p.getId());
        assertEquals("Ana Pérez", p.getFullName());
        assertEquals(34, p.getAge());
        assertEquals("+34 111 222".length() > 0 ? "+34 600 111 222" : "", p.getPhone());
        assertEquals("O+", p.getBloodType());
        assertEquals("Sanitas", p.getInsurance());
        assertEquals("Penicilina", p.getAllergies());
    }

    @Test
    public void lasListasIgnoranDocumentosNulos() {
        List<FirestoreApi.FirestoreDocument> docs = new ArrayList<>();
        Map<String, FirestoreApi.Value> f = campos();
        f.put("fullName", FirestoreApi.ofString("Uno"));
        docs.add(documento("d1", f));
        docs.add(null);

        List<Doctor> doctores = FirestoreMapper.toDoctors(docs);
        assertEquals(1, doctores.size());
        assertEquals("Uno", doctores.get(0).getFullName());

        // Lista nula no debe reventar
        assertTrue(FirestoreMapper.toDoctors(null).isEmpty());
        assertTrue(FirestoreMapper.toSpecialties(null).isEmpty());
        assertTrue(FirestoreMapper.toPatients(null).isEmpty());
    }

    @Test
    public void laLecturaDeTextoAceptaNumerosYBooleanos() {
        Map<String, FirestoreApi.Value> f = campos();
        f.put("texto", FirestoreApi.ofString("hola"));
        f.put("numero", FirestoreApi.ofInteger(7));
        f.put("flag", FirestoreApi.ofBoolean(true));

        assertEquals("hola", FirestoreMapper.text(f, "texto", "x"));
        assertEquals("7", FirestoreMapper.text(f, "numero", "x"));
        assertEquals("true", FirestoreMapper.text(f, "flag", "x"));
        assertEquals("reserva", FirestoreMapper.text(f, "no_existe", "reserva"));
        assertEquals("reserva", FirestoreMapper.text(null, "texto", "reserva"));
    }

    @Test
    public void laLecturaNumericaEsToleranteAFormatos() {
        Map<String, FirestoreApi.Value> f = campos();
        f.put("entero", FirestoreApi.ofInteger(42));
        f.put("decimal", FirestoreApi.ofDouble(3.5));
        f.put("texto", FirestoreApi.ofString("12.25"));
        f.put("basura", FirestoreApi.ofString("no es un número"));

        assertEquals(42, FirestoreMapper.number(f, "entero", -1), 0.001);
        assertEquals(3.5, FirestoreMapper.number(f, "decimal", -1), 0.001);
        assertEquals("Un número en texto también debe leerse",
                12.25, FirestoreMapper.number(f, "texto", -1), 0.001);
        assertEquals("Texto no numérico debe caer al valor de reserva",
                -1, FirestoreMapper.number(f, "basura", -1), 0.001);
        assertEquals(-1, FirestoreMapper.number(null, "entero", -1), 0.001);
    }

    // ───────────────────────── Estructura del JSON de escritura ─────────────────────────

    @Test
    public void losValoresSeConstruyenConLaFormaQueEsperaFirestore() {
        // Firestore exige que cada campo sea un objeto con su tipo explícito.
        FirestoreApi.Value s = FirestoreApi.ofString("Ana");
        assertNotNull(s.stringValue);
        assertEquals("Ana", s.stringValue);

        FirestoreApi.Value i = FirestoreApi.ofInteger(34);
        assertNotNull("integerValue debe ser texto, no número", i.integerValue);
        assertEquals("34", i.integerValue);

        FirestoreApi.Value d = FirestoreApi.ofDouble(4.5);
        assertNotNull(d.doubleValue);
        assertEquals(4.5, d.doubleValue, 0.001);

        FirestoreApi.Value b = FirestoreApi.ofBoolean(true);
        assertNotNull(b.booleanValue);
        assertTrue(b.booleanValue);
    }

    @Test
    public void unaConsultaEstructuradaSeConstruyeConColeccionYFiltro() {
        FirestoreApi.FieldFilterBody filtro = new FirestoreApi.FieldFilterBody(
                "specialtyId", "EQUAL", FirestoreApi.ofString("sp_cardio"));
        FirestoreApi.CompositeFilterBody compuesto =
                new FirestoreApi.CompositeFilterBody(java.util.Collections.singletonList(
                        new FirestoreApi.FieldFilter(filtro)));
        FirestoreApi.StructuredQuery query =
                new FirestoreApi.StructuredQuery(FirebaseConfig.COLLECTION_DOCTORS);
        query.where = new FirestoreApi.CompositeFilter(compuesto);

        assertNotNull(query.from);
        assertEquals(1, query.from.size());
        assertEquals("doctors", query.from.get(0).collectionId);
        assertNotNull(query.where);
        assertEquals("AND", query.where.compositeFilter.op);
        assertEquals(1, query.where.compositeFilter.filters.size());
        assertEquals("specialtyId",
                query.where.compositeFilter.filters.get(0).fieldFilter.field.fieldPath);
        assertEquals("EQUAL",
                query.where.compositeFilter.filters.get(0).fieldFilter.op);
    }


    @Test
    public void elPerfilMuestraElOrigenDeLosDatos() {
        // La UI debe poder informar si los datos vienen de Firebase o son simulados.
        // El chip de Perfil pinta esta cadena tal cual, así que nunca puede quedar
        // vacía y debe corresponder al estado real del interruptor.
        String modo = RepositoryProvider.describeDataSource();
        assertNotNull(modo);
        assertFalse("El chip no puede quedar vacío", modo.trim().isEmpty());

        if (FirebaseConfig.isEnabled()) {
            assertTrue("Con credenciales el chip debe decir Firebase",
                    modo.startsWith("Firebase"));
            assertTrue("El chip debe nombrar el proyecto para poder verificarlo",
                    modo.contains(FirebaseConfig.projectId()));
        } else {
            assertEquals("Sin credenciales el chip debe decir Mock", "Mock", modo);
        }
    }

    @Test
    public void lasCadenasDelOrigenDeDatosExistenEnRecursos() {
        // Evita un crash si alguien añade el chip y olvida el string.
        android.content.Context ctx = RuntimeEnvironment.getApplication();
        assertNotNull(ctx.getString(R.string.data_source_mock));
        assertNotNull(ctx.getString(R.string.data_source_firebase, "Firebase (demo)"));
        assertTrue(ctx.getString(R.string.data_source_firebase, "Firebase (demo)")
                .contains("Firebase (demo)"));
    }

    // ───────────────────────── Restricción de Java puro ─────────────────────────

    @Test
    public void noSeAnadeNingunaDependenciaDeFirebaseSdk() throws Exception {
        // El SDK arrastraría Kotlin. La app habla con Firebase por REST (Retrofit),
        // así que no debe aparecer ninguna librería com.google.firebase en el classpath
        // ni en el build.gradle.
        // El directorio de trabajo de los tests puede ser la raíz del repo o app/.
        java.io.File gradle = new java.io.File("app/build.gradle");
        if (!gradle.exists()) {
            gradle = new java.io.File("build.gradle");
        }
        assertTrue("No se encuentra app/build.gradle desde " + gradle.getAbsolutePath(),
                gradle.exists());
        String buildGradle = new String(
                java.nio.file.Files.readAllBytes(gradle.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("No debe añadirse firebase-auth (arrastra Kotlin)",
                buildGradle.contains("firebase-auth"));
        assertFalse("No debe añadirse firebase-firestore (arrastra Kotlin)",
                buildGradle.contains("firebase-firestore"));
        assertFalse("No debe aplicarse el plugin de Google Services",
                buildGradle.contains("com.google.gms.google-services"));

        // Y tampoco debe estar el SDK en el classpath por transitividad.
        boolean sdkPresente;
        try {
            Class.forName("com.google.firebase.firestore.FirebaseFirestore");
            sdkPresente = true;
        } catch (ClassNotFoundException e) {
            sdkPresente = false;
        }
        assertFalse("El SDK de Firestore no debe estar en el classpath", sdkPresente);
    }

    // ───────────────── Respaldo local ante fallos de Firestore ─────────────────

    /** Repositorio de prueba que siempre falla, para simular la nube caída. */
    private static final class RepositorioQueFalla
            implements com.tuempresa.citasmedicas.domain.repository.IDoctorRepository,
            com.tuempresa.citasmedicas.domain.repository.IPatientRepository {

        private final String mensaje;

        RepositorioQueFalla(String mensaje) {
            this.mensaje = mensaje;
        }

        @Override
        public void getSpecialties(
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Specialty>> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getDoctors(
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getDoctorsBySpecialty(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getDoctorById(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<Doctor> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getAvailableSlots(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<com.tuempresa.citasmedicas.model.TimeSlot>> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getPatients(
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Patient>> cb) {
            cb.onError(mensaje);
        }

        @Override
        public void getPatientById(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<Patient> cb) {
            cb.onError(mensaje);
        }
    }

    /** Repositorio de prueba que devuelve una lista vacía (colección sin datos). */
    private static final class RepositorioVacio
            implements com.tuempresa.citasmedicas.domain.repository.IDoctorRepository {

        @Override
        public void getSpecialties(
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Specialty>> cb) {
            cb.onSuccess(new ArrayList<>());
        }

        @Override
        public void getDoctors(
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>> cb) {
            cb.onSuccess(new ArrayList<>());
        }

        @Override
        public void getDoctorsBySpecialty(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>> cb) {
            cb.onSuccess(new ArrayList<>());
        }

        @Override
        public void getDoctorById(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<Doctor> cb) {
            cb.onSuccess(null);
        }

        @Override
        public void getAvailableSlots(String id,
                com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<com.tuempresa.citasmedicas.model.TimeSlot>> cb) {
            cb.onSuccess(new ArrayList<>());
        }
    }

    @Test
    public void siFirestoreFallaSeSirvenLosDatosLocales() {
        // Este es el comportamiento central: una base sin crear, unas reglas que
        // bloquean o falta de red NO deben dejar la pantalla en blanco.
        com.tuempresa.citasmedicas.data.repository.DoctorRepository local =
                new com.tuempresa.citasmedicas.data.repository.DoctorRepository();

        com.tuempresa.citasmedicas.domain.repository.IDoctorRepository decorado =
                com.tuempresa.citasmedicas.data.firebase.FirestoreFallback.doctors(
                        new RepositorioQueFalla("Firestore respondió con HTTP 404"), local);

        final List<Doctor>[] recibidos = new List[]{null};
        final String[] error = new String[]{null};
        decorado.getDoctors(new com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                recibidos[0] = data;
            }

            @Override
            public void onError(String message) {
                error[0] = message;
            }
        });

        esperar(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return recibidos[0] != null || error[0] != null;
            }
        });

        org.junit.Assert.assertNull("No debe propagarse el error si el local responde", error[0]);
        assertNotNull("Deben servirse los datos locales", recibidos[0]);
        assertFalse("Los datos locales no deben estar vacíos", recibidos[0].isEmpty());
    }

    @Test
    public void siLaColeccionRemotaEstaVaciaTambienSeUsaElLocal() {
        // Colección existente pero sin documentos: el usuario vería una lista en
        // blanco igual que con un error, así que también se recurre al respaldo.
        com.tuempresa.citasmedicas.data.repository.DoctorRepository local =
                new com.tuempresa.citasmedicas.data.repository.DoctorRepository();

        com.tuempresa.citasmedicas.domain.repository.IDoctorRepository decorado =
                com.tuempresa.citasmedicas.data.firebase.FirestoreFallback.doctors(
                        new RepositorioVacio(), local);

        final List<Doctor>[] recibidos = new List[]{null};
        decorado.getDoctors(new com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                recibidos[0] = data;
            }

            @Override
            public void onError(String message) {
                recibidos[0] = new ArrayList<>();
            }
        });

        esperar(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return recibidos[0] != null;
            }
        });

        assertNotNull(recibidos[0]);
        assertFalse("El respaldo local no debe devolver una lista vacía",
                recibidos[0].isEmpty());
    }

    @Test
    public void elRespaldoNuncaDejaLosHorariosSinResponder() {
        // Los horarios siempre son locales: el respaldo no debe interceptarlos.
        com.tuempresa.citasmedicas.data.repository.DoctorRepository local =
                new com.tuempresa.citasmedicas.data.repository.DoctorRepository();

        com.tuempresa.citasmedicas.domain.repository.IDoctorRepository decorado =
                com.tuempresa.citasmedicas.data.firebase.FirestoreFallback.doctors(
                        new RepositorioQueFalla("sin red"), local);

        final List<com.tuempresa.citasmedicas.model.TimeSlot>[] slots = new List[]{null};
        decorado.getAvailableSlots("doc_01",
                new com.tuempresa.citasmedicas.domain.repository.RepositoryCallback<List<com.tuempresa.citasmedicas.model.TimeSlot>>() {
                    @Override
                    public void onSuccess(List<com.tuempresa.citasmedicas.model.TimeSlot> data) {
                        slots[0] = data;
                    }

                    @Override
                    public void onError(String message) {
                        slots[0] = new ArrayList<>();
                    }
                });

        esperar(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return slots[0] != null;
            }
        });

        assertNotNull("El respaldo debe entregar horarios", slots[0]);
        assertFalse("Deben venir horarios reales del cálculo local", slots[0].isEmpty());
    }

    // ─────────────── Lectura de errores (body() vs errorBody()) ───────────────

    @Test
    public void elErrorSeLeeDelErrorBodyCuandoLaRespuestaNoEsExitosa() {
        // BUG REAL corregido: en Retrofit, con un HTTP 4xx el cuerpo NO se convierte al
        // tipo declarado (body() == null) y el JSON queda en errorBody(). Si sólo se
        // leía body(), los mensajes de Firebase se perdían y el usuario veía un
        // genérico "inténtalo de nuevo" en vez de "ese correo ya existe".
        String jsonError = "{\"error\":{\"code\":400,\"message\":\"EMAIL_EXISTS\","
                + "\"errors\":[{\"message\":\"EMAIL_EXISTS\",\"domain\":\"global\","
                + "\"reason\":\"invalid\"}]}}";

        FirebaseAuthApi.AuthResponse parseada = FirebaseErrors.parsearAuth(jsonError);

        assertNotNull("Debe poder parsearse el JSON de error", parseada);
        assertNotNull("Debe leerse la estructura de error", parseada.error);
        assertEquals("EMAIL_EXISTS", parseada.error.message);
    }

    @Test
    public void elMensajeCrudoNoVuelcaUnHtmlEnorme() {
        // Un proxy puede devolver HTML: no debe romper ni llenar la pantalla.
        String html = "<html><body>" + "x".repeat(500) + "</body></html>";
        String crudo = FirebaseErrors.mensajeCrudo(html);
        assertNotNull(crudo);
        assertFalse("No debe quedar vacío", crudo.trim().isEmpty());
        assertTrue("No debe volcar un HTML entero", crudo.length() <= 200);
    }

    @Test
    public void elMensajeCrudoEsVacioConCuerpoNuloOVacio() {
        assertEquals("", FirebaseErrors.mensajeCrudo(null));
        assertEquals("", FirebaseErrors.mensajeCrudo("   "));
    }

    @Test
    public void elDiagnosticoDeFirestoreExplicaQueLaBaseNoExiste() {
        // Éste es EXACTAMENTE el error del proyecto real: hay que decir "crea la base
        // de datos", no un 404 a secas.
        String json = "{\"error\":{\"code\":404,\"message\":\"The database (default) "
                + "does not exist for project demo Please visit ...\","
                + "\"status\":\"NOT_FOUND\"}}";
        String mensaje = FirebaseErrors.describirErrorFirestore(404, json);
        String minus = mensaje.toLowerCase(java.util.Locale.ROOT);
        assertTrue("Debe decir que la base no está creada: " + mensaje,
                minus.contains("no est"));
        assertTrue("Debe indicar cómo crearla", minus.contains("firestore"));
    }

    @Test
    public void elDiagnosticoDeFirestoreExplicaLasReglasDeSeguridad() {
        String json = "{\"error\":{\"code\":403,\"message\":\"Missing or insufficient "
                + "permissions.\",\"status\":\"PERMISSION_DENIED\"}}";
        String mensaje = FirebaseErrors.describirErrorFirestore(403, json);
        assertTrue("Debe hablar de las reglas: " + mensaje,
                mensaje.toLowerCase(java.util.Locale.ROOT).contains("reglas"));
    }

    @Test
    public void lasRutasConDosPuntosUsanPrefijoParaEvitarMalformedUrl() {
        // BUG REAL corregido: Retrofit resuelve la ruta con las reglas de java.net.URI
        // y "accounts:signInWithPassword" se interpreta como un ESQUEMA ("accounts:"),
        // produciendo "Malformed URL" antes de salir a la red. El prefijo "./" fuerza
        // a tratarlo como ruta relativa.
        String auth = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseAuthApi.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirebaseAuthApi.java");
        assertFalse("Debe poder leerse el fuente de la API de Auth", auth.isEmpty());
        assertTrue("El login debe declararse con prefijo ./",
                auth.contains("@POST(\"./accounts:signInWithPassword\")"));
        assertTrue("El registro debe declararse con prefijo ./",
                auth.contains("@POST(\"./accounts:signUp\")"));

        String firestore = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java");
        assertFalse("Debe poder leerse el fuente de la API de Firestore", firestore.isEmpty());
        assertTrue("runQuery también necesita el prefijo",
                firestore.contains("@POST(\"./{collection}:runQuery\")"));
    }

    /** Lee un archivo fuente probando las dos raíces posibles del directorio de trabajo. */
    private String leerFuente(String rutaApp, String rutaRaiz) {
        java.io.File f = new java.io.File(rutaApp);
        if (!f.exists()) {
            f = new java.io.File(rutaRaiz);
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    // ─────────────── Degradación segura de Auth ───────────────

    @Test
    public void elAuthSoloDegradaAnteProblemasDeConfiguracion() {
        assertTrue("CONFIGURATION_NOT_FOUND es un problema de configuración",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "Firebase Authentication no está configurado en el proyecto. "
                                + "Actívalo en Firebase Console"));

        assertTrue("El proveedor deshabilitado también lo es",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "El acceso con email/contraseña está deshabilitado en Firebase Console"));

        assertTrue("Una clave de otro proyecto también",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "La clave de Firebase no es válida para este proyecto. "
                                + "Revisa FIREBASE_API_KEY"));
    }

    @Test
    public void elAuthNoDegradaConCredencialesIncorrectasNiConFallosDeRed() {
        // Crítico: si degradara aquí, se podría entrar con cualquier contraseña.
        assertFalse("Contraseña incorrecta NO debe degradar al modo local",
                FirebaseAuthFallback.esProblemaDeConfiguracion("La contraseña no es correcta"));

        assertFalse("Correo inexistente NO debe degradar",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "No hay ninguna cuenta con ese correo"));

        assertFalse("Correo ya registrado NO debe degradar",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "Ya existe una cuenta con ese correo"));

        assertFalse("Sin internet NO debe degradar (daría una sesión falsa)",
                FirebaseAuthFallback.esProblemaDeConfiguracion(
                        "Sin conexión a internet. Revisa tu red e inténtalo de nuevo"));

        assertFalse("Un mensaje nulo no debe degradar",
                FirebaseAuthFallback.esProblemaDeConfiguracion(null));
    }

    /** Espera activa a que se cumpla una condición (las llamadas son asíncronas). */
    private void esperar(java.util.function.BooleanSupplier condicion) {
        long fin = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < fin) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }
}
