package com.tuempresa.citasmedicas.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.domain.usecase.LoginUseCase;
import com.tuempresa.citasmedicas.domain.usecase.RegisterUseCase;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.Resource;

/**
 * ViewModel de autenticación (MVVM).
 *
 * Consume casos de uso, igual que el resto de ViewModels: no habla con el
 * repositorio ni sabe si el usuario viene de la red o de una caché. Sólo traduce
 * el resultado a {@link Resource} para que la vista pinte estado de carga, éxito
 * o error.
 */
public class AuthViewModel extends ViewModel {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;
    private final MutableLiveData<Resource<User>> authState = new MutableLiveData<>();

    public AuthViewModel() {
        AuthRepository repository = new AuthRepository();
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
        authState.setValue(Resource.loading());
        registerUseCase.execute(new AuthRequest(fullName, email, password, age),
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
}
