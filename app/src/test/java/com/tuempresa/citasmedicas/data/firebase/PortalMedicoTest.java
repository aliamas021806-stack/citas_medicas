package com.tuempresa.citasmedicas.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.UserRole;
import com.tuempresa.citasmedicas.navigation.Navigator;
import com.tuempresa.citasmedicas.navigation.Routes;
import com.tuempresa.citasmedicas.viewmodel.AdminDoctoresViewModel;
import com.tuempresa.citasmedicas.viewmodel.DoctorAgendaViewModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pruebas del <b>portal del médico</b> y del <b>alta de médicos</b>.
 * <p>
 * Cubren las dos preguntas que originaron este trabajo:
 * <ol>
 *   <li>¿Cómo ve un médico las citas que le tocan en el día?</li>
 *   <li>¿Cómo se añaden médicos sin entrar a la base de datos a mano?</li>
 * </ol>
 * Lo importante que se comprueba aquí y no en la pantalla:
 * <ul>
 *   <li>La consulta a Firestore tiene la <b>forma exacta</b> que exige la API
 *       ({@code compositeFilter} con {@code op=AND} envolviendo un
 *       {@code fieldFilter}). Si se enviara el {@code fieldFilter} suelto, Firestore
 *       devolvería 400 y la agenda saldría siempre vacía sin ningún error visible.</li>
 *   <li>El filtro por día y el de "canceladas" no dependen de la red.</li>
 *   <li>La vinculación cuenta ↔ ficha de médico acierta tanto por {@code uid} como
 *       por el correo de la invitación.</li>
 *   <li>Añadir un médico nunca genera dos fichas para el mismo correo.</li>
 * </ul>
 * No necesita Robolectric: no se infla ninguna vista ni se toca Android. Sólo la
 * lógica pura, así que corre en milisegundos.
 */
public class PortalMedicoTest {

    // ───────────────────────── 1. La consulta a Firestore ─────────────────────────

    /**
     * El filtro debe ser {@code compositeFilter > fieldFilter}, no un {@code fieldFilter}
     * suelto. Es el fallo silencioso clásico: la consulta responde 400 y la lista sale
     * vacía, sin excepción que avise.
     */
    @Test
    public void elFiltroUsaCompositeFilterConOpAndAlrededorDeFieldFilter() {
        FirestoreApi.CompositeFilter filtro =
                FirestoreDoctorAgendaRepository.igualdad("doctorId", "doc_ana");

        String json = new Gson().toJson(filtro);

        assertTrue("Debe enviarse envuelto en compositeFilter: " + json,
                json.contains("\"compositeFilter\""));
        assertTrue("El operador debe ser AND: " + json, json.contains("\"op\":\"AND\""));
        assertTrue("La condición debe ser un fieldFilter: " + json,
                json.contains("\"fieldFilter\""));
        assertTrue("El campo filtrado debe ser doctorId: " + json,
                json.contains("\"fieldPath\":\"doctorId\""));
        assertTrue("El operador de comparación debe ser EQUAL: " + json,
                json.contains("\"op\":\"EQUAL\""));
        assertTrue("El valor debe viajar como stringValue: " + json,
                json.contains("\"stringValue\":\"doc_ana\""));
    }

    /** Un solo filtro, no varios: con más de uno Firestore pediría índice compuesto. */
    @Test
    public void elFiltroTieneUnaSolaCondicionParaNoExigirIndice() {
        FirestoreApi.CompositeFilter filtro =
                FirestoreDoctorAgendaRepository.igualdad("doctorId", "doc_x");
        assertNotNull(filtro.compositeFilter);
        assertNotNull(filtro.compositeFilter.filters);
        assertEquals(1, filtro.compositeFilter.filters.size());
    }

    // ───────────────────────── 2. Citas del día ─────────────────────────

    @Test
    public void deHoyDevuelveSoloLasCitasDeLaFechaPedida() {
        List<Appointment> todas = Arrays.asList(
                cita("a", "2025-03-10", "09:00"),
                cita("b", "2025-03-11", "10:00"),
                cita("c", "2025-03-11", "08:00"),
                cita("d", "2025-03-12", "11:00"));

        List<Appointment> hoy = FirestoreDoctorAgendaRepository.deHoy(todas, "2025-03-11");

        assertEquals(2, hoy.size());
        assertEquals("b", hoy.get(0).getId());
        assertEquals("c", hoy.get(1).getId());
    }

    @Test
    public void deHoyConListaVaciaDevuelveListaVacia() {
        assertTrue(FirestoreDoctorAgendaRepository
                .deHoy(Collections.<Appointment>emptyList(), "2025-03-11").isEmpty());
    }

    /** Una fecha sin citas no debe devolver null ni lanzar: la pantalla muestra el vacío. */
    @Test
    public void deHoyConFechaSinCitasDevuelveListaVacia() {
        List<Appointment> todas = Collections.singletonList(cita("a", "2025-03-10", "09:00"));
        assertTrue(FirestoreDoctorAgendaRepository.deHoy(todas, "2030-01-01").isEmpty());
    }

    @Test
    public void activasExcluyeLasCanceladas() {
        Appointment confirmada = cita("a", "2025-03-11", "09:00");
        confirmada.setStatus(AppointmentStatus.CONFIRMED);
        Appointment pendiente = cita("b", "2025-03-11", "10:00");
        pendiente.setStatus(AppointmentStatus.PENDING);
        Appointment cancelada = cita("c", "2025-03-11", "11:00");
        cancelada.setStatus(AppointmentStatus.CANCELLED);

        List<Appointment> activas = FirestoreDoctorAgendaRepository
                .activas(Arrays.asList(confirmada, pendiente, cancelada));

        assertEquals(2, activas.size());
        assertFalse(activas.contains(cancelada));
    }

    /** El orden es por fecha y, a igual fecha, por hora: es el orden de la consulta. */
    @Test
    public void ordenarPorFechaYHoraPonePrimeroLaCitaMasTemprana() {
        List<Appointment> citas = new ArrayList<>(Arrays.asList(
                cita("tarde", "2025-03-11", "16:00"),
                cita("otroDia", "2025-03-10", "20:00"),
                cita("temprano", "2025-03-11", "08:00")));

        FirestoreDoctorAgendaRepository.ordenarPorFecha(citas);

        assertEquals("otroDia", citas.get(0).getId());
        assertEquals("temprano", citas.get(1).getId());
        assertEquals("tarde", citas.get(2).getId());
    }

    // ───────────────────────── 3. Navegación de días ─────────────────────────

    @Test
    public void hoyUsaElFormatoDeLaApp() {
        String hoy = DoctorAgendaViewModel.hoy();
        assertTrue("Debe ser yyyy-MM-dd pero era: " + hoy,
                hoy.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void diaAnteriorYSiguienteCruzanElCambioDeMes() {
        assertEquals("2025-02-28", DoctorAgendaViewModel.diaAnterior("2025-03-01"));
        assertEquals("2025-03-01", DoctorAgendaViewModel.diaSiguiente("2025-02-28"));
    }

    @Test
    public void diaAnteriorYSiguienteCruzanElCambioDeAnio() {
        assertEquals("2024-12-31", DoctorAgendaViewModel.diaAnterior("2025-01-01"));
        assertEquals("2025-01-01", DoctorAgendaViewModel.diaSiguiente("2024-12-31"));
    }

    /** Una fecha corrupta no debe romper la pantalla: se devuelve tal cual. */
    @Test
    public void unaFechaInvalidaSeDevuelveSinCambios() {
        assertEquals("no-es-fecha", DoctorAgendaViewModel.diaSiguiente("no-es-fecha"));
    }

    // ───────────────────────── 4. Rol y vinculación ─────────────────────────

    @Test
    public void elRolDesconocidoSeTrataComoSiemprePaciente() {
        assertEquals(UserRole.PATIENT, UserRole.fromValue(null));
        assertEquals(UserRole.PATIENT, UserRole.fromValue(""));
        assertEquals(UserRole.PATIENT, UserRole.fromValue("superusuario"));
        assertEquals(UserRole.DOCTOR, UserRole.fromValue("doctor"));
        assertEquals(UserRole.ADMIN, UserRole.fromValue("ADMIN"));
    }

    @Test
    public void unMedicoSeVinculaPorElUidYaGuardado() {
        Doctor ana = doctor("doc_ana", "Ana López", "ana@clinica.com", "uid_ana");
        Doctor luis = doctor("doc_luis", "Luis Ruiz", "luis@clinica.com", "uid_luis");

        Doctor encontrado = FirestoreUserRepository.buscarFichaDeLaCuenta(
                Arrays.asList(luis, ana), "uid_ana", "otro@correo.com");

        assertNotNull(encontrado);
        assertEquals("doc_ana", encontrado.getId());
    }

    /** La invitación pendiente se resuelve por correo, sin distinguir mayúsculas. */
    @Test
    public void unMedicoInvitadoSeVinculaPorSuCorreo() {
        Doctor invitado = doctor("doc_ana", "Ana López", "Ana.Lopez@Clinica.com", "");

        Doctor encontrado = FirestoreUserRepository.buscarFichaDeLaCuenta(
                Collections.singletonList(invitado), "uid_nuevo", "ana.lopez@clinica.com");

        assertNotNull("El correo debe bastar para reclamar la ficha", encontrado);
        assertEquals("doc_ana", encontrado.getId());
    }

    /** Una ficha ya vinculada a otra persona NO se reclama por correo. */
    @Test
    public void unaFichaYaVinculadaNoSeReclamaPorCorreo() {
        Doctor yaVinculado = doctor("doc_ana", "Ana López", "ana@clinica.com", "uid_ana");

        Doctor encontrado = FirestoreUserRepository.buscarFichaDeLaCuenta(
                Collections.singletonList(yaVinculado), "uid_intruso", "ana@clinica.com");

        assertNull("El uid manda sobre el correo", encontrado);
    }

    @Test
    public void unPacienteNormalNoCoincideConNingunaFicha() {
        Doctor ana = doctor("doc_ana", "Ana López", "ana@clinica.com", "uid_ana");

        assertNull(FirestoreUserRepository.buscarFichaDeLaCuenta(
                Collections.singletonList(ana), "uid_paciente", "paciente@gmail.com"));
    }

    @Test
    public void laFichaSinCorreoNiUidNoSeReclama() {
        Doctor huerfano = doctor("doc_x", "Sin datos", "", "");
        assertNull(FirestoreUserRepository.buscarFichaDeLaCuenta(
                Collections.singletonList(huerfano), "uid_x", "cualquiera@x.com"));
    }

    @Test
    public void matchesAccountEsFalsoSinCorreoEnLaFicha() {
        Doctor sinCorreo = doctor("doc_x", "Sin datos", null, "");
        assertFalse(sinCorreo.matchesAccount("uid_x", "a@b.com"));
        assertFalse(sinCorreo.matchesAccount(null, null));
    }

    // ───────────────────────── 5. Alta de médicos ─────────────────────────

    /**
     * El id se deriva del correo, así que pulsar "Registrar" dos veces no crea dos
     * fichas: la segunda escritura va a la misma ruta y sólo actualiza.
     */
    @Test
    public void elIdDelMedicoSeDerivaDelCorreoYEsEstable() {
        String primera = FirestoreAdminRepository.idDesdeEmail("Ana.Lopez@Clinica.com");
        String segunda = FirestoreAdminRepository.idDesdeEmail("  ana.lopez@clinica.com  ");

        assertEquals(primera, segunda);
        assertEquals("doc_ana_lopez_clinica_com", primera);
    }

    /** El id de un documento de Firestore no puede contener '/': se sustituye. */
    @Test
    public void elIdNoContieneBarras() {
        String id = FirestoreAdminRepository.idDesdeEmail("a/b@c.com");
        assertFalse("Un id con '/' rompería la ruta del documento: " + id, id.contains("/"));
    }

    @Test
    public void losCamposDelAltaSonLosQueLeeElCatalogo() {
        Doctor d = doctor("doc_ana", "Ana López", "ana@clinica.com", "");
        d.setSpecialtyName("Cardiología");
        d.setSpecialtyId("cardiologia");
        d.setAge(41);
        d.setYearsExperience(12);
        d.setConsultationFee(50d);

        Map<String, FirestoreApi.Value> campos = FirestoreAdminRepository.aCampos(d,
                "Ana@Clinica.com");

        // Los que el catálogo del paciente necesita para pintar la tarjeta.
        assertNotNull(campos.get("fullName"));
        assertNotNull(campos.get("specialtyName"));
        assertNotNull(campos.get("specialtyId"));
        assertNotNull(campos.get("age"));
        assertNotNull(campos.get("yearsExperience"));
        assertNotNull(campos.get("consultationFee"));
        // Los que permiten reclamar la ficha al entrar.
        assertNotNull("Sin 'email' el médico no podría reclamar su ficha",
                campos.get("email"));
        assertNotNull("'userId' debe existir vacío para rellenarse al entrar",
                campos.get("userId"));
        assertEquals("El correo se guarda en minúsculas",
                "ana@clinica.com", campos.get("email").stringValue);
        assertEquals("La invitación empieza sin uid",
                "", campos.get("userId").stringValue);
    }

    @Test
    public void laEspecialidadSeNormalizaAUnIdSinAcentosNiEspacios() {
        assertEquals("cardiologia", AdminDoctoresViewModel.especialidadIdDesde("Cardiología"));
        assertEquals("medicina_general",
                AdminDoctoresViewModel.especialidadIdDesde("Medicina General"));
        assertEquals("pediatria",
                AdminDoctoresViewModel.especialidadIdDesde("  Pediatría  "));
    }

    /** Si el campo se deja vacío debe haber un id usable, no una cadena vacía. */
    @Test
    public void unaEspecialidadVaciaCaeEnGeneral() {
        assertEquals("general", AdminDoctoresViewModel.especialidadIdDesde(""));
        assertEquals("general", AdminDoctoresViewModel.especialidadIdDesde("   "));
    }

    // ───────────────────────── 6. Menús por rol ─────────────────────────

    @Test
    public void cadaRolTieneSuPropioMenu() {
        int paciente = Navigator.menuForRole(UserRole.PATIENT);
        int medico = Navigator.menuForRole(UserRole.DOCTOR);
        int admin = Navigator.menuForRole(UserRole.ADMIN);

        assertEquals(com.tuempresa.citasmedicas.R.menu.menu_bottom_nav, paciente);
        assertEquals(com.tuempresa.citasmedicas.R.menu.menu_bottom_nav_doctor, medico);
        assertEquals(com.tuempresa.citasmedicas.R.menu.menu_bottom_nav_admin, admin);
        assertTrue("Los tres menús deben ser distintos", medico != paciente);
        assertTrue(admin != paciente);
        assertTrue(admin != medico);
    }

    /** Sin rol (o uno desconocido) se usa el menú del paciente: el permiso mínimo. */
    @Test
    public void sinRolSeUsaElMenuDelPaciente() {
        assertEquals(com.tuempresa.citasmedicas.R.menu.menu_bottom_nav,
                Navigator.menuForRole(null));
    }

    @Test
    public void cadaRolAterrizaEnSuPestanaInicial() {
        assertEquals(Routes.Tab.DOCTORS, Navigator.homeTabForRole(UserRole.PATIENT));
        assertEquals(Routes.Tab.AGENDA, Navigator.homeTabForRole(UserRole.DOCTOR));
        assertEquals(Routes.Tab.ADMIN, Navigator.homeTabForRole(UserRole.ADMIN));
        assertEquals(Routes.Tab.DOCTORS, Navigator.homeTabForRole(null));
    }

    /** Las tres pestañas nuevas resuelven a un fragmento: ninguna queda muerta. */
    @Test
    public void lasPestanasDeCadaRolTienenFragmento() {
        assertNotNull(Navigator.fragmentForTab(Routes.Tab.AGENDA));
        assertNotNull(Navigator.fragmentForTab(Routes.Tab.ADMIN));
        assertNotNull(Navigator.fragmentForTab(Routes.Tab.PROFILE));
    }

    @Test
    public void laPestanaDeAgendaTieneTitulo() {
        assertEquals(com.tuempresa.citasmedicas.R.string.tab_agenda,
                Navigator.titleForTab(Routes.Tab.AGENDA));
        assertEquals(com.tuempresa.citasmedicas.R.string.tab_admin,
                Navigator.titleForTab(Routes.Tab.ADMIN));
    }

    // ───────────────────────── 7. Lectura de citas desde Firestore ─────────────────────────

    @Test
    public void unDocumentoDeCitaSeTraduceAlModelo() {
        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.name = "projects/p/databases/(default)/documents/appointments/abc123";
        doc.fields = new java.util.HashMap<>();
        doc.fields.put("doctorId", FirestoreApi.ofString("doc_ana"));
        doc.fields.put("doctorName", FirestoreApi.ofString("Ana López"));
        doc.fields.put("specialtyName", FirestoreApi.ofString("Cardiología"));
        doc.fields.put("patientName", FirestoreApi.ofString("Marta Gil"));
        doc.fields.put("patientAge", FirestoreApi.ofInteger(34));
        doc.fields.put("date", FirestoreApi.ofString("2025-03-11"));
        doc.fields.put("time", FirestoreApi.ofString("10:30"));
        doc.fields.put("status", FirestoreApi.ofString("Confirmed"));
        doc.fields.put("reason", FirestoreApi.ofString("Revisión"));
        doc.fields.put("ownerId", FirestoreApi.ofString("uid_marta"));

        Appointment a = FirestoreAppointmentMapper.toAppointment(doc);

        assertNotNull(a);
        assertEquals("abc123", a.getId());
        assertEquals("abc123", a.getRemoteId());
        assertEquals("doc_ana", a.getDoctorId());
        assertEquals("Ana López", a.getDoctorName());
        assertEquals("Marta Gil", a.getPatientName());
        assertEquals(34, a.getPatientAge());
        assertEquals("2025-03-11", a.getDate());
        assertEquals("10:30", a.getTime());
        assertEquals(AppointmentStatus.CONFIRMED, a.getStatus());
        assertEquals("Revisión", a.getReason());
        assertEquals("uid_marta", a.getOwnerId());
    }

    /**
     * Un documento viejo (sin {@code ownerId}, creado antes de esta versión) no debe
     * provocar un {@code NullPointerException} en la agenda.
     */
    @Test
    public void unDocumentoIncompletoNoRompeLaLectura() {
        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.name = "projects/p/databases/(default)/documents/appointments/viejo";
        doc.fields = new java.util.HashMap<>();

        Appointment a = FirestoreAppointmentMapper.toAppointment(doc);

        assertNotNull(a);
        assertEquals("viejo", a.getId());
        assertEquals("", a.getDoctorId());
        assertEquals("", a.getOwnerId());
        assertEquals(AppointmentStatus.CONFIRMED, a.getStatus());
    }

    @Test
    public void unDocumentoNuloSeTraduceANulo() {
        assertNull(FirestoreAppointmentMapper.toAppointment(null));
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private static Appointment cita(String id, String fecha, String hora) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setDate(fecha);
        a.setTime(hora);
        a.setStatus(AppointmentStatus.CONFIRMED);
        return a;
    }

    private static Doctor doctor(String id, String nombre, String email, String userId) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setFullName(nombre);
        d.setEmail(email);
        d.setUserId(userId);
        return d;
    }
}
