package com.tuempresa.citasmedicas.data.repository;

import com.tuempresa.citasmedicas.data.remote.ApiClient;
import com.tuempresa.citasmedicas.data.remote.MedicalApiService;
import com.tuempresa.citasmedicas.data.remote.dto.DtoMapper;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Patient;

import java.util.List;

/**
 * Repositorio de PACIENTES.
 * <p>
 * Obtiene la información del paciente (incluida su EDAD) desde la API mock, cuyos
 * datos son generados por IA en {@code MockData}, y la traduce de DTO a modelo.
 */
public class PatientRepository implements com.tuempresa.citasmedicas.domain.repository.IPatientRepository {

    private final MedicalApiService api;

    public PatientRepository() {
        this.api = ApiClient.getApi();
    }

    public void getPatients(RepositoryCallback<List<Patient>> callback) {
        RemoteCall.list(api.getPatients(), DtoMapper::toModel, callback);
    }

    public void getPatientById(String patientId, RepositoryCallback<Patient> callback) {
        RemoteCall.single(api.getPatientById(patientId), DtoMapper::toModel, callback);
    }
}
