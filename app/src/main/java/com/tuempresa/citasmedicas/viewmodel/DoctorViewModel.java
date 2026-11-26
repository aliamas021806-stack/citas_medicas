package com.tuempresa.citasmedicas.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.AndroidViewModel;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetAvailableSlotsUseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetDoctorsUseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetSpecialtiesUseCase;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel que expone especialidades, doctores y horarios disponibles.
 * <p>
 * Consume los CASOS DE USO de la capa {@code domain}, no los repositorios: la
 * regla de negocio (filtrado por texto, descarte de franjas ocupadas) vive en
 * dominio y aquí solo se traduce el resultado a {@link Resource} para la UI.
 */
public class DoctorViewModel extends AndroidViewModel {

    /**
     * Implementación resuelta por la raíz de composición (Firestore con respaldo local).
     * <p>
     * Se extiende {@link AndroidViewModel} para poder pasar el {@code Context} al
     * repositorio: es lo que permite enviar el {@code idToken} del usuario en cada
     * petición a Firestore. Con {@code ViewModel} a secas no había forma de obtenerlo,
     * y por eso las peticiones viajaban sin credencial, dependiendo de que las reglas
     * fuesen públicas. La UI no cambia: {@code ViewModelProvider} construye
     * {@code AndroidViewModel} por sí solo.
     */
    private final com.tuempresa.citasmedicas.domain.repository.IDoctorRepository repository;

    private final GetSpecialtiesUseCase getSpecialtiesUseCase;
    private final GetDoctorsUseCase getDoctorsUseCase;
    private final GetAvailableSlotsUseCase getAvailableSlotsUseCase;

    private final MutableLiveData<Resource<List<Specialty>>> specialties = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Doctor>>> doctors = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<TimeSlot>>> slots = new MutableLiveData<>();

    private List<Doctor> allDoctors = new ArrayList<>();
    /** Último filtro aplicado, para poder re-filtrar sin volver a la red. */
    private String activeQuery = "";

    public DoctorViewModel(@NonNull android.app.Application application) {
        super(application);
        this.repository = RepositoryProvider.doctors(application.getApplicationContext());
        this.getSpecialtiesUseCase = new GetSpecialtiesUseCase(repository);
        this.getDoctorsUseCase = new GetDoctorsUseCase(repository);
        this.getAvailableSlotsUseCase = new GetAvailableSlotsUseCase(repository);
    }

    public LiveData<Resource<List<Specialty>>> getSpecialties() {
        return specialties;
    }

    public LiveData<Resource<List<Doctor>>> getDoctors() {
        return doctors;
    }

    public LiveData<Resource<List<TimeSlot>>> getSlots() {
        return slots;
    }

    public void loadHome() {
        loadSpecialties();
        loadDoctors();
    }

    public void loadSpecialties() {
        specialties.setValue(Resource.loading());
        getSpecialtiesUseCase.execute(new UseCase.Callback<List<Specialty>>() {
            @Override
            public void onSuccess(List<Specialty> result) {
                specialties.setValue(Resource.success(result));
            }

            @Override
            public void onError(String message) {
                specialties.setValue(Resource.error(message));
            }
        });
    }

    public void loadDoctors() {
        loadDoctorsBySpecialty(null);
    }

    public void loadDoctorsBySpecialty(String specialtyId) {
        doctors.setValue(Resource.loading());
        activeQuery = "";
        getDoctorsUseCase.execute(new GetDoctorsUseCase.Params(specialtyId, null),
                new UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        allDoctors = result != null ? result : new ArrayList<>();
                        doctors.setValue(Resource.success(allDoctors));
                    }

                    @Override
                    public void onError(String message) {
                        doctors.setValue(Resource.error(message));
                    }
                });
    }

    /**
     * Filtra en memoria usando el caso de uso (misma regla de negocio que la red),
     * evitando una petición por cada pulsación del buscador.
     */
    public void filterDoctors(String query) {
        activeQuery = query != null ? query : "";
        if (activeQuery.trim().isEmpty()) {
            doctors.setValue(Resource.success(allDoctors));
            return;
        }
        getDoctorsUseCase.executeWithLocalData(allDoctors, activeQuery,
                new UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        doctors.setValue(Resource.success(result));
                    }

                    @Override
                    public void onError(String message) {
                        doctors.setValue(Resource.error(message));
                    }
                });
    }

    /** Reaplica el filtro activo (p. ej. tras recargar con SwipeRefresh). */
    public void refreshFilter() {
        if (!activeQuery.trim().isEmpty()) {
            filterDoctors(activeQuery);
        }
    }

    public void loadSlots(String doctorId) {
        slots.setValue(Resource.loading());
        getAvailableSlotsUseCase.execute(
                new GetAvailableSlotsUseCase.Params(doctorId, null),
                new UseCase.Callback<List<TimeSlot>>() {
                    @Override
                    public void onSuccess(List<TimeSlot> result) {
                        slots.setValue(Resource.success(result));
                    }

                    @Override
                    public void onError(String message) {
                        slots.setValue(Resource.error(message));
                    }
                });
    }

    /** Filtra por fecha usando el caso de uso sobre los horarios ya cargados. */
    public void filterSlotsByDate(List<TimeSlot> source, String date) {
        if (source == null) {
            return;
        }
        List<TimeSlot> result = new ArrayList<>();
        for (TimeSlot slot : source) {
            if (date == null || date.equals(slot.getDate())) {
                result.add(slot);
            }
        }
        slots.setValue(Resource.success(result));
    }

    /** Normaliza texto para comparaciones (expuesto para pruebas). */
    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.getDefault()).trim();
    }
}
