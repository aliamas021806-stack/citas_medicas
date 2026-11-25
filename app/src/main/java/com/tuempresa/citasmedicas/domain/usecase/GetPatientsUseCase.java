package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.Patient;

import java.util.List;

/**
 * Caso de uso: obtiene el listado completo de pacientes.
 */
public class GetPatientsUseCase extends UseCase<Void, List<Patient>> {

    private final IPatientRepository repository;

    public GetPatientsUseCase(@NonNull IPatientRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(Void params, @NonNull Callback<List<Patient>> callback) {
        repository.getPatients(new RepositoryCallback<List<Patient>>() {
            @Override
            public void onSuccess(List<Patient> data) {
                if (data == null || data.isEmpty()) {
                    callback.onError("No hay pacientes registrados");
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
