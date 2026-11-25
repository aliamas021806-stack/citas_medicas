package com.tuempresa.citasmedicas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.CancelAppointmentUseCase;
import com.tuempresa.citasmedicas.domain.usecase.CreateAppointmentUseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetAppointmentsUseCase;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.List;

/**
 * ViewModel de citas médicas.
 * <p>
 * Consume los CASOS DE USO de {@code domain} (no el repositorio directamente):
 * <ul>
 *   <li>{@link GetAppointmentsUseCase} expone Room como flujo observable.</li>
 *   <li>{@link CreateAppointmentUseCase} valida la cita antes de guardarla.</li>
 *   <li>{@link CancelAppointmentUseCase} gestiona la cancelación.</li>
 * </ul>
 * Así la regla de negocio vive en dominio y este ViewModel solo traduce a
 * {@link Resource} para la UI.
 */
public class AppointmentViewModel extends AndroidViewModel {

    private final GetAppointmentsUseCase getAppointmentsUseCase;
    private final CreateAppointmentUseCase createAppointmentUseCase;
    private final CancelAppointmentUseCase cancelAppointmentUseCase;

    private final LiveData<List<AppointmentEntity>> appointments;
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    /**
     * Evento de un solo disparo que indica que la cita se creó correctamente.
     * Se usa Boolean (no Void) porque un LiveData<Void> emite siempre {@code null}
     * y un observador con {@code if (value != null)} nunca se activaría.
     */
    private final MutableLiveData<Boolean> navigateBack = new MutableLiveData<>();

    public AppointmentViewModel(@NonNull Application application) {
        super(application);
        AppointmentRepository repository = new AppointmentRepository(application);
        this.getAppointmentsUseCase = new GetAppointmentsUseCase(repository);
        this.createAppointmentUseCase = new CreateAppointmentUseCase(repository);
        this.cancelAppointmentUseCase = new CancelAppointmentUseCase(repository);
        this.appointments = getAppointmentsUseCase.execute();
    }

    public LiveData<List<AppointmentEntity>> getAppointments() {
        return appointments;
    }

    public LiveData<Resource<Void>> getActionState() {
        return actionState;
    }

    public LiveData<Boolean> getNavigateBack() {
        return navigateBack;
    }

    public void createAppointment(Appointment appointment) {
        actionState.setValue(Resource.loading());
        createAppointmentUseCase.execute(appointment, new UseCase.Callback<Long>() {
            @Override
            public void onSuccess(Long data) {
                actionState.setValue(Resource.success(null));
                // true = éxito de creación (evento observable por la Activity)
                navigateBack.setValue(Boolean.TRUE);
            }

            @Override
            public void onError(String message) {
                actionState.setValue(Resource.error(message));
            }
        });
    }

    public void cancelAppointment(long appointmentId) {
        actionState.setValue(Resource.loading());
        cancelAppointmentUseCase.execute(appointmentId, new UseCase.Callback<Void>() {
            @Override
            public void onSuccess(Void data) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(String message) {
                actionState.setValue(Resource.error(message));
            }
        });
    }

    public void clearActionState() {
        actionState.setValue(null);
    }

    /** Consume el evento de navegación para evitar que se repita (p. ej. al rotar). */
    public void clearNavigateBack() {
        navigateBack.setValue(Boolean.FALSE);
    }
}
