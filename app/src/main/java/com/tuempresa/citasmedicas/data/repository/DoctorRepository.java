package com.tuempresa.citasmedicas.data.repository;

import com.tuempresa.citasmedicas.data.remote.ApiClient;
import com.tuempresa.citasmedicas.data.remote.MedicalApiService;
import com.tuempresa.citasmedicas.data.remote.dto.DoctorDto;
import com.tuempresa.citasmedicas.data.remote.dto.DtoMapper;
import com.tuempresa.citasmedicas.data.remote.dto.SpecialtyDto;
import com.tuempresa.citasmedicas.data.remote.dto.TimeSlotDto;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.List;

/**
 * Repositorio de doctores y especialidades.
 * <p>
 * Consume {@link MedicalApiService} (respuestas simuladas por el MockInterceptor)
 * y traduce los DTO de red a modelos de dominio con {@link DtoMapper}. La app nunca
 * ve las clases {@code *Dto}.
 */
public class DoctorRepository implements com.tuempresa.citasmedicas.domain.repository.IDoctorRepository {

    private final MedicalApiService api;

    public DoctorRepository() {
        this.api = ApiClient.getApi();
    }

    public void getSpecialties(RepositoryCallback<List<Specialty>> callback) {
        RemoteCall.list(api.getSpecialties(), DtoMapper::toModel, callback);
    }

    public void getDoctors(RepositoryCallback<List<Doctor>> callback) {
        RemoteCall.list(api.getDoctors(), DtoMapper::toModel, callback);
    }

    public void getDoctorsBySpecialty(String specialtyId, RepositoryCallback<List<Doctor>> callback) {
        RemoteCall.list(api.getDoctorsBySpecialty(specialtyId), DtoMapper::toModel, callback);
    }

    public void getDoctorById(String doctorId, RepositoryCallback<Doctor> callback) {
        RemoteCall.single(api.getDoctorById(doctorId), DtoMapper::toModel, callback);
    }

    public void getAvailableSlots(String doctorId, RepositoryCallback<List<TimeSlot>> callback) {
        RemoteCall.list(api.getSlots(doctorId), DtoMapper::toModel, callback);
    }
}
