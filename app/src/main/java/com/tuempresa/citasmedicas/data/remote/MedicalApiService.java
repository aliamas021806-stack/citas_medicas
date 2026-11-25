package com.tuempresa.citasmedicas.data.remote;

import com.tuempresa.citasmedicas.data.remote.dto.DoctorDto;
import com.tuempresa.citasmedicas.data.remote.dto.PatientDto;
import com.tuempresa.citasmedicas.data.remote.dto.SpecialtyDto;
import com.tuempresa.citasmedicas.data.remote.dto.TimeSlotDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Definición de la API REST (Retrofit).
 * <p>
 * Devuelve <b>DTO</b> ({@code *Dto}), no modelos de dominio. La traducción a modelo
 * la hace {@code DtoMapper} en los repositorios, de forma que la app queda
 * desacoplada del contrato JSON: si el backend renombra un campo, solo cambia el
 * DTO y su mapper.
 * <p>
 * En un backend real apuntaría a un servidor; aquí las respuestas las intercepta
 * {@link MockInterceptor}, que devuelve JSON sintético (generado por IA). Incluye
 * los endpoints de PACIENTES con su información y EDAD.
 */
public interface MedicalApiService {

    // ----------------------- Catálogo médico -----------------------
    @GET("specialties")
    Call<List<SpecialtyDto>> getSpecialties();

    @GET("doctors")
    Call<List<DoctorDto>> getDoctors();

    @GET("doctors/specialty/{specialtyId}")
    Call<List<DoctorDto>> getDoctorsBySpecialty(@Path("specialtyId") String specialtyId);

    @GET("doctors/{doctorId}")
    Call<DoctorDto> getDoctorById(@Path("doctorId") String doctorId);

    @GET("slots/{doctorId}")
    Call<List<TimeSlotDto>> getSlots(@Path("doctorId") String doctorId);

    // ----------------------- Pacientes (info + EDAD) -----------------------
    @GET("patients")
    Call<List<PatientDto>> getPatients();

    @GET("patients/{patientId}")
    Call<PatientDto> getPatientById(@Path("patientId") String patientId);
}
