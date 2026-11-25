package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * API REST de Firebase Authentication (Identity Toolkit), definida con Retrofit.
 * <p>
 * <b>Por qué REST y no el SDK.</b> El SDK {@code firebase-auth} trae
 * {@code kotlin-stdlib} y coroutines. Estas dos llamadas HTTP hacen lo mismo sin
 * añadir ni una dependencia nueva: la app ya tenía Retrofit + Gson. Así se mantiene
 * la restricción de <b>Java puro</b>.
 * <p>
 * La clave de API viaja como parámetro {@code key}, tal y como documenta Google.
 * Es una clave pública de cliente (no un secreto de servidor): su función es
 * identificar el proyecto, no autorizar. La autorización de verdad la hacen las
 * <i>Reglas de seguridad</i> de Firestore.
 */
public interface FirebaseAuthApi {

    /**
     * Inicia sesión con email + contraseña.
     * <p>
     * <b>Por qué el prefijo {@code ./}.</b> Retrofit resuelve la ruta relativa contra
     * la URL base siguiendo las reglas de {@code java.net.URI}. Una ruta que contiene
     * dos puntos ({@code accounts:signInWithPassword}) se interpreta como un
     * <i>esquema</i> de URI ("accounts:"), y la petición falla con
     * <i>"Malformed URL"</i> sin llegar a la red. Al empezar por {@code ./} se
     * garantiza que se trate como ruta relativa y se concatene a la base.
     */
    @POST("./accounts:signInWithPassword")
    Call<AuthResponse> signInWithPassword(@Query("key") String apiKey,
                                          @NonNull @Body AuthPayload body);

    /** Crea una cuenta nueva con email + contraseña (mismo motivo para el {@code ./}). */
    @POST("./accounts:signUp")
    Call<AuthResponse> signUp(@Query("key") String apiKey,
                              @NonNull @Body AuthPayload body);

    // ───────────────────────── Modelos de petición/respuesta ─────────────────────────

    /** Cuerpo de la petición: email, contraseña y si queremos el idToken. */
    class AuthPayload {
        @SerializedName("email")
        public String email;

        @SerializedName("password")
        public String password;

        @SerializedName("returnSecureToken")
        public boolean returnSecureToken = true;

        public AuthPayload(@Nullable String email, @Nullable String password) {
            this.email = email;
            this.password = password;
        }
    }

    /**
     * Respuesta de Firebase.
     * <p>
     * Los campos se declaran como envoltorios ({@link String}, {@link Long}) porque
     * Firebase los omite cuando hay error: así distinguimos "ausente" de "vacío".
     */
    class AuthResponse {
        @SerializedName("localId")
        public String localId;

        @SerializedName("email")
        public String email;

        @SerializedName("idToken")
        public String idToken;

        @SerializedName("refreshToken")
        public String refreshToken;

        @SerializedName("expiresIn")
        public String expiresIn;

        @SerializedName("displayName")
        public String displayName;

        /** Estructura del error, presente solo cuando la operación falla. */
        @SerializedName("error")
        public AuthError error;
    }

    /** Error devuelto por Firebase Authentication. */
    class AuthError {
        @SerializedName("code")
        public Integer code;

        @SerializedName("message")
        public String message;
    }
}
