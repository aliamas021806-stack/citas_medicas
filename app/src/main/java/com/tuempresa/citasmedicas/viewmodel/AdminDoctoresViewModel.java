package com.tuempresa.citasmedicas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.data.firebase.FirestoreAdminRepository;
import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.util.Resource;

import java.util.List;

/**
 * ViewModel del <b>alta de médicos</b> (sólo administrador).
 * <p>
 * Dos operaciones: listar los médicos que ya existen (para ver quién está vinculado
 * y quién todavía no ha entrado) y crear uno nuevo con su correo.
 */
public class AdminDoctoresViewModel extends AndroidViewModel {

    private final FirestoreAdminRepository admin;
    private final IDoctorRepository catalogo;

    private final MutableLiveData<Resource<List<Doctor>>> medicos = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> alta = new MutableLiveData<>();

    public AdminDoctoresViewModel(@NonNull Application application) {
        super(application);
        this.admin = new FirestoreAdminRepository(application.getApplicationContext());
        this.catalogo = RepositoryProvider.doctors(application.getApplicationContext());
    }

    public LiveData<Resource<List<Doctor>>> getMedicos() {
        return medicos;
    }

    /** Evento de un solo disparo: el {@code String} es el correo del médico creado. */
    public LiveData<Resource<String>> getAlta() {
        return alta;
    }

    /** Carga la lista de médicos registrados. */
    public void cargarMedicos() {
        medicos.setValue(Resource.loading());
        catalogo.getDoctors(new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                medicos.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message) {
                medicos.setValue(Resource.error(message));
            }
        });
    }

    /**
     * Registra un médico.
     *
     * @param email correo con el que el médico entrará a la app
     */
    public void registrarMedico(@NonNull String fullName, @NonNull String email,
                                @NonNull String specialtyName, @NonNull String hospital,
                                int age, int yearsExperience, double fee, @NonNull String bio) {
        alta.setValue(Resource.loading());

        Doctor doctor = new Doctor();
        doctor.setFullName(fullName.trim());
        doctor.setSpecialtyName(specialtyName.trim());
        // El id de especialidad se deriva del nombre: el catálogo de especialidades ya
        // existente usa ids cortos y así la ficha aparece filtrada correctamente.
        doctor.setSpecialtyId(especialidadIdDesde(specialtyName));
        doctor.setHospital(hospital.trim());
        doctor.setAge(age);
        doctor.setYearsExperience(yearsExperience);
        doctor.setConsultationFee(fee);
        doctor.setBio(bio != null ? bio.trim() : "");
        doctor.setRating(0d);
        doctor.setPhotoUrl("");

        admin.crearMedico(doctor, email.trim(), new FirestoreAdminRepository.AltaCallback() {
            @Override
            public void onCreado(@NonNull String doctorId) {
                alta.setValue(Resource.success(email.trim()));
                // Se recarga para que la ficha nueva aparezca con su estado.
                cargarMedicos();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                alta.setValue(Resource.error(mensaje));
            }
        });
    }

    /** Id de especialidad normalizado a partir del nombre escrito. */
    @NonNull
    public static String especialidadIdDesde(@NonNull String nombre) {
        String limpio = nombre.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        while (limpio.startsWith("_")) {
            limpio = limpio.substring(1);
        }
        while (limpio.endsWith("_")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio.isEmpty() ? "general" : limpio;
    }

    public void limpiarAlta() {
        alta.setValue(null);
    }
}
