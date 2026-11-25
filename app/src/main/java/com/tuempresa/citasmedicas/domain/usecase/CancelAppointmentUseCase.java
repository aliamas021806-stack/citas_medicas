package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;

/**
 * Caso de uso: cancela una cita existente.
 * <p>
 * Regla de negocio: una cita ya cancelada no se vuelve a cancelar (evita
 * escrituras innecesarias y mensajes de confirmación engañosos).
 */
public class CancelAppointmentUseCase extends UseCase<String, Void> {

    private final IAppointmentRepository repository;

    public CancelAppointmentUseCase(@NonNull IAppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(@NonNull String appointmentId, @NonNull Callback<Void> callback) {
        if (appointmentId == null || appointmentId.trim().isEmpty()) {
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
