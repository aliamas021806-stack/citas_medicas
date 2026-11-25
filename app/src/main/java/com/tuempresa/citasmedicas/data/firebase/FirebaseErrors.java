package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;

import retrofit2.Response;

/**
 * Extrae el mensaje de error que devuelve Firebase cuando la respuesta es un HTTP 4xx/5xx.
 * <p>
 * <b>El error que evita.</b> En Retrofit, cuando el código HTTP no es 2xx el cuerpo
 * <b>no</b> se convierte al tipo declarado: {@code response.body()} devuelve
 * {@code null} y el JSON real queda en {@code response.errorBody()}. Si se lee
 * {@code body()} a secas, los mensajes de Firebase ({@code EMAIL_EXISTS},
 * {@code INVALID_PASSWORD}, {@code CONFIGURATION_NOT_FOUND}...) se pierden y el usuario
 * sólo ve un genérico "inténtalo de nuevo". Esta clase lee el cuerpo correcto.
 * <p>
 * <b>Java puro:</b> sólo Gson (ya presente) y Retrofit.
 */
public final class FirebaseErrors {

    private static final Gson GSON = new Gson();

    private FirebaseErrors() {
        // Clase de utilidades.
    }

    /**
     * Devuelve la respuesta del cuerpo si llegó con 2xx, o reconstruye la respuesta de
     * error a partir de {@code errorBody()} para poder leer sus campos.
     *
     * @param response respuesta de Retrofit tal cual
     * @return el cuerpo útil (éxito o error) o {@code null} si no se pudo leer
     */
    @Nullable
    public static FirebaseAuthApi.AuthResponse leerRespuestaAuth(
            @NonNull Response<FirebaseAuthApi.AuthResponse> response) {
        if (response.body() != null) {
            return response.body();
        }
        return parsearAuth(cuerpoDeError(response));
    }

    /** Convierte el texto JSON de un error en el modelo, si tiene la forma esperada. */
    @Nullable
    public static FirebaseAuthApi.AuthResponse parsearAuth(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, FirebaseAuthApi.AuthResponse.class);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    /**
     * Lee el cuerpo de una respuesta HTTP no exitosa.
     *
     * @return el JSON en texto, o {@code null} si no hay cuerpo legible
     */
    @Nullable
    public static String cuerpoDeError(@NonNull Response<?> response) {
        if (response.errorBody() == null) {
            return null;
        }
        try {
            return response.errorBody().string();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Busca en el JSON de error el texto que identifica la causa.
     * <p>
     * Firebase lo coloca en {@code error.message}. Si el cuerpo no tiene esa forma (por
     * ejemplo, un HTML de un proxy), se devuelve el texto completo recortado para que
     * el mensaje al usuario no quede vacío.
     */
    @NonNull
    public static String mensajeCrudo(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }
        FirebaseAuthApi.AuthResponse parseada = parsearAuth(json);
        if (parseada != null && parseada.error != null && parseada.error.message != null) {
            return parseada.error.message;
        }
        // Sin estructura reconocible: se devuelve algo acotado para no volcar un HTML.
        String limpio = json.trim().replaceAll("\\s+", " ");
        return limpio.length() > 200 ? limpio.substring(0, 200) : limpio;
    }

    /**
     * Describe en español por qué un 403/404 de Firestore ocurre.
     * <p>
     * Firestore devuelve los detalles en {@code error.message}; el caso más frecuente
     * con diferencia es que la base de datos <b>no exista todavía</b> en el proyecto,
     * que es justo lo que hay que decirle al usuario en lugar de un "403" a secas.
     */
    @NonNull
    public static String describirErrorFirestore(int codigo, @Nullable String json) {
        String crudo = mensajeCrudo(json);
        String minus = crudo.toLowerCase(java.util.Locale.ROOT);

        if (minus.contains("does not exist") && minus.contains("database")) {
            return "La base de datos de Firestore no está creada en este proyecto. "
                    + "Créala en Firebase Console → Firestore Database → Crear base de datos";
        }
        if (minus.contains("permission") || minus.contains("insufficient")) {
            return "Las Reglas de seguridad de Firestore bloquean la lectura. "
                    + "Revisa Firebase Console → Firestore → Reglas";
        }
        if (minus.contains("not found") && minus.contains("collection")) {
            return "La colección no existe en Firestore";
        }
        if (minus.contains("api key") || minus.contains("api_key_invalid")) {
            return "La clave de Firebase no es válida para este proyecto";
        }
        return crudo;
    }
}
