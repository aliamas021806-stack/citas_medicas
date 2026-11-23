package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas de los datos mock (la "base de datos" del API local).
 * Se ejecutan en la JVM, sin emulador.
 */
public class MockDataTest {

    // ------------------------------------------------------------------
    // Especialidades
    // ------------------------------------------------------------------

    @Test
    public void especialidades_noEstanVacias() {
        List<Specialty> specialties = MockData.getSpecialties();
        assertNotNull(specialties);
        assertFalse("Debe haber al menos una especialidad", specialties.isEmpty());
    }

    @Test
    public void especialidades_tienenIdsUnicos() {
        Set<String> ids = new HashSet<>();
        for (Specialty s : MockData.getSpecialties()) {
            assertTrue("Id duplicado: " + s.getId(), ids.add(s.getId()));
            assertNotNull(s.getName());
            assertFalse(s.getName().trim().isEmpty());
        }
    }

    @Test
    public void getSpecialtyById_encuentraYDevuelveNull() {
        Specialty primera = MockData.getSpecialties().get(0);
        Specialty encontrada = MockData.getSpecialtyById(primera.getId());
        assertNotNull(encontrada);
        assertEquals(primera.getId(), encontrada.getId());
        assertNull(MockData.getSpecialtyById("esp_inexistente"));
    }

    // ------------------------------------------------------------------
    // Doctores
    // ------------------------------------------------------------------

    @Test
    public void doctores_tienenEdadEnRangoValido() {
        for (Doctor d : MockData.getDoctors()) {
            assertTrue("Edad inválida en " + d.getFullName() + ": " + d.getAge(),
                    d.getAge() >= 25 && d.getAge() <= 80);
        }
    }

    @Test
    public void doctores_tienenEspecialidadVinculada() {
        for (Doctor d : MockData.getDoctors()) {
            assertNotNull("Especialidad nula en " + d.getFullName(),
                    MockData.getSpecialtyById(d.getSpecialtyId()));
        }
    }

    @Test
    public void doctores_tienenRatingEnRango() {
        for (Doctor d : MockData.getDoctors()) {
            assertTrue("Rating fuera de rango: " + d.getRating(),
                    d.getRating() >= 0.0 && d.getRating() <= 5.0);
        }
    }

    @Test
    public void doctores_tienenIdsUnicos() {
        Set<String> ids = new HashSet<>();
        for (Doctor d : MockData.getDoctors()) {
            assertTrue("Id duplicado: " + d.getId(), ids.add(d.getId()));
        }
    }

    @Test
    public void getDoctorsBySpecialty_filtraCorrectamente() {
        String specialtyId = MockData.getDoctors().get(0).getSpecialtyId();
        List<Doctor> filtrados = MockData.getDoctorsBySpecialty(specialtyId);
        assertFalse(filtrados.isEmpty());
        for (Doctor d : filtrados) {
            assertEquals(specialtyId, d.getSpecialtyId());
        }
    }

    @Test
    public void getDoctorsBySpecialty_devuelveVacioParaDesconocida() {
        assertTrue(MockData.getDoctorsBySpecialty("esp_inexistente").isEmpty());
    }

    // ------------------------------------------------------------------
    // Pacientes (datos generados por IA)
    // ------------------------------------------------------------------

    @Test
    public void pacientes_tienenEdadEnRangoValido() {
        for (Patient p : MockData.getPatients()) {
            assertTrue("Edad inválida en " + p.getFullName() + ": " + p.getAge(),
                    p.getAge() > 0 && p.getAge() <= 120);
        }
    }

    @Test
    public void pacientes_tienenInformacionClinicaCompleta() {
        for (Patient p : MockData.getPatients()) {
            assertNotNull(p.getBloodType());
            assertFalse(p.getBloodType().trim().isEmpty());
            assertNotNull(p.getInsurance());
            assertNotNull(p.getAllergies());
        }
    }

    @Test
    public void pacientes_tienenIdsUnicos() {
        Set<String> ids = new HashSet<>();
        for (Patient p : MockData.getPatients()) {
            assertTrue("Id duplicado: " + p.getId(), ids.add(p.getId()));
        }
    }

    @Test
    public void getPatientById_devuelveElPacientePorDefecto() {
        Patient p = MockData.getPatientById("pat_01");
        assertNotNull(p);
        assertEquals("pat_01", p.getId());
        assertEquals("Laura Jiménez", p.getFullName());
        assertEquals(34, p.getAge());
    }

    @Test
    public void getPatientById_devuelveNullSiNoExiste() {
        assertNull(MockData.getPatientById("pat_inexistente"));
    }

    @Test
    public void getAveragePatientAge_esCoherente() {
        double media = MockData.getAveragePatientAge();
        assertTrue("Media fuera de rango: " + media, media >= 18 && media <= 90);
    }

    @Test
    public void imcDelPaciente_esCoherente() {
        for (Patient p : MockData.getPatients()) {
            double bmi = p.getBmi();
            assertTrue("IMC fuera de rango: " + bmi, bmi >= 10 && bmi <= 60);
        }
    }

    // ------------------------------------------------------------------
    // Horarios
    // ------------------------------------------------------------------

    @Test
    public void slots_pertenecenAlDoctorSolicitado() {
        String doctorId = MockData.getDoctors().get(0).getId();
        List<TimeSlot> slots = MockData.getAvailableSlots(doctorId);
        assertFalse(slots.isEmpty());
        Set<String> ids = new HashSet<>();
        for (TimeSlot s : slots) {
            assertEquals(doctorId, s.getDoctorId());
            assertTrue("Slot duplicado: " + s.getId(), ids.add(s.getId()));
        }
    }

    @Test
    public void slots_tienenDisponiblesYNoDisponibles() {
        List<TimeSlot> slots = MockData.getAvailableSlots(MockData.getDoctors().get(0).getId());
        boolean hayDisponible = false;
        boolean hayOcupado = false;
        for (TimeSlot s : slots) {
            if (s.isAvailable()) {
                hayDisponible = true;
            } else {
                hayOcupado = true;
            }
        }
        assertTrue("Debe haber al menos un slot disponible", hayDisponible);
        assertTrue("Debe haber al menos un slot ocupado", hayOcupado);
    }

    @Test
    public void getSlotsByDate_filtraPorFecha() {
        String doctorId = MockData.getDoctors().get(0).getId();
        String fecha = MockData.getSelectableDates().get(0);
        List<TimeSlot> slots = MockData.getSlotsByDate(doctorId, fecha);
        assertFalse(slots.isEmpty());
        for (TimeSlot s : slots) {
            assertEquals(fecha, s.getDate());
        }
    }

    @Test
    public void getSelectableDates_devuelveSieteFechasConFormato() {
        List<String> fechas = MockData.getSelectableDates();
        assertEquals(7, fechas.size());
        for (String f : fechas) {
            assertTrue("Formato inválido: " + f, f.matches("\\d{4}-\\d{2}-\\d{2}"));
        }
    }
}
