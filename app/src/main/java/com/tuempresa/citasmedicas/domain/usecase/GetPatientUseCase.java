package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.Patient;

/**
 * Caso de uso: obtiene el expediente del paciente (incluida su EDAD).
 */
public class GetPatientUseCase extends UseCase<String, Patient> {

    private final IPatientRepository repository;

    public GetPatientUseCase(IPatientRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(String patientId, @NonNull Callback<Patient> callback) {
        if (patientId == null || patientId.trim().isEmpty()) {
            callback.onError("No se especificó el paciente");
            return;
        }
        repository.getPatientById(patientId, new RepositoryCallback<Patient>() {
            @Override
            public void onSuccess(Patient data) {
                if (data == null) {
                    callback.onError("No se encontró la información del paciente");
                    return;
                }
                callback.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
