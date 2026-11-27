package com.tuempresa.citasmedicas.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pruebas de la <b>agenda por prioridad</b> y de los estados nuevos.
 * <p>
 * El problema que motivó este trabajo, tal como lo describió el usuario: «al entrar como
 * médico me dice que no tengo citas, pero ya registré una para hoy a las 12; y quiero ver
 * las pendientes por atender y aparte las ya atendidas, dando prioridad a las que faltan».
 * <p>
 * Aquí se fija el comportamiento esperado sin depender de la red ni de Android:
 * <ul>
 *   <li>Una cita <b>pendiente</b> y una <b>atendida</b> caen en grupos distintos, nunca
 *       mezcladas.</li>
 *   <li>Las pendientes van <b>por hora ascendente</b>: la siguiente consulta primero.</li>
 *   <li>Una cita de <b>otro día</b> no aparece en el grupo de atendidas del día
 *       consultado, pero sí se avisa si quedó pendiente.</li>
 *   <li>Una cita <b>cancelada</b> no cuenta en ninguno de los dos grupos.</li>
 *   <li>Los estados nuevos se leen de Firestore con tolerancia a las distintas formas de
 *       escribirlos a mano en la consola.</li>
 * </ul>
 */
public class AgendaPrioridadTest {

    private static final String HOY = "2026-11-26";
    private static final String AYER = "2026-11-25";

    /** Cita mínima para las pruebas. */
    private static Appointment cita(String id, String fecha, String hora,
                                    AppointmentStatus estado) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setDate(fecha);
        a.setTime(hora);
        a.setStatus(estado);
        a.setDoctorId("doc_ana");
        a.setPatientName("Paciente " + id);
        return a;
    }

    // ───────────────────── 1. Estados nuevos ─────────────────────

    /**
     * {@code ATTENDED} y {@code NO_SHOW} son estados nuevos de esta versión: sin ellos no
     * hay forma de distinguir lo ya atendido de lo que falta, que es justo lo que se
     * pedía.
     */
    @Test
    public void losEstadosNuevosExistenYSeLeenDeFirestore() {
        assertEquals(AppointmentStatus.ATTENDED, AppointmentStatus.fromValue("Attended"));
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("NoShow"));
    }

    /**
     * En la consola de Firebase es fácil escribir {@code no_show} o {@code "No Show"}.
     * Si eso cayera a {@code CONFIRMED}, la cita volvería a la lista de pendientes sin
     * avisar, así que se aceptan las variantes.
     */
    @Test
    public void elEstadoToleraComoSepEscribaEnLaConsola() {
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("no_show"));
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("No Show"));
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("no show"));
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("NO_SHOW"));
        assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromValue("noshow"));
    }

    /**
     * Un valor desconocido debe caer en {@code CONFIRMED} (pendiente), nunca en
     * {@code ATTENDED}: dar por atendida una cita por un error de escritura ocultaría
     * trabajo real.
     */
    @Test
    public void unEstadoDesconocidoNuncaSeInterpretaComoAtendido() {
        AppointmentStatus leido = AppointmentStatus.fromValue("loquesea");
        assertEquals(AppointmentStatus.CONFIRMED, leido);
        assertFalse(leido.esAtendida());
        assertTrue(leido.esPendiente());
    }

    @Test
    public void pendienteYAtendidaSonGruposExcluyentesYSinHuecos() {
        for (AppointmentStatus s : AppointmentStatus.values()) {
            if (s == AppointmentStatus.CANCELLED) {
                continue;
            }
            boolean pendiente = s.esPendiente();
            boolean atendida = s.esAtendida();
            assertTrue("Cada estado debe estar en un grupo: " + s, pendiente || atendida);
            assertFalse("No puede estar en los dos: " + s, pendiente && atendida);
        }
    }

    /** Una cancelada no exige trabajo: no debe aparecer en ningún grupo. */
    @Test
    public void unaCitaCanceladaNoEsNiPendienteNiAtendida() {
        assertFalse(AppointmentStatus.CANCELLED.esPendiente());
        assertFalse(AppointmentStatus.CANCELLED.esAtendida());
        assertFalse(AppointmentStatus.CANCELLED.sePuedeAtender());
    }

    /** Una ya atendida no ofrece el botón otra vez. */
    @Test
    public void unaCitaAtendidaYaNoSePuedeVolverAAtender() {
        assertFalse(AppointmentStatus.ATTENDED.sePuedeAtender());
        assertTrue(AppointmentStatus.CONFIRMED.sePuedeAtender());
        assertTrue(AppointmentStatus.PENDING.sePuedeAtender());
        // Una inasistencia sí: hay que decidir si se reprograma.
        assertTrue(AppointmentStatus.NO_SHOW.sePuedeAtender());
    }

    // ───────────────────── 2. Separación en dos grupos ─────────────────────

    /**
     * El caso exacto del usuario: una cita de hoy a las 12:00. Debe salir en
     * <b>pendientes</b>, no en atendidas, para que la vea al entrar.
     */
    @Test
    public void unaCitaDeHoyPendienteApareceEnPendientes() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("1", HOY, "12:00", AppointmentStatus.CONFIRMED));

        List<Appointment> pendientes =
                FirestoreDoctorAgendaRepository.pendientes(todas, HOY);
        List<Appointment> atendidas =
                FirestoreDoctorAgendaRepository.atendidas(todas, HOY);

        assertEquals(1, pendientes.size());
        assertEquals("12:00", pendientes.get(0).getTime());
        assertTrue(atendidas.isEmpty());
    }

    @Test
    public void unaCitaAtendidaVaAlGrupoDeAtendidasYNoAlDePendientes() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("1", HOY, "09:00", AppointmentStatus.ATTENDED));

        assertTrue(FirestoreDoctorAgendaRepository.pendientes(todas, HOY).isEmpty());
        List<Appointment> atendidas =
                FirestoreDoctorAgendaRepository.atendidas(todas, HOY);
        assertEquals(1, atendidas.size());
        assertEquals("1", atendidas.get(0).getId());
    }

    /**
     * Lo que se pedía explícitamente: <b>prioridad a las que faltan por atender</b>. La
     * de las 12:00 sigue pendiente aunque una de las 09:00 ya esté atendida, y debe ser
     * la primera de la lista de pendientes.
     */
    @Test
    public void lasPendientesSeOrdenanPorHoraYLasAtendidasQuedanAparte() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("a", HOY, "09:00", AppointmentStatus.ATTENDED));
        todas.add(cita("b", HOY, "12:00", AppointmentStatus.CONFIRMED));
        todas.add(cita("c", HOY, "10:30", AppointmentStatus.CONFIRMED));

        List<Appointment> pendientes =
                FirestoreDoctorAgendaRepository.pendientes(todas, HOY);

        // Las 10:30 va antes que las 12:00: la siguiente consulta primero.
        assertEquals(2, pendientes.size());
        assertEquals("10:30", pendientes.get(0).getTime());
        assertEquals("12:00", pendientes.get(1).getTime());
    }

    /**
     * Si quedó una cita pendiente de ayer, esconderla sería peor que mostrarla fuera de
     * fecha: el médico necesita resolverla. Por eso las pendientes no se limitan al día.
     */
    @Test
    public void unaPendienteDeAyerSigueAvisandoPeroVaDespuesDeLasDeHoy() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("vieja", AYER, "08:00", AppointmentStatus.CONFIRMED));
        todas.add(cita("nueva", HOY, "12:00", AppointmentStatus.CONFIRMED));

        List<Appointment> pendientes =
                FirestoreDoctorAgendaRepository.pendientes(todas, HOY);

        assertEquals(2, pendientes.size());
        // La de hoy primero, aunque la de ayer sea más temprana en el calendario.
        assertEquals("nueva", pendientes.get(0).getId());
        assertEquals("vieja", pendientes.get(1).getId());
    }

    /**
     * El histórico sí se limita al día que se está mirando: si se mezclaran los días,
     * «Ya atendidas» acabaría siendo una lista interminable.
     */
    @Test
    public void lasAtendidasSoloMuestranElDiaConsultado() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("hoy", HOY, "09:00", AppointmentStatus.ATTENDED));
        todas.add(cita("ayer", AYER, "09:00", AppointmentStatus.ATTENDED));

        List<Appointment> atendidas =
                FirestoreDoctorAgendaRepository.atendidas(todas, HOY);
        assertEquals(1, atendidas.size());
        assertEquals("hoy", atendidas.get(0).getId());
    }

    @Test
    public void unaCanceladaNoApareceEnNingunGrupo() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("x", HOY, "11:00", AppointmentStatus.CANCELLED));

        assertTrue(FirestoreDoctorAgendaRepository.pendientes(todas, HOY).isEmpty());
        assertTrue(FirestoreDoctorAgendaRepository.atendidas(todas, HOY).isEmpty());
    }

    /** Una inasistencia sigue pendiente de decisión: se muestra, no se esconde. */
    @Test
    public void unaInasistenciaSigueEnPendientesParaPoderReprogramarla() {
        List<Appointment> todas = new ArrayList<>();
        todas.add(cita("ns", HOY, "16:00", AppointmentStatus.NO_SHOW));

        List<Appointment> pendientes =
                FirestoreDoctorAgendaRepository.pendientes(todas, HOY);
        assertEquals(1, pendientes.size());
        assertEquals(AppointmentStatus.NO_SHOW, pendientes.get(0).getStatus());
    }

    // ───────────────────── 3. Casos límite ─────────────────────

    @Test
    public void unaListaVaciaNoRompeLaSeparacion() {
        List<Appointment> vacia = new ArrayList<>();
        assertTrue(FirestoreDoctorAgendaRepository.pendientes(vacia, HOY).isEmpty());
        assertTrue(FirestoreDoctorAgendaRepository.atendidas(vacia, HOY).isEmpty());
    }

    /** Una cita sin estado (documento antiguo) se trata como confirmada, no atendida. */
    @Test
    public void unaCitaSinEstadoSeTrataComoPendiente() {
        Appointment sinEstado = cita("s", HOY, "13:00", null);
        List<Appointment> todas = new ArrayList<>();
        todas.add(sinEstado);

        List<Appointment> pendientes =
                FirestoreDoctorAgendaRepository.pendientes(todas, HOY);
        assertEquals(1, pendientes.size());
        assertTrue(FirestoreDoctorAgendaRepository.atendidas(todas, HOY).isEmpty());
    }

    /**
     * El estado se envía a Firestore con el valor en PascalCase ({@code "Attended"}), que
     * es el que leen las reglas y el que se ve en la consola.
     */
    @Test
    public void elValorQueSeEscribeEnFirestoreEsElEsperado() {
        assertEquals("Attended", AppointmentStatus.ATTENDED.getValue());
        assertEquals("NoShow", AppointmentStatus.NO_SHOW.getValue());
    }

    @Test
    public void laNormalizacionDeEspecialidadQuitaAcentosYEspacios() {
        assertEquals("cardiologia",
                FirestoreUserRepository.normalizarEspecialidad("Cardiología"));
        assertEquals("medicina_general",
                FirestoreUserRepository.normalizarEspecialidad("Medicina General"));
        assertEquals("general", FirestoreUserRepository.normalizarEspecialidad("   "));
    }
}
