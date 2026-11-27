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
    /**
     * Catálogo de especialidades para el desplegable del alta.
     * <p>
     * Se ofrece la lista real (de Firestore, o el respaldo del mock) en lugar de dejar
     * escribir texto libre: así el {@code specialtyId} de la ficha siempre existe y el
     * médico aparece al filtrar por su especialidad.
     */
    private final MutableLiveData<List<String>> especialidades = new MutableLiveData<>();

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

    /** Nombres de especialidad para el desplegable del alta. */
    public LiveData<List<String>> getEspecialidades() {
        return especialidades;
    }

    /**
     * Carga el catálogo de especialidades.
     * <p>
     * Si Firestore no responde, la lista se queda vacía y el desplegable permite
     * escribir (MaterialAutoCompleteTextView no bloquea la entrada): así el alta no se
     * queda bloqueada por un fallo de red, que es lo que importaba.
     */
    public void cargarEspecialidades() {
        catalogo.getSpecialties(new RepositoryCallback<List<com.tuempresa.citasmedicas
                .model.Specialty>>() {
            @Override
            public void onSuccess(List<com.tuempresa.citasmedicas.model.Specialty> data) {
                java.util.List<String> nombres = new java.util.ArrayList<>();
                if (data != null) {
                    for (com.tuempresa.citasmedicas.model.Specialty s : data) {
                        if (s != null && s.getName() != null
                                && !s.getName().trim().isEmpty()) {
                            nombres.add(s.getName().trim());
                        }
                    }
                }
                especialidades.setValue(nombres);
            }

            @Override
            public void onError(String message) {
                // Sin catálogo no se rompe el alta: el campo sigue admitiendo texto.
                especialidades.setValue(new java.util.ArrayList<>());
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

    /**
     * Id de especialidad normalizado a partir del nombre escrito.
     * <p>
     * <b>Los acentos se eliminan de verdad, no se sustituyen.</b> Si se aplicara sólo
     * {@code [^a-z0-9] -> _}, "Cardiología" daría {@code cardiolog_a}, con un guion
     * bajo en medio que rompe el id y no coincide con el de las especialidades ya
     * existentes. {@link java.text.Normalizer} separa la letra de su tilde (NFD) y
     * después se descartan las marcas diacríticas, así que "Cardiología" queda
     * {@code cardiologia} y "Pediatría" queda {@code pediatria}.
     */
    @NonNull
    public static String especialidadIdDesde(@NonNull String nombre) {
        String sinTildes = java.text.Normalizer
                .normalize(nombre.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String limpio = sinTildes.toLowerCase(java.util.Locale.ROOT)
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
