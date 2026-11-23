package com.tuempresa.citasmedicas.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.AuthValidator;

import java.util.Locale;

/**
 * Repositorio de autenticación (SIMULADO).
 * <p>
 * No hay backend: valida credenciales con {@link AuthValidator} y responde tras
 * un pequeño delay asíncrono (Handler) para reproducir la latencia de una API.
 * Conserva la EDAD del paciente en la sesión.
 */
public class AuthRepository {

    private static final long FAKE_DELAY_MS = 900L;
    /** Edad por defecto "generada" para el login cuando no viene en la request. */
    private static final int DEFAULT_AGE = 30;
    /** Teléfono simulado que devuelve el backend ficticio. */
    private static final String FAKE_PHONE = "+34 600 123 456";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Login simulado: acepta email válido + contraseña de 4+ caracteres. */
    public void login(AuthRequest request, RepositoryCallback<User> callback) {
        mainHandler.postDelayed(() -> {
            String error = AuthValidator.validateLogin(
                    request.getEmail(), request.getPassword());
            if (error != null) {
                callback.onError(error);
                return;
            }
            String name = deriveNameFromEmail(request.getEmail());
            int age = request.getAge() > 0 ? request.getAge() : DEFAULT_AGE;
            User user = new User("user_01", name, request.getEmail(), FAKE_PHONE, age);
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    /** Registro simulado: valida datos y devuelve el usuario creado (con edad). */
    public void register(AuthRequest request, RepositoryCallback<User> callback) {
        mainHandler.postDelayed(() -> {
            String error = AuthValidator.validateRegister(
                    request.getFullName(), request.getEmail(),
                    request.getPassword(), request.getAge());
            if (error != null) {
                callback.onError(error);
                return;
            }
            User user = new User("user_01", request.getFullName().trim(),
                    request.getEmail(), FAKE_PHONE, request.getAge());
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    private String deriveNameFromEmail(String email) {
        String local = email.split("@")[0];
        local = local.replace('.', ' ').replace('_', ' ').trim();
        if (local.isEmpty()) {
            return "Paciente";
        }
        Locale locale = Locale.getDefault();
        String[] parts = local.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.substring(1).toLowerCase(locale))
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
