package com.tuempresa.citasmedicas.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tuempresa.citasmedicas.data.repository.PatientRepository;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.GetPatientUseCase;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.List;

/**
 * ViewModel del PACIENTE. Carga la información del paciente (incluida su EDAD)
 * desde la API mock consumiendo el caso de uso {@link GetPatientUseCase}.
 */
public class PatientViewModel extends ViewModel {

    private final GetPatientUseCase getPatientUseCase = new GetPatientUseCase(new PatientRepository());

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
        new com.tuempresa.citasmedicas.domain.usecase.GetPatientsUseCase(new PatientRepository())
                .execute(new UseCase.Callback<List<Patient>>() {
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
