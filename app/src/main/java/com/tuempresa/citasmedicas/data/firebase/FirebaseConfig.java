package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.BuildConfig;

/**
 * Configuración de Firebase leída en tiempo de compilación.
 * <p>
 * <b>De dónde salen los valores.</b> No están escritos en el código ni se suben al
 * repositorio: los inyecta Gradle desde {@code local.properties} (o desde variables
 * de entorno) como {@code BuildConfig}. Ver {@code app/build.gradle}.
 * <p>
 * <b>Por qué esto permite seguir en Java puro.</b> El SDK oficial de Firebase
 * (Authentication / Firestore) arrastra {@code kotlin-stdlib} y
 * {@code kotlinx-coroutines}. Aquí no se usa ningún SDK: se consumen las <b>APIs REST</b>
 * de Google ({@code identitytoolkit} y {@code firestore.googleapis.com}) con el
 * Retrofit/OkHttp que la app ya tenía. Resultado: 0 archivos Kotlin y 0 bytecode
 * Kotlin en el APK.
 * <p>
 * <b>Comportamiento si falta configuración.</b> {@link #isEnabled()} devuelve
 * {@code false} y la app arranca en <i>modo mock</i>, exactamente como hasta ahora.
 * Eso hace que el proyecto compile en GitHub Actions y que el APK del profesor
 * funcione sin configurar nada.
 */
public final class FirebaseConfig {

    /** Colecciones usadas en Firestore. */
    public static final String COLLECTION_DOCTORS = "doctors";
    public static final String COLLECTION_SPECIALTIES = "specialties";
    public static final String COLLECTION_PATIENTS = "patients";
    public static final String COLLECTION_APPOINTMENTS = "appointments";

    private FirebaseConfig() {
        // Clase de utilidades: no se instancia.
    }

    /** ¿Hay credenciales suficientes para intentar hablar con Firebase? */
    public static boolean isEnabled() {
        return BuildConfig.FIREBASE_ENABLED;
    }

    @NonNull
    public static String projectId() {
        return safe(BuildConfig.FIREBASE_PROJECT_ID);
    }

    @NonNull
    public static String apiKey() {
        return safe(BuildConfig.FIREBASE_API_KEY);
    }

    @NonNull
    public static String applicationId() {
        return safe(BuildConfig.FIREBASE_APP_ID);
    }

    /**
     * URL base de Firestore para este proyecto.
     * <p>
     * Firestore exige la barra final en {@code baseUrl()} de Retrofit, y los
     * endpoints de documentos usan la forma
     * {@code projects/{p}/databases/(default)/documents/{coleccion}}.
     */
    @NonNull
    public static String firestoreBaseUrl() {
        return "https://firestore.googleapis.com/v1/projects/" + projectId()
                + "/databases/(default)/documents/";
    }

    /** URL base de Firebase Authentication (email/contraseña). */
    @NonNull
    public static String authBaseUrl() {
        return "https://identitytoolkit.googleapis.com/v1/";
    }

    /**
     * Descripción legible del modo activo, para mostrarla en la UI o en los logs.
     *
     * @return {@code "Firebase"} si está configurado, {@code "Mock"} si no
     */
    @NonNull
    public static String describeMode() {
        if (!isEnabled()) {
            return "Mock";
        }
        String id = projectId();
        return id.isEmpty() ? "Firebase" : "Firebase (" + id + ")";
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
