package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.Specialty;

import java.util.List;

/**
 * Caso de uso: obtiene el catálogo de especialidades médicas.
 */
public class GetSpecialtiesUseCase extends UseCase<Void, List<Specialty>> {

    private final DoctorRepository repository;

    public GetSpecialtiesUseCase(DoctorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(Void params, @NonNull Callback<List<Specialty>> callback) {
        repository.getSpecialties(new RepositoryCallback<List<Specialty>>() {
            @Override
            public void onSuccess(List<Specialty> data) {
                if (data == null || data.isEmpty()) {
                    callback.onError("No hay especialidades disponibles");
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
