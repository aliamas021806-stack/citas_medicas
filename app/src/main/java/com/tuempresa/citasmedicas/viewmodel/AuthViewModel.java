package com.tuempresa.citasmedicas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.LoginUseCase;
import com.tuempresa.citasmedicas.domain.usecase.RegisterUseCase;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.model.UserRole;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;

/**
 * ViewModel de autenticación (MVVM).
 *
 * Consume casos de uso, igual que el resto de ViewModels: no habla con el
 * repositorio ni sabe si el usuario viene de la red o de una caché. Sólo traduce
 * el resultado a {@link Resource} para que la vista pinte estado de carga, éxito
 * o error.
 */
public class AuthViewModel extends AndroidViewModel {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;
    private final MutableLiveData<Resource<User>> authState = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        // La implementación concreta la decide RepositoryProvider (raíz de composición).
        IAuthRepository repository = RepositoryProvider.auth(application);

        this.loginUseCase = new LoginUseCase(repository);
        this.registerUseCase = new RegisterUseCase(repository);
    }

    public LiveData<Resource<User>> getAuthState() {
        return authState;
    }

    public void login(String email, String password) {
        authState.setValue(Resource.loading());
        loginUseCase.execute(new AuthRequest(email, password), new UseCase.Callback<User>() {
            @Override
            public void onSuccess(User result) {
                authState.setValue(Resource.success(result));
            }

            @Override
            public void onError(String message) {
                authState.setValue(Resource.error(message));
            }
        });
    }

    public void register(String fullName, String email, String password, int age) {
        register(fullName, null, email, password, age, null, null, null, null);
    }

    /**
     * Registro con el expediente del paciente.
     * <p>
     * El teléfono, el grupo sanguíneo, la aseguradora y las alergias los escribe el
     * usuario; así el perfil muestra sus datos y no los aleatorios del mock.
     */
    public void register(String fullName, String lastName, String email, String password,
                         int age, String phone, String bloodType, String insurance,
                         String allergies) {
        authState.setValue(Resource.loading());
        registerUseCase.execute(
                new AuthRequest(fullName, lastName, email, password, age, phone,
                        bloodType, insurance, allergies),
                new UseCase.Callback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        authState.setValue(Resource.success(result));
                    }

                    @Override
                    public void onError(String message) {
                        authState.setValue(Resource.error(message));
                    }
                });
    }

    public void resetState() {
        authState.setValue(null);
    }

    // ─────────────────── Registro de médico ───────────────────

    /**
     * Resultado del intento de vincular la cuenta recién creada con una ficha de médico.
     * <p>
     * <b>Por qué hace falta.</b> El registro siempre crea la cuenta como paciente
     * ({@code role = "patient"}): es la única forma de que nadie pueda autoproclamarse
     * médico. Pero entonces, ¿cómo entra un médico? Su ficha en {@code doctors} la crea
     * un administrador con su correo, y al registrarse él mismo esa ficha se vincula.
     * Este es el paso que intenta esa vinculación justo después del registro, para que
     * no tenga que cerrar sesión y volver a entrar.
     */
    public interface VinculacionCallback {
        /**
         * @param vinculado {@code true} si encontró (o creó) su ficha y su rol es médico
         * @param doctorId  id de la ficha, o {@code null} si no se vinculó
         */
        void onResultado(boolean vinculado, @Nullable String doctorId);
    }

    /**
     * Intenta vincular la cuenta recién registrada con su ficha de médico.
     * <p>
     * Se ejecuta sólo si el usuario pulsó «Médico» en el registro. El servidor decide:
     * las reglas de Firestore únicamente permiten escribir el {@code userId} de una
     * ficha si el correo de la ficha coincide con el del token y nadie la había
     * reclamado, así que este intento no puede usarse para apropiarse de la ficha de
     * otro médico.
     * <p>
     * Si no hay ninguna ficha con ese correo, se devuelve {@code vinculado = false}: la
     * cuenta sigue siendo válida como paciente y la interfaz explica qué falta.
     */
    public void vincularMedicoRecienRegistrado(@NonNull User user,
                                               @NonNull final VinculacionCallback callback) {
        if (!com.tuempresa.citasmedicas.data.firebase.FirebaseConfig.isEnabled()) {
            callback.onResultado(false, null);
            return;
        }
        final String uid = user.getId();
        final String email = user.getEmail();
        if (uid == null || uid.trim().isEmpty()) {
            callback.onResultado(false, null);
            return;
        }

        final com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository roles =
                new com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository(getApplication());

        // No se crea nada aquí: el alta de la ficha es competencia del administrador
        // (ver firestore.rules). Sólo se comprueba si ya existe una ficha con su correo.
        roles.cargarRol(uid, email, new com.tuempresa.citasmedicas.data.firebase
                .FirestoreUserRepository.RoleCallback() {
            @Override
            public void onRole(@NonNull UserRole role, @Nullable String doctorId) {
                boolean esMedico = role == UserRole.DOCTOR
                        && doctorId != null && !doctorId.trim().isEmpty();
                if (esMedico) {
                    SessionManager.getInstance(getApplication()).updateRole(role, doctorId);
                }
                // Si no hay ficha con su correo, NO se crea una: sólo un administrador
                // puede darla de alta. La cuenta sigue siendo válida como paciente y la
                // pantalla explica qué falta.
                callback.onResultado(esMedico, esMedico ? doctorId : null);
            }

            @Override
            public void onFallback(@NonNull String motivo) {
                callback.onResultado(false, null);
            }
        });
    }
}
