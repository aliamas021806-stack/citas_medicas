package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.Doctor;

import java.util.List;
import java.util.Locale;

/**
 * Caso de uso: obtiene el listado de doctores, opcionalmente filtrado por
 * especialidad y/o por texto de búsqueda. El filtrado por texto es lógica de
 * negocio, por eso vive en dominio y no en el Fragment.
 */
public class GetDoctorsUseCase extends UseCase<GetDoctorsUseCase.Params, List<Doctor>> {

    /** Parámetros del caso de uso. */
    public static final class Params {
        @Nullable
        public final String specialtyId;
        @Nullable
        public final String query;

        public Params(@Nullable String specialtyId, @Nullable String query) {
            this.specialtyId = specialtyId;
            this.query = query;
        }

        public static Params all() {
            return new Params(null, null);
        }
    }

    private final DoctorRepository repository;

    public GetDoctorsUseCase(DoctorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(Params params, @NonNull Callback<List<Doctor>> callback) {
        final Params safeParams = params != null ? params : Params.all();

        RepositoryCallback<List<Doctor>> bridge = new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                callback.onSuccess(filter(data, safeParams.query));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };

        if (safeParams.specialtyId != null && !safeParams.specialtyId.trim().isEmpty()) {
            repository.getDoctorsBySpecialty(safeParams.specialtyId, bridge);
        } else {
            repository.getDoctors(bridge);
        }
    }

    /** Filtra por nombre de doctor o de especialidad (sin distinguir mayúsculas). */
    private List<Doctor> filter(@Nullable List<Doctor> doctors, @Nullable String query) {
        if (doctors == null) {
            return java.util.Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return doctors;
        }
        String q = query.toLowerCase(Locale.getDefault()).trim();
        java.util.List<Doctor> filtered = new java.util.ArrayList<>();
        for (Doctor d : doctors) {
            String name = d.getFullName() != null
                    ? d.getFullName().toLowerCase(Locale.getDefault()) : "";
            String specialty = d.getSpecialtyName() != null
                    ? d.getSpecialtyName().toLowerCase(Locale.getDefault()) : "";
            if (name.contains(q) || specialty.contains(q)) {
                filtered.add(d);
            }
        }
        return filtered;
    }

    /**
     * Variante offline del caso de uso: aplica la MISMA regla de filtrado sobre
     * una lista ya obtenida, sin volver a la red. Se usa mientras el usuario
     * escribe en el buscador.
     */
    public void executeWithLocalData(@Nullable List<Doctor> source,
                                     @Nullable String query,
                                     @NonNull Callback<List<Doctor>> callback) {
        callback.onSuccess(filter(source, query));
    }
}
