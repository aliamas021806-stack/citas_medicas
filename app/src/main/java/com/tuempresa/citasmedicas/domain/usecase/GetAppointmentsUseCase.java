package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.domain.UseCase;

import java.util.List;

/**
 * Caso de uso: observa las citas guardadas en Room (SQLite).
 * <p>
 * Particularidad frente al resto de casos de uso: aquí NO se usa el contrato
 * {@link Callback}, porque Room entrega un {@link androidx.lifecycle.LiveData}
 * que ya es observable y reactivo. Envolverlo en un callback puntual obligaría a
 * consultar la base de datos cada vez que cambia algo, perdiendo justo la ventaja
 * de "offline first": que la UI se actualiza sola al insertar o cancelar.
 * <p>
 * Equivalencia con Kotlin: sería {@code fun invoke(): Flow<List<Appointment>>}.
 * En Java el equivalente nativo de {@code Flow} es {@code LiveData}.
 */
public class GetAppointmentsUseCase {

    private final AppointmentRepository repository;

    public GetAppointmentsUseCase(@NonNull AppointmentRepository repository) {
        this.repository = repository;
    }

    /** Flujo observable de todas las citas, ordenadas por fecha y hora. */
    @NonNull
    public androidx.lifecycle.LiveData<List<AppointmentEntity>> execute() {
        return repository.observeAll();
    }
}
