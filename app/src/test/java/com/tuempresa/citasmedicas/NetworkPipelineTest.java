package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.remote.ApiClient;
import com.tuempresa.citasmedicas.data.remote.MedicalApiService;
import com.tuempresa.citasmedicas.data.remote.dto.DoctorDto;
import com.tuempresa.citasmedicas.data.remote.dto.PatientDto;
import com.tuempresa.citasmedicas.data.remote.dto.SpecialtyDto;
import com.tuempresa.citasmedicas.data.remote.dto.TimeSlotDto;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Prueba de integración REAL del pipeline de red, sin mocks de Retrofit:
 * <pre>
 *   MedicalApiService --> Retrofit --> OkHttp --> MockInterceptor
 *                     --> JSON --> Gson --> DTO --> DtoMapper --> modelo
 * </pre>
 * Las llamadas se ejecutan de forma síncrona con {@code execute()}, por lo que no
 * hace falta ni emulador ni servidor: valida que el APK hable con la "API" y que el
 * contrato JSON y los mapeos encajen de verdad.
 */
public class NetworkPipelineTest {

    private MedicalApiService api() {
        return ApiClient.getApi();
    }

    @Test
    public void especialidades_lleganComoDtoMapeadoAlModelo() throws IOException {
        Response<List<SpecialtyDto>> response = api().getSpecialties().execute();

        assertTrue("HTTP " + response.code(), response.isSuccessful());
        assertNotNull(response.body());

        List<SpecialtyDto> dtos = response.body();
        assertFalse("El catálogo no puede estar vacío", dtos.isEmpty());

        // El DTO trae el contrato JSON y el mapper lo convierte a dominio.
        SpecialtyDto first = dtos.get(0);
        assertNotNull(first.getId());
        assertNotNull(first.getName());
        assertEquals("Cardiología", com.tuempresa.citasmedicas.data.remote.dto.DtoMapper
                .toModel(first).getName());
    }

    @Test
    public void doctores_incluyenLaEdadEnElJson() throws IOException {
        Response<List<DoctorDto>> response = api().getDoctors().execute();

        assertTrue(response.isSuccessful());
        List<DoctorDto> dtos = response.body();
        assertNotNull(dtos);
        assertFalse(dtos.isEmpty());

        for (DoctorDto dto : dtos) {
            assertNotNull("Falta el id en el JSON", dto.getId());
            assertNotNull("Falta fullName en el JSON", dto.getFullName());
            assertNotNull("El JSON debe exponer la EDAD del doctor", dto.getAge());
            assertTrue(dto.getAge() > 0);
            // El mapper nunca devuelve null aunque falte un campo.
            assertNotNull(com.tuempresa.citasmedicas.data.remote.dto.DtoMapper.toModel(dto));
        }
    }

    @Test
    public void doctoresPorEspecialidad_filtraEnElServidor() throws IOException {
        Response<List<DoctorDto>> response = api().getDoctorsBySpecialty("esp_01").execute();

        assertTrue(response.isSuccessful());
        List<DoctorDto> dtos = response.body();
        assertNotNull(dtos);
        assertFalse(dtos.isEmpty());
        for (DoctorDto dto : dtos) {
            assertEquals("esp_01", dto.getSpecialtyId());
        }
    }

    @Test
    public void doctorPorId_devuelveElDoctorConcreto() throws IOException {
        Response<DoctorDto> response = api().getDoctorById("doc_01").execute();

        assertTrue(response.isSuccessful());
        DoctorDto dto = response.body();
        assertNotNull(dto);
        assertEquals("doc_01", dto.getId());
        assertEquals("Carlos Ramírez", dto.getFullName());
        assertEquals(52, dto.getAge().intValue());
    }

    @Test
    public void horarios_lleganConFechaHoraYDisponibilidad() throws IOException {
        Response<List<TimeSlotDto>> response = api().getSlots("doc_01").execute();

        assertTrue(response.isSuccessful());
        List<TimeSlotDto> dtos = response.body();
        assertNotNull(dtos);
        assertFalse(dtos.isEmpty());

        boolean disponible = false;
        for (TimeSlotDto dto : dtos) {
            assertNotNull(dto.getDate());
            assertNotNull(dto.getTime());
            assertNotNull("available debe viajar en el JSON", dto.getAvailable());
            if (Boolean.TRUE.equals(dto.getAvailable())) {
                disponible = true;
            }
        }
        assertTrue("Debe haber al menos una franja disponible", disponible);
    }

    @Test
    public void pacientes_exponenLaEdadEnElJson() throws IOException {
        Response<List<PatientDto>> response = api().getPatients().execute();

        assertTrue(response.isSuccessful());
        List<PatientDto> dtos = response.body();
        assertNotNull(dtos);
        assertFalse(dtos.isEmpty());

        for (PatientDto dto : dtos) {
            assertNotNull("El JSON debe incluir la EDAD del paciente", dto.getAge());
            assertTrue(dto.getAge() > 0 && dto.getAge() <= 120);
        }
    }

    @Test
    public void pacientePorId_conservaNombreYEdad() throws IOException {
        Response<PatientDto> response = api().getPatientById("pat_01").execute();

        assertTrue(response.isSuccessful());
        PatientDto dto = response.body();
        assertNotNull(dto);
        assertEquals("Laura Jiménez", dto.getFullName());
        assertEquals(34, dto.getAge().intValue());
    }

    @Test
    public void pacienteInexistente_noRompeElPipeline() throws IOException {
        // El interceptor simula "no existe" devolviendo el literal JSON null.
        Response<PatientDto> response = api().getPatientById("no_existe").execute();

        assertTrue("La respuesta HTTP sigue siendo 200", response.isSuccessful());
        // Cuerpo null: es justo el caso que RemoteCall traduce a onError y no a éxito.
        org.junit.Assert.assertNull(response.body());
    }

    @Test
    public void todasLasLlamadasUsanElMockYNoSalenALaRed() {
        // La URL base es un dominio inexistente: si el interceptor no actuara,
        // cualquier llamada fallaría con UnknownHostException. Que responda 200
        // demuestra que la app funciona 100% offline.
        assertEquals("https://mock.citasmedicas.local/api/",
                ApiClient.getRetrofit().baseUrl().toString());
    }
}
