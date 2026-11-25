package com.tuempresa.citasmedicas.domain.usecase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.domain.UseCase;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;

/**
 * Caso de uso: registrar un paciente nuevo.
 *
 * La validación de los campos (nombre, correo, contraseña y edad) vive en
 * {@code AuthValidator}, dentro del propio repositorio, para que la regla se
 * aplique igual venga de donde venga la petición. Aquí sólo se comprueba que
 * llegue una petición y que el resultado sea utilizable.
 */
public class RegisterUseCase extends UseCase<AuthRequest, User> {

    private final IAuthRepository repository;

    public RegisterUseCase(IAuthRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(AuthRequest params, @NonNull Callback<User> callback) {
        if (params == null) {
            callback.onError("No hay datos para crear la cuenta");
            return;
        }
        repository.register(params, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User data) {
                if (data == null) {
                    callback.onError("No pudimos crear la cuenta. Inténtalo de nuevo");
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
