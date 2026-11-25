package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.data.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;

/**
 * Caso de uso: cancela una cita existente.
 * <p>
 * Regla de negocio: una cita ya cancelada no se vuelve a cancelar (evita
 * escrituras innecesarias y mensajes de confirmación engañosos).
 */
public class CancelAppointmentUseCase extends UseCase<Long, Void> {

    private final AppointmentRepository repository;

    public CancelAppointmentUseCase(@NonNull AppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(@NonNull Long appointmentId, @NonNull Callback<Void> callback) {
        if (appointmentId == null || appointmentId <= 0L) {
            callback.onError("La cita indicada no es válida");
            return;
        }
        repository.cancel(appointmentId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                callback.onSuccess(null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
