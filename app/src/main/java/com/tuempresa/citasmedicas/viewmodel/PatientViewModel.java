package com.tuempresa.citasmedicas.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetPatientUseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetPatientsUseCase;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.List;

/**
 * ViewModel del PACIENTE. Carga la información del paciente (incluida su EDAD)
 * desde la API mock consumiendo el caso de uso {@link GetPatientUseCase}.
 */
public class PatientViewModel extends AndroidViewModel {

    private final GetPatientUseCase getPatientUseCase;
    private final GetPatientsUseCase getPatientsUseCase;

    /**
     * Se extiende {@link AndroidViewModel} para poder pasar el {@code Context} al
     * repositorio y que las peticiones a Firestore viajen con el {@code idToken} del
     * usuario. Con {@code ViewModel} no había forma de obtenerlo, así que la lectura
     * dependía de que las reglas fuesen públicas. La UI no cambia:
     * {@code ViewModelProvider} construye {@code AndroidViewModel} por sí solo.
     */
    public PatientViewModel(@NonNull android.app.Application application) {
        super(application);
        com.tuempresa.citasmedicas.domain.repository.IPatientRepository repository =
                RepositoryProvider.patients(application.getApplicationContext());
        this.getPatientUseCase = new GetPatientUseCase(repository);
        this.getPatientsUseCase = new GetPatientsUseCase(repository);
    }

    private final MutableLiveData<Resource<Patient>> patient = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Patient>>> patients = new MutableLiveData<>();

    public LiveData<Resource<Patient>> getPatient() {
        return patient;
    }

    public LiveData<Resource<List<Patient>>> getPatients() {
        return patients;
    }

    public void loadPatient(String patientId) {
        patient.setValue(Resource.loading());
        getPatientUseCase.execute(patientId, new UseCase.Callback<Patient>() {
            @Override
            public void onSuccess(Patient result) {
                patient.setValue(Resource.success(result));
            }

            @Override
            public void onError(String message) {
                patient.setValue(Resource.error(message));
            }
        });
    }

    public void loadPatients() {
        patients.setValue(Resource.loading());
        getPatientsUseCase.execute(new UseCase.Callback<List<Patient>>() {
                    @Override
                    public void onSuccess(List<Patient> result) {
                        patients.setValue(Resource.success(result));
                    }

                    @Override
                    public void onError(String message) {
                        patients.setValue(Resource.error(message));
                    }
                });
    }
}
