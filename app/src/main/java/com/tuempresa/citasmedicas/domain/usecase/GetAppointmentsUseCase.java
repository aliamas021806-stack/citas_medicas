package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.model.Appointment;

import java.util.List;

/**
 * Caso de uso: observa las citas guardadas.
 * <p>
 * Particularidad frente al resto de casos de uso: aquí NO se usa el contrato
 * {@link Callback}, porque la fuente entrega un {@link LiveData} que ya es
 * observable y reactivo. Envolverlo en un callback puntual obligaría a consultar
 * el almacenamiento cada vez que cambia algo, perdiendo justo la ventaja del
 * enfoque <i>offline first</i>: que la UI se actualice sola al insertar o cancelar.
 * <p>
 * <b>Importante:</b> devuelve {@link Appointment} (modelo de dominio) y no
 * {@code AppointmentEntity}. Así el dominio y la presentación no conocen la tabla
 * de SQLite, y la misma firma seguirá siendo válida cuando las citas vengan de
 * Firestore.
 * <p>
 * Equivalencia con Kotlin: sería {@code fun invoke(): Flow<List<Appointment>>}.
 * En Java el equivalente nativo de {@code Flow} es {@code LiveData}.
 */
public class GetAppointmentsUseCase {

    private final IAppointmentRepository repository;

    public GetAppointmentsUseCase(@NonNull IAppointmentRepository repository) {
        this.repository = repository;
    }

    /** Flujo observable de todas las citas, ordenadas por fecha y hora. */
    @NonNull
    public LiveData<List<Appointment>> execute() {
        return repository.observeAll();
    }
}
