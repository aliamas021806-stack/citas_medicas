package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.AppointmentMapper;
import com.tuempresa.citasmedicas.util.AuthValidator;

import org.junit.Test;

/**
 * Pruebas de la lógica de negocio pura (sin Android): validaciones de
 * autenticación, mapeo Room ⇄ modelo y presentación del nombre del doctor.
 */
public class BusinessLogicTest {

    // ------------------------------------------------------------------
    // AuthValidator: email
    // ------------------------------------------------------------------

    @Test
    public void email_valido() {
        assertTrue(AuthValidator.isValidEmail("ana@correo.com"));
        assertTrue(AuthValidator.isValidEmail("ana.perez+test@sub.correo.es"));
    }

    @Test
    public void email_invalido() {
        assertFalse(AuthValidator.isValidEmail("ana"));
        assertFalse(AuthValidator.isValidEmail("ana@"));
        assertFalse(AuthValidator.isValidEmail("@correo.com"));
        assertFalse(AuthValidator.isValidEmail("ana@correo"));
        assertFalse(AuthValidator.isValidEmail(""));
    }

    @Test
    public void email_nullEsInvalido() {
        assertFalse(AuthValidator.isValidEmail(null));
    }

    // ------------------------------------------------------------------
    // AuthValidator: contraseña
    // ------------------------------------------------------------------

    @Test
    public void password_cortaEsInvalida() {
        assertFalse(AuthValidator.isValidPassword("123"));
        assertFalse(AuthValidator.isValidPassword(""));
    }

    @Test
    public void password_deCuatroCaracteresEsValida() {
        assertTrue(AuthValidator.isValidPassword("1234"));
        assertTrue(AuthValidator.isValidPassword("secreta"));
    }

    @Test
    public void password_nullEsInvalida() {
        assertFalse(AuthValidator.isValidPassword(null));
    }

    // ------------------------------------------------------------------
    // AuthValidator: edad
    // ------------------------------------------------------------------

    @Test
    public void edad_limitesValidos() {
        assertTrue(AuthValidator.isValidAge(1));
        assertTrue(AuthValidator.isValidAge(34));
        assertTrue(AuthValidator.isValidAge(120));
    }

    @Test
    public void edad_fueraDeRango() {
        assertFalse(AuthValidator.isValidAge(0));
        assertFalse(AuthValidator.isValidAge(-5));
        assertFalse(AuthValidator.isValidAge(121));
        assertFalse(AuthValidator.isValidAge(200));
    }

    // ------------------------------------------------------------------
    // AuthValidator: validaciones completas
    // ------------------------------------------------------------------

    @Test
    public void validateLogin_detectaEmailInvalido() {
        assertNotNull(AuthValidator.validateLogin("no-es-email", "1234"));
    }

    @Test
    public void validateLogin_detectaPasswordCorta() {
        assertNotNull(AuthValidator.validateLogin("ana@correo.com", "12"));
    }

    @Test
    public void validateLogin_aceptaCredencialesValidas() {
        assertNull(AuthValidator.validateLogin("ana@correo.com", "1234"));
    }

    @Test
    public void validateRegister_detectaNombreCorto() {
        assertNotNull(AuthValidator.validateRegister("Al", "ana@correo.com", "1234", 30));
    }

    @Test
    public void validateRegister_detectaEdadFueraDeRango() {
        assertNotNull(AuthValidator.validateRegister("Ana Pérez", "ana@correo.com", "1234", 200));
    }

    @Test
    public void validateRegister_detectaEmailNulo() {
        assertNotNull(AuthValidator.validateRegister("Ana Pérez", null, "1234", 30));
    }

    @Test
    public void validateRegister_aceptaDatosValidos() {
        assertNull(AuthValidator.validateRegister("Ana Pérez", "ana@correo.com", "1234", 30));
    }

    // ------------------------------------------------------------------
    // AppointmentMapper
    // ------------------------------------------------------------------

    @Test
    public void mapper_preservaLaEdadDelPacienteEnAmbosSentidos() {
        Appointment a = new Appointment();
        a.setDoctorId("doc_01");
        a.setDoctorName("Carlos Ramírez");
        a.setSpecialtyName("Cardiología");
        a.setPatientName("Laura Jiménez");
        a.setPatientAge(41);
        a.setDate("2026-03-10");
        a.setTime("10:00");
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setReason("Revisión");

        AppointmentEntity e = AppointmentMapper.toEntity(a);
        assertEquals(41, e.patientAge);
        assertEquals("Laura Jiménez", e.patientName);

        Appointment vuelta = AppointmentMapper.toModel(e);
        assertNotNull(vuelta);
        assertEquals(41, vuelta.getPatientAge());
        assertEquals("Laura Jiménez", vuelta.getPatientName());
    }

    @Test
    public void mapper_usaConfirmedComoEstadoPorDefecto() {
        Appointment a = new Appointment();
        a.setDoctorId("doc_01");
        a.setDoctorName("Carlos Ramírez");
        a.setPatientName("Ana");
        a.setPatientAge(30);
        a.setDate("2026-03-10");
        a.setTime("09:00");
        a.setStatus(null);

        AppointmentEntity e = AppointmentMapper.toEntity(a);
        assertEquals(AppointmentStatus.CONFIRMED.getValue(), e.status);
    }

    @Test
    public void mapper_rellenaCreatedAtSiFalta() {
        Appointment a = new Appointment();
        a.setDoctorId("doc_01");
        a.setPatientName("Ana");
        a.setPatientAge(30);
        a.setDate("2026-03-10");
        a.setTime("09:00");
        a.setCreatedAt(0L);

        AppointmentEntity e = AppointmentMapper.toEntity(a);
        assertTrue("createdAt debe rellenarse", e.createdAt > 0);
    }

    // ------------------------------------------------------------------
    // Doctor: nombre para mostrar
    // ------------------------------------------------------------------

    @Test
    public void doctor_anteponeDrParaNombreMasculino() {
        Doctor d = new Doctor();
        d.setFullName("Carlos Ramírez");
        assertEquals("Dr. Carlos Ramírez", d.getDisplayName());
    }

    @Test
    public void doctor_anteponeDraParaNombreFemenino() {
        Doctor d = new Doctor();
        d.setFullName("María González");
        assertEquals("Dra. María González", d.getDisplayName());
    }

    @Test
    public void doctor_sinNombreDevuelveGenerico() {
        Doctor d = new Doctor();
        d.setFullName("");
        assertEquals("Doctor", d.getDisplayName());
    }

    // ------------------------------------------------------------------
    // User
    // ------------------------------------------------------------------

    @Test
    public void user_calculaIniciales() {
        User u = new User("u1", "Ana Pérez", "ana@correo.com", "+34 600 000 000", 41);
        assertEquals("AP", u.getInitials());
    }

    @Test
    public void user_conservaLaEdad() {
        User u = new User("u1", "Ana Pérez", "ana@correo.com", "+34 600 000 000", 41);
        assertEquals(41, u.getAge());
    }
}
