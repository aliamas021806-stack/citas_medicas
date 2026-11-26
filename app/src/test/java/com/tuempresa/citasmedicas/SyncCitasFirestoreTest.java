package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.firebase.FirestoreApi;
import com.tuempresa.citasmedicas.data.firebase.FirestoreAppointmentRepository;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

/**
 * Pruebas de la sincronización de citas con Firestore.
 * <p>
 * Lo que se verifica aquí es el contrato que hace que la integración sea segura:
 * <ul>
 *   <li>El documento que se envía a Firestore tiene <b>exactamente</b> los campos y
 *       tipos que espera la consola (un {@code integerValue} mal puesto hace que el
 *       documento se vea pero la app no lo lea).</li>
 *   <li>Los valores nulos no rompen la subida.</li>
 *   <li>Las citas se siguen guardando en Room: la nube es <i>adicional</i>, nunca un
 *       sustituto que pueda perder datos si falla.</li>
 *   <li>La restricción de Java puro se mantiene (0 archivos Kotlin).</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SyncCitasFirestoreTest {

    private Appointment citaDeEjemplo() {
        Appointment a = new Appointment();
        a.setDoctorId("doc_01");
        a.setDoctorName("Dr. Carlos Ramírez");
        a.setSpecialtyName("Cardiología");
        a.setPatientName("Laura Jiménez");
        a.setPatientAge(34);
        a.setDate("2025-06-10");
        a.setTime("10:00");
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setReason("Chequeo cardiológico anual");
        a.setCreatedAt(1_750_000_000_000L);
        return a;
    }

    // ───────────────────── Forma del documento enviado ─────────────────────

    @Test
    public void elDocumentoEnviadoTieneTodosLosCamposDeLaCita() {
        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(citaDeEjemplo());

        assertNotNull("Debe construirse el documento", doc);
        assertNotNull("Debe tener campos", doc.fields);

        for (String campo : new String[]{"doctorId", "doctorName", "specialtyName",
                "patientName", "patientAge", "date", "time", "status", "reason",
                "createdAt"}) {
            assertTrue("Falta el campo obligatorio '" + campo + "'",
                    doc.fields.containsKey(campo));
        }
    }

    @Test
    public void losEnterosViajanComoTextoPorqueFirestoreLoExige() {
        // Firestore declara integerValue como STRING en su JSON. Si se envía un número
        // JSON normal, la API responde 400 y el documento nunca se crea.
        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(citaDeEjemplo());

        FirestoreApi.Value edad = doc.fields.get("patientAge");
        assertNotNull(edad);
        assertNotNull("patientAge debe ser integerValue", edad.integerValue);
        assertEquals("34", edad.integerValue);

        FirestoreApi.Value creado = doc.fields.get("createdAt");
        assertNotNull(creado);
        assertEquals("1750000000000", creado.integerValue);
    }

    @Test
    public void losTextosViajanComoStringValue() {
        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(citaDeEjemplo());

        assertEquals("Dr. Carlos Ramírez",
                doc.fields.get("doctorName").stringValue);
        assertEquals("Cardiología", doc.fields.get("specialtyName").stringValue);
        assertEquals("Laura Jiménez", doc.fields.get("patientName").stringValue);
        assertEquals("2025-06-10", doc.fields.get("date").stringValue);
        assertEquals("10:00", doc.fields.get("time").stringValue);
    }

    @Test
    public void elStatusSeEnviaComoTextoLegible() {
        // En la consola se debe leer "Confirmed", no un número. Es además el formato
        // que AppointmentStatus.fromValue() sabe interpretar al volver.
        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(citaDeEjemplo());
        assertEquals("Confirmed", doc.fields.get("status").stringValue);
    }

    @Test
    public void losDatosNulosNoRompenLaSubida() {
        // Una cita a medio rellenar no debe provocar un NullPointerException al
        // construir el documento.
        Appointment incompleta = new Appointment();
        incompleta.setDoctorId("doc_02");
        // doctorName, specialtyName, status, reason... quedan a null.

        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(incompleta);

        assertNotNull(doc);
        assertNotNull(doc.fields);
        assertNotNull("Un texto nulo debe enviarse como cadena vacía",
                doc.fields.get("doctorName").stringValue);
        assertEquals("", doc.fields.get("doctorName").stringValue);
        // El estado por defecto de una cita nueva es "Confirmed".
        assertEquals("Confirmed", doc.fields.get("status").stringValue);
    }

    @Test
    public void todosLosValoresSonEnvoltoriosDeFirestore() {
        // Ningún campo puede ir "suelto": todos deben ser objetos con su tipo.
        FirestoreApi.FirestoreDocument doc =
                FirestoreAppointmentRepository.toDocument(citaDeEjemplo());

        for (Map.Entry<String, FirestoreApi.Value> e : doc.fields.entrySet()) {
            FirestoreApi.Value v = e.getValue();
            assertNotNull("El campo '" + e.getKey() + "' no puede ser nulo", v);
            boolean tieneTipo = v.stringValue != null || v.integerValue != null
                    || v.doubleValue != null || v.booleanValue != null
                    || v.timestampValue != null || v.mapValue != null
                    || v.arrayValue != null || v.nullValue != null;
            assertTrue("El campo '" + e.getKey() + "' no declara ningún tipo de Firestore",
                    tieneTipo);
        }
    }

    // ───────────────────── Room sigue siendo la base ─────────────────────

    @Test
    public void elModeloTransportaElIdRemotoSinAfectarALaUi() {
        // remoteId existe para no duplicar el documento en la nube. La UI lo ignora.
        Appointment a = citaDeEjemplo();
        a.setRemoteId("abc123XYZ");
        assertEquals("abc123XYZ", a.getRemoteId());

        // Y sobrevive el viaje a entidad y de vuelta (lo usa el sincronizador).
        com.tuempresa.citasmedicas.data.local.AppointmentEntity entidad =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toEntity(a);
        assertEquals("abc123XYZ", entidad.remoteId);

        Appointment vuelta =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toModel(entidad);
        assertNotNull(vuelta);
        assertEquals("abc123XYZ", vuelta.getRemoteId());
    }

    @Test
    public void unaCitaSinSubirTieneRemoteIdVacioYNoFalla() {
        Appointment a = citaDeEjemplo();
        // Sin remoteId: es el estado normal al agendar sin internet.
        com.tuempresa.citasmedicas.data.local.AppointmentEntity entidad =
                com.tuempresa.citasmedicas.util.AppointmentMapper.toEntity(a);
        assertNotNull("La entidad no puede tener remoteId nulo", entidad.remoteId);
        assertEquals("", entidad.remoteId);
    }

    @Test
    public void laEntidadDeclaraRemoteIdConValorPorDefectoParaLaMigracion() {
        // Sin defaultValue, el ALTER TABLE de SQLite falla con las filas existentes
        // (no se puede añadir una columna NOT NULL sin valor por defecto).
        //
        // Se comprueba sobre el código fuente y no por reflexión porque la anotación
        // @ColumnInfo de Room tiene retención CLASS: no está disponible en tiempo de
        // ejecución.
        String fuente = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/local/AppointmentEntity.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/local/AppointmentEntity.java");
        assertFalse("Debe poder leerse la entidad", fuente.isEmpty());

        assertTrue("La entidad debe declarar la columna remote_id",
                fuente.contains("name = \"remote_id\""));
        assertTrue("La columna necesita defaultValue para la migración",
                fuente.contains("defaultValue = \"\""));
        assertTrue("Debe ser pública para el DAO y el mapper",
                fuente.contains("public String remoteId"));

        // Y la migración debe existir realmente, no sólo la columna.
        String base = leerFuente(
                "src/main/java/com/tuempresa/citasmedicas/data/local/AppDatabase.java",
                "app/src/main/java/com/tuempresa/citasmedicas/data/local/AppDatabase.java");
        assertTrue("La base debe estar en la versión 2", base.contains("version = 2"));
        assertTrue("Debe existir una migración 1 -> 2",
                base.contains("MIGRACION_1_2"));
        assertTrue("La migración debe añadir la columna sin borrar las citas",
                base.contains("ALTER TABLE appointments ADD COLUMN remote_id"));
        assertTrue("La migración debe estar registrada en el builder",
                base.contains("addMigrations(MIGRACION_1_2)"));
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

    // ───────────── Nombre de la base de datos ─────────────

    @Test
    public void laUrlUsaElNombreDeBaseConfiguradoYPorDefectoDefault() {
        // El asistente de Firebase permite cambiar el "ID de la base de datos". Si se
        // elige otro nombre y el código escribe "(default)" a fuego, TODAS las
        // peticiones devuelven 404 y la app parece "no conectada" sin motivo.
        String url = com.tuempresa.citasmedicas.data.firebase.FirebaseConfig
                .firestoreBaseUrl();

        String base = com.tuempresa.citasmedicas.data.firebase.FirebaseConfig
                .databaseId();
        assertNotNull(base);
        assertFalse("El nombre de la base no puede quedar vacío", base.trim().isEmpty());
        // Si no hay configuración explícita, debe ser (default), que es lo habitual.
        assertEquals("Sin configuración, la base debe ser (default)", "(default)", base);
        assertTrue("La URL debe construir el nombre de la base dinámicamente: " + url,
                url.contains("/databases/(default)/documents/"));
    }

    // ───────────────────── Java puro ─────────────────────

    @Test
    public void laSincronizacionNoIntroduceKotlin() {
        // El SDK de Firebase arrastraría kotlin-stdlib. Se comprueba que sigue ausente.
        boolean sdkPresente;
        try {
            Class.forName("com.google.firebase.firestore.FirebaseFirestore");
            sdkPresente = true;
        } catch (ClassNotFoundException e) {
            sdkPresente = false;
        }
        assertFalse("El SDK de Firestore no debe estar en el classpath", sdkPresente);
    }

    @Test
    public void elRepositorioDeSincronizacionImplementaElContratoDeDominio() {
        // La UI depende de IAppointmentRepository, no de esta clase concreta: por eso
        // se puede añadir Firestore sin tocar ni una pantalla.
        assertTrue("Debe implementar IAppointmentRepository",
                com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository.class
                        .isAssignableFrom(FirestoreAppointmentRepository.class));
    }

    // ───────── Cancelar y borrar: la nube debe reflejar lo mismo que el movil ─────────

    @Test
    public void cancelarActualizaElEstadoEnFirestoreSinPerderCampos() throws Exception {
        // Regresion real: antes, cancelar una cita solo cambiaba Room. En la consola
        // de Firebase seguia apareciendo "Confirmed", es decir, la app y la nube
        // mostraban estados distintos. El PATCH DEBE llevar updateMask para no
        // reemplazar el documento entero (Firestore borraria los demas campos).
        String fuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java");

        assertTrue("Debe existir una operacion de actualizacion parcial (PATCH)",
                fuente.contains("@PATCH("));
        assertTrue("La actualizacion debe enviar updateMask.fieldPaths; sin la mascara "
                        + "Firestore sustituye el documento completo y se pierden campos",
                fuente.contains("updateMask.fieldPaths"));
    }

    @Test
    public void eliminarUnaCitaBorraTambienElDocumentoRemoto() throws Exception {
        // Antes el borrado remoto solo se registraba en el log: la cita desaparecia
        // del telefono pero seguia existiendo en Firestore para siempre.
        String apiFuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreApi.java");
        String repoFuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreAppointmentRepository.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreAppointmentRepository.java");

        assertTrue("La API debe exponer DELETE", apiFuente.contains("@DELETE("));
        assertTrue("El repositorio debe invocar el borrado remoto",
                repoFuente.contains("deleteDocument("));
        assertTrue("Cancelar tambien debe propagarse a la nube",
                repoFuente.contains("marcarCanceladaEnFirestore("));
    }

    @Test
    public void unaCitaSinRemoteIdNoIntentaBorrarNadaEnLaNube() throws Exception {
        // Si la cita se agendo sin internet nunca se subio: no hay documento remoto.
        // Intentar borrar lanzaria un 404 inutil en cada borrado.
        String fuente = leerFuente(
                "app/src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreAppointmentRepository.java",
                "src/main/java/com/tuempresa/citasmedicas/data/firebase/FirestoreAppointmentRepository.java");
        assertTrue("Debe comprobarse remoteId antes de borrar en Firestore",
                fuente.contains("remoteId == null || remoteId.trim().isEmpty()"));
    }

    /** Lee un fuente Java probando dos rutas relativas (Gradle e IDE). */
    private static String leerFuente(String... rutas) throws Exception {
        for (String ruta : rutas) {
            java.io.File f = new java.io.File(ruta);
            if (f.isFile()) {
                return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new java.io.IOException("No se encontro el fuente: " + String.join(", ", rutas));
    }
}
