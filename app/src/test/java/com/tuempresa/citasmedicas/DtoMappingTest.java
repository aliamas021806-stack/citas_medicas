package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.data.remote.MockInterceptor;
import com.tuempresa.citasmedicas.data.remote.dto.DoctorDto;
import com.tuempresa.citasmedicas.data.remote.dto.DtoMapper;
import com.tuempresa.citasmedicas.data.remote.dto.PatientDto;
import com.tuempresa.citasmedicas.data.remote.dto.SpecialtyDto;
import com.tuempresa.citasmedicas.data.remote.dto.TimeSlotDto;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pruebas de la capa de red: contrato JSON (DTO) y conversión a modelo de dominio.
 * JVM puro, sin Android.
 */
public class DtoMappingTest {

    private final Gson gson = new Gson();

    // ------------------------------------------------- deserialización Gson

    @Test
    public void doctorDto_deserializaElJsonDelServidor() {
        String json = "{\"id\":\"doc_01\",\"fullName\":\"Carlos Ramírez\","
                + "\"specialtyId\":\"esp_01\",\"specialtyName\":\"Cardiología\","
                + "\"bio\":\"Cardiólogo\",\"rating\":4.8,\"yearsExperience\":15,"
                + "\"age\":52,\"consultationFee\":45.0,"
                + "\"hospital\":\"Hospital Central\",\"photoUrl\":\"\"}";

        DoctorDto dto = gson.fromJson(json, DoctorDto.class);

        assertNotNull(dto);
        assertEquals("doc_01", dto.getId());
        assertEquals("Carlos Ramírez", dto.getFullName());
        assertEquals(52, dto.getAge().intValue());
        assertEquals(4.8, dto.getRating(), 0.001);
    }

    @Test
    public void doctorDto_mapeaAlModeloDeDominio() {
        DoctorDto dto = new DoctorDto("doc_01", "Carlos Ramírez", "esp_01", "Cardiología",
                "bio", 4.8, 15, 52, 45.0, "Hospital Central", "");

        Doctor doctor = DtoMapper.toModel(dto);

        assertEquals("doc_01", doctor.getId());
        assertEquals("Carlos Ramírez", doctor.getFullName());
        assertEquals(52, doctor.getAge());
        assertEquals("Cardiología", doctor.getSpecialtyName());
        assertEquals("Dr. Carlos Ramírez", doctor.getDisplayName());
    }

    // ------------------------------------------- campos nulos o ausentes

    @Test
    public void doctorDto_conCamposNulosNoLanzaExcepcion() {
        DoctorDto dto = new DoctorDto("doc_x", null, null, null, null,
                null, null, null, null, null, null);

        Doctor doctor = DtoMapper.toModel(dto);

        assertNotNull(doctor);
        assertEquals("doc_x", doctor.getId());
        assertEquals("", doctor.getFullName());
        assertEquals(0, doctor.getAge());
        assertEquals(0, doctor.getYearsExperience());
        assertEquals(0d, doctor.getConsultationFee(), 0.001);
    }

    @Test
    public void doctorDto_nuloDevuelveModeloVacioEnVezDeNull() {
        Doctor doctor = DtoMapper.toModel((DoctorDto) null);

        assertNotNull(doctor);
        assertEquals("", doctor.getFullName());
        assertEquals(0, doctor.getAge());
    }

    @Test
    public void ratingFueraDeRango_seAcotaA0y5() {
        DoctorDto alto = new DoctorDto("d1", "Alto", "e", "E", "", 9.9, 1, 30, 10d, "", "");
        DoctorDto bajo = new DoctorDto("d2", "Bajo", "e", "E", "", -3.0, 1, 30, 10d, "", "");

        assertEquals(5.0, DtoMapper.toModel(alto).getRating(), 0.001);
        assertEquals(0.0, DtoMapper.toModel(bajo).getRating(), 0.001);
    }

    @Test
    public void edadYCosteNegativos_seNormalizanACero() {
        DoctorDto dto = new DoctorDto("d1", "X", "e", "E", "", 4.0, -5, -20, -99d, "", "");

        Doctor doctor = DtoMapper.toModel(dto);

        assertEquals(0, doctor.getAge());
        assertEquals(0, doctor.getYearsExperience());
        assertEquals(0d, doctor.getConsultationFee(), 0.001);
    }

    // ------------------------------------------------------------- pacientes

    @Test
    public void patientDto_conservaLaEdadYLosDatosClinicos() {
        PatientDto dto = new PatientDto("pat_01", "Laura Jiménez", "l@c.com", "+34 600",
                34, "O+", "Femenino", "Calle 1", "SaludPlus", "Penicilina", "Ninguna",
                165.0, 62.0, "Juan", 1700000000000L);

        Patient patient = DtoMapper.toModel(dto);

        assertEquals("Laura Jiménez", patient.getFullName());
        assertEquals(34, patient.getAge());
        assertEquals("O+", patient.getBloodType());
        assertEquals("SaludPlus", patient.getInsurance());
        assertTrue(patient.getBmi() > 0);
    }

    @Test
    public void patientDto_sinEdadNoInventaValor() {
        PatientDto dto = new PatientDto("pat_x", "Sin Edad", "", "", null,
                "", "", "", "", "", "", null, null, "", null);

        Patient patient = DtoMapper.toModel(dto);

        assertEquals(0, patient.getAge());
        assertEquals(0d, patient.getHeightCm(), 0.001);
        assertEquals(0d, patient.getWeightKg(), 0.001);
        // Sin estatura no se puede calcular el IMC: debe ser 0, no NaN ni Infinity.
        assertEquals(0d, patient.getBmi(), 0.001);
    }

    // -------------------------------------------------------------- horarios

    @Test
    public void timeSlotDto_availableNuloSeInterpretaComoNoDisponible() {
        TimeSlotDto dto = new TimeSlotDto("s1", "doc_01", "2025-06-10", "10:00", null);

        TimeSlot slot = DtoMapper.toModel(dto);

        assertFalse("Un slot sin dato no debe ofrecerse", slot.isAvailable());
        assertEquals("2025-06-10", slot.getDate());
    }

    @Test
    public void timeSlotDto_availableTrueSeRespeta() {
        assertTrue(DtoMapper.toModel(
                new TimeSlotDto("s1", "d", "2025-06-10", "10:00", true)).isAvailable());
    }

    // -------------------------------------------------------------- listas

    @Test
    public void listasConElementosNulos_seFiltranSinRomper() {
        List<DoctorDto> dtos = new ArrayList<>();
        dtos.add(new DoctorDto("d1", "Uno", "e", "E", "", 4d, 1, 30, 10d, "", ""));
        dtos.add(null);
        dtos.add(new DoctorDto("d2", "Dos", "e", "E", "", 4d, 1, 30, 10d, "", ""));

        List<Doctor> doctors = DtoMapper.toDoctorList(dtos);

        assertEquals(2, doctors.size());
    }

    @Test
    public void listaNulaDevuelveListaVaciaNoNull() {
        assertNotNull(DtoMapper.toDoctorList(null));
        assertTrue(DtoMapper.toDoctorList(null).isEmpty());
        assertNotNull(DtoMapper.toSpecialtyList(null));
        assertNotNull(DtoMapper.toPatientList(null));
        assertNotNull(DtoMapper.toTimeSlotList(null));
    }

    // ------------------------------------------- payload del MockInterceptor

    @Test
    public void payloadDelMock_esUnJsonValidoConElContratoEsperado() {
        DoctorDto dto = MockInterceptor.doctorToDto(MockData.getDoctorById("doc_01"));
        assertNotNull(dto);

        String json = gson.toJson(dto);
        DoctorDto releido = gson.fromJson(json, DoctorDto.class);

        assertEquals(dto.getId(), releido.getId());
        assertEquals(dto.getAge(), releido.getAge());
        assertEquals(dto.getRating(), releido.getRating());
        // El JSON debe usar los nombres del contrato, no los del modelo Java.
        assertTrue(json.contains("\"fullName\""));
        assertTrue(json.contains("\"age\""));
        assertTrue(json.contains("\"specialtyName\""));
    }

    @Test
    public void payloadDelMock_conservaTodasLasEdadesDePacientes() {
        List<PatientDto> dtos = new ArrayList<>();
        for (Patient p : MockData.getPatients()) {
            dtos.add(MockInterceptor.patientToDto(p));
        }

        assertFalse(dtos.isEmpty());
        for (PatientDto dto : dtos) {
            assertNotNull(dto);
            assertNotNull("La edad viaja en el payload", dto.getAge());
        }
    }

    @Test
    public void specialtyDto_mapeaTodosLosCampos() {
        SpecialtyDto dto = new SpecialtyDto("esp_01", "Cardiología", "🫀", "Corazón", 3);

        Specialty specialty = DtoMapper.toModel(dto);

        assertEquals("esp_01", specialty.getId());
        assertEquals("Cardiología", specialty.getName());
        assertEquals("🫀", specialty.getIconEmoji());
        assertEquals(3, specialty.getDoctorCount());
    }

    @Test
    public void specialtyDto_sinDoctorCountDevuelveCero() {
        SpecialtyDto dto = new SpecialtyDto("esp_01", "Cardiología", "🫀", "Corazón", null);

        assertEquals(0, DtoMapper.toModel(dto).getDoctorCount());
    }

    @Test
    public void tipoRetornoDeLaApiEsDtoYNoModelo() throws NoSuchMethodException {
        // Garantiza que el contrato de red no se acople al dominio: si alguien
        // cambia la firma para devolver Doctor, esta prueba falla.
        java.lang.reflect.Method m = com.tuempresa.citasmedicas.data.remote.MedicalApiService.class
                .getMethod("getDoctorById", String.class);
        String ret = m.getGenericReturnType().toString();
        assertTrue("La API debe devolver DTO, no modelo: " + ret, ret.contains("DoctorDto"));
        assertFalse(ret.contains("model.Doctor"));
    }
}
