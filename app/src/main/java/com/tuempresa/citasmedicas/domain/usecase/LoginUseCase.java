package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;

/**
 * Caso de uso: iniciar sesión.
 *
 * Vive en {@code domain} y no conoce Retrofit, Room ni Android: sólo pide al
 * repositorio el usuario y normaliza el resultado. El ViewModel se limita a
 * exponer el estado, así que la capa de presentación no depende de cómo se
 * obtiene el dato.
 */
public class LoginUseCase extends UseCase<AuthRequest, User> {

    private final IAuthRepository repository;

    public LoginUseCase(IAuthRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(AuthRequest params, @NonNull Callback<User> callback) {
        if (params == null || params.getEmail() == null || params.getEmail().trim().isEmpty()) {
            callback.onError("Ingresa tu correo electrónico");
            return;
        }
        repository.login(params, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User data) {
                if (data == null) {
                    callback.onError("No pudimos iniciar sesión. Inténtalo de nuevo");
                    return;
                }
                callback.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
