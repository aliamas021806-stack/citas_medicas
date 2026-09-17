package com.tuempresa.citasmedicas.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel que expone especialidades, doctores y horarios disponibles.
 */
public class DoctorViewModel extends ViewModel {

    private final DoctorRepository repository = new DoctorRepository();

    private final MutableLiveData<Resource<List<Specialty>>> specialties = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Doctor>>> doctors = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<TimeSlot>>> slots = new MutableLiveData<>();

    private List<Doctor> allDoctors = new ArrayList<>();

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
        repository.getSpecialties(new RepositoryCallback<List<Specialty>>() {
            @Override
            public void onSuccess(List<Specialty> data) {
                specialties.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message) {
                specialties.setValue(Resource.error(message));
            }
        });
    }

    public void loadDoctors() {
        doctors.setValue(Resource.loading());
        repository.getDoctors(new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                allDoctors = data != null ? data : new ArrayList<>();
                doctors.setValue(Resource.success(allDoctors));
            }

            @Override
            public void onError(String message) {
                doctors.setValue(Resource.error(message));
            }
        });
    }

    public void loadDoctorsBySpecialty(String specialtyId) {
        doctors.setValue(Resource.loading());
        repository.getDoctorsBySpecialty(specialtyId, new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                doctors.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message) {
                doctors.setValue(Resource.error(message));
            }
        });
    }

    public void filterDoctors(String query) {
        if (query == null || query.trim().isEmpty()) {
            doctors.setValue(Resource.success(allDoctors));
            return;
        }
        String q = query.toLowerCase().trim();
        List<Doctor> filtered = new ArrayList<>();
        for (Doctor d : allDoctors) {
            if (d.getFullName().toLowerCase().contains(q)
                    || d.getSpecialtyName().toLowerCase().contains(q)) {
                filtered.add(d);
            }
        }
        doctors.setValue(Resource.success(filtered));
    }

    public void loadSlots(String doctorId) {
        slots.setValue(Resource.loading());
        repository.getAvailableSlots(doctorId, new RepositoryCallback<List<TimeSlot>>() {
            @Override
            public void onSuccess(List<TimeSlot> data) {
                slots.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message) {
                slots.setValue(Resource.error(message));
            }
        });
    }
}
