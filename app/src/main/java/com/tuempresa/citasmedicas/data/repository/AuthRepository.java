package com.tuempresa.citasmedicas.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;

import java.util.regex.Pattern;

/**
 * Repositorio de autenticación (SIMULADO).
 * <p>
 * No hay backend: valida credenciales con reglas locales y responde tras un
 * pequeño delay asíncrono (Handler) para reproducir la latencia de una API.
 * Conserva la EDAD del paciente en la sesión.
 */
public class AuthRepository {

    private static final long FAKE_DELAY_MS = 900L;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Login simulado: acepta email válido + contraseña de 4+ caracteres. */
    public void login(AuthRequest request, RepositoryCallback<User> callback) {
        mainHandler.postDelayed(() -> {
            if (TextUtils.isEmpty(request.getEmail()) || !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
                callback.onError("Ingresa un correo electrónico válido");
                return;
            }
            if (TextUtils.isEmpty(request.getPassword()) || request.getPassword().length() < 4) {
                callback.onError("La contraseña debe tener al menos 4 caracteres");
                return;
            }
            String name = deriveNameFromEmail(request.getEmail());
            // Edad por defecto "generada" para el login (si no viene en la request)
            int age = request.getAge() > 0 ? request.getAge() : 30;
            User user = new User("user_01", name, request.getEmail(), "+34 600 123 456", age);
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    /** Registro simulado: valida datos y devuelve el usuario creado (con edad). */
    public void register(AuthRequest request, RepositoryCallback<User> callback) {
        mainHandler.postDelayed(() -> {
            if (TextUtils.isEmpty(request.getFullName()) || request.getFullName().trim().length() < 3) {
                callback.onError("Ingresa tu nombre completo");
                return;
            }
            if (TextUtils.isEmpty(request.getEmail()) || !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
                callback.onError("Ingresa un correo electrónico válido");
                return;
            }
            if (TextUtils.isEmpty(request.getPassword()) || request.getPassword().length() < 4) {
                callback.onError("La contraseña debe tener al menos 4 caracteres");
                return;
            }
            if (request.getAge() < 1 || request.getAge() > 120) {
                callback.onError("Ingresa una edad válida (entre 1 y 120 años)");
                return;
            }
            User user = new User("user_01", request.getFullName().trim(),
                    request.getEmail(), "+34 600 123 456", request.getAge());
            callback.onSuccess(user);
        }, FAKE_DELAY_MS);
    }

    private String deriveNameFromEmail(String email) {
        String local = email.split("@")[0];
        local = local.replace('.', ' ').replace('_', ' ').trim();
        if (local.isEmpty()) {
            return "Paciente";
        }
        String[] parts = local.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.substring(1).toLowerCase())
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
