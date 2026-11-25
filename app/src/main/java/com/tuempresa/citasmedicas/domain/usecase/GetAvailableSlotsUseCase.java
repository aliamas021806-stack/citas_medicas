package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Caso de uso: obtiene los horarios disponibles de un doctor para una fecha.
 * <p>
 * Regla de negocio: solo se ofrecen franjas marcadas como disponibles.
 */
public class GetAvailableSlotsUseCase extends UseCase<GetAvailableSlotsUseCase.Params, List<TimeSlot>> {

    /** Parámetros: doctor + fecha (yyyy-MM-dd, opcional). */
    public static final class Params {
        public final String doctorId;
        public final String date;

        public Params(@NonNull String doctorId, String date) {
            this.doctorId = doctorId;
            this.date = date;
        }
    }

    private final IDoctorRepository repository;

    public GetAvailableSlotsUseCase(IDoctorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(Params params, @NonNull Callback<List<TimeSlot>> callback) {
        if (params == null || params.doctorId == null || params.doctorId.trim().isEmpty()) {
            callback.onError("Selecciona un doctor para ver los horarios");
            return;
        }
        repository.getAvailableSlots(params.doctorId, new RepositoryCallback<List<TimeSlot>>() {
            @Override
            public void onSuccess(List<TimeSlot> data) {
                if (data == null) {
                    callback.onError("No hay horarios disponibles");
                    return;
                }
                List<TimeSlot> result = new ArrayList<>();
                for (TimeSlot slot : data) {
                    if (params.date != null && !params.date.equals(slot.getDate())) {
                        continue;
                    }
                    result.add(slot);
                }
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
