package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
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

    // ───────────────────────── Modo mock por defecto ─────────────────────────

    @Test
    public void sinCredencialesLaAppSigueEnModoMock() {
        // En el repositorio no hay local.properties con claves: FIREBASE_ENABLED=false.
        assertFalse("Sin credenciales, Firebase debe estar desactivado",
                FirebaseConfig.isEnabled());
        assertFalse(RepositoryProvider.isUsingFirebase());
        assertEquals("Mock", RepositoryProvider.describeDataSource());
    }

    @Test
    public void enModoMockElProviderDevuelveLasImplementacionesLocales() {
        assertTrue("Sin credenciales debe usarse AuthRepository (simulado)",
                RepositoryProvider.auth(RuntimeEnvironment.getApplication())
                        instanceof AuthRepository);
        assertTrue("Sin credenciales debe usarse DoctorRepository (mock)",
                RepositoryProvider.doctors() instanceof DoctorRepository);
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
        // Sin credenciales, el provider lo indica como "Mock".
        String modo = RepositoryProvider.describeDataSource();
        assertNotNull(modo);
        assertEquals("Mock", modo);
        assertFalse("No debe decir Firebase sin credenciales", modo.contains("Firebase"));
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
}
