package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.data.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.Appointment;

/**
 * Caso de uso: agenda una nueva cita validando las reglas de negocio antes de
 * escribir en Room.
 * <p>
 * Validaciones aplicadas (no existían antes en la vista):
 * <ul>
 *   <li>Debe haber doctor y fecha/hora.</li>
 *   <li>La fecha debe tener formato {@code yyyy-MM-dd} y la hora {@code HH:mm}.</li>
 *   <li>La fecha no puede ser anterior a hoy (no se agenda en el pasado).</li>
 *   <li>La edad del paciente, si viene informada, debe ser humana (0..120).</li>
 * </ul>
 * Se validan en dominio para que la regla no dependa de qué pantalla llame.
 */
public class CreateAppointmentUseCase extends UseCase<Appointment, Long> {

    private final AppointmentRepository repository;

    public CreateAppointmentUseCase(@NonNull AppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(@Nullable Appointment appointment,
                        @NonNull Callback<Long> callback) {
        String error = validate(appointment);
        if (error != null) {
            callback.onError(error);
            return;
        }
        repository.create(appointment, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long data) {
                callback.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Valida la cita.
     *
     * @return {@code null} si es válida; en caso contrario, el motivo.
     */
    @Nullable
    /**
     * Regla de validación de una cita, expuesta para que la vista pueda avisar
     * antes de enviar y para poder probarla sin tocar Room.
     *
     * @return el mensaje de error, o {@code null} si la cita es válida.
     */
    public static String validate(@Nullable Appointment appointment) {
        if (appointment == null) {
            return "No hay datos de la cita";
        }
        if (isBlank(appointment.getDoctorId())) {
            return "Selecciona un doctor para agendar la cita";
        }
        if (isBlank(appointment.getDate())) {
            return "Selecciona una fecha para la cita";
        }
        if (!appointment.getDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "La fecha de la cita no es válida";
        }
        if (isBlank(appointment.getTime()) || !appointment.getTime().matches("\\d{2}:\\d{2}")) {
            return "Selecciona un horario para la cita";
        }
        if (isPast(appointment.getDate())) {
            return "No puedes agendar una cita en una fecha pasada";
        }
        if (appointment.getPatientAge() < 0 || appointment.getPatientAge() > 120) {
            return "La edad del paciente no es válida";
        }
        return null;
    }

    /**
     * Compara la fecha (yyyy-MM-dd) con hoy.
     * <p>
     * Se comparan cadenas en orden lexicográfico en lugar de usar
     * {@code java.time.LocalDate}: con el formato ISO {@code yyyy-MM-dd} el orden
     * alfabético ES el orden cronológico, así que el resultado es idéntico y la
     * capa de dominio deja de depender de una API que en Android necesita
     * desugaring. El formato ya viene validado por el llamador.
     */
    private static boolean isPast(@NonNull String date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.US);
        String today = sdf.format(new java.util.Date());
        return date.compareTo(today) < 0;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
