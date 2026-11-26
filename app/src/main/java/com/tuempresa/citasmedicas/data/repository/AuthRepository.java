package com.tuempresa.citasmedicas.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
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
public class AuthRepository implements com.tuempresa.citasmedicas.domain.repository.IAuthRepository {

    private static final long FAKE_DELAY_MS = 900L;
    /** Edad por defecto "generada" para el login cuando no viene en la request. */
    private static final int DEFAULT_AGE = 30;

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
            // El id se deriva del correo para que cada cuenta tenga su propio espacio
            // de citas; antes era siempre "user_01" y todos compartían las mismas.
            User user = new User(idDe(request.getEmail()), name, request.getEmail(), "", age);
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    /**
     * Registro simulado.
     * <p>
     * Devuelve el usuario con los datos del expediente que escribió el paciente; ya
     * no inventa un teléfono.
     */
    public void register(AuthRequest request, RepositoryCallback<User> callback) {
        mainHandler.postDelayed(() -> {
            String error = AuthValidator.validateRegister(
                    request.getFullName(), request.getEmail(),
                    request.getPassword(), request.getAge());
            if (error != null) {
                callback.onError(error);
                return;
            }
            User user = new User(idDe(request.getEmail()),
                    request.getFullName() != null ? request.getFullName().trim() : "",
                    request.getLastName(), request.getEmail(),
                    request.getPhone() != null ? request.getPhone().trim() : "",
                    request.getAge(), request.getBloodType(), request.getInsurance(),
                    request.getAllergies());
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    /** Id estable por cuenta, derivado del correo. */
    @NonNull
    private String idDe(@Nullable String email) {
        if (email == null || email.trim().isEmpty()) {
            return "user_local";
        }
        String limpio = email.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return "user_" + limpio;
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
