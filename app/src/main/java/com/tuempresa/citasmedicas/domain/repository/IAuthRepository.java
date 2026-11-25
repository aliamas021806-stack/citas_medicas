package com.tuempresa.citasmedicas.domain.repository;

import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;

/**
 * Contrato del repositorio de autenticación.
 * <p>
 * Hoy lo implementa {@code AuthRepository} (validación local simulada). Cuando se
 * conecte Firebase, bastará con añadir {@code FirebaseAuthRepository} implementando
 * esta misma interfaz: el {@code AuthViewModel} y los casos de uso
 * {@code LoginUseCase} / {@code RegisterUseCase} no cambian ni una línea.
 * <p>
 * Es el punto de sustitución más limpio de todo el proyecto, porque la autenticación
 * no necesita base de datos local ni caché: es una operación de red puntual.
 */
public interface IAuthRepository {

    /** Inicia sesión con email + contraseña. */
    void login(AuthRequest request, RepositoryCallback<User> callback);

    /** Registra un usuario nuevo con nombre, email, contraseña y edad. */
    void register(AuthRequest request, RepositoryCallback<User> callback);
}
