package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.AuthValidator;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Autenticación real contra <b>Firebase Authentication</b> mediante su API REST.
 * <p>
 * Implementa {@link IAuthRepository}, el mismo contrato que usaba la versión
 * simulada. Por eso el {@code AuthViewModel}, los casos de uso {@code LoginUseCase}
 * y {@code RegisterUseCase} y la pantalla de login <b>no cambian ni una línea</b>:
 * sólo se cambia qué implementación devuelve {@code RepositoryProvider}.
 * <p>
 * <b>Java puro.</b> No se usa el SDK {@code firebase-auth} (que arrastraría Kotlin).
 * Se llama a {@code identitytoolkit.googleapis.com} con Retrofit.
 * <p>
 * <b>Validación primero, red después.</b> Se reutiliza {@link AuthValidator} para
 * rechazar correos mal formados sin gastar una petición: mismo comportamiento que
 * la versión mock, así los mensajes al usuario son idénticos.
 * <p>
 * <b>Manejo de errores.</b> Firebase devuelve códigos como {@code EMAIL_EXISTS} o
 * {@code INVALID_PASSWORD}; se traducen a mensajes en español porque la UI los
 * muestra tal cual.
 */
public class FirebaseAuthRepository implements IAuthRepository {

    private static final String TAG_ERROR_EMAIL_EXISTS = "EMAIL_EXISTS";
    private static final String TAG_ERROR_NOT_FOUND = "EMAIL_NOT_FOUND";
    private static final String TAG_ERROR_INVALID_PASSWORD = "INVALID_PASSWORD";
    private static final String TAG_ERROR_INVALID_EMAIL = "INVALID_EMAIL";
    private static final String TAG_ERROR_WEAK_PASSWORD = "WEAK_PASSWORD";
    private static final String TAG_ERROR_TOO_MANY = "TOO_MANY_ATTEMPTS_TRY_LATER";
    private static final String TAG_ERROR_OPERATION_NOT_ALLOWED = "OPERATION_NOT_ALLOWED";
    private static final String TAG_ERROR_API_KEY = "API key not valid";
    /**
     * Firebase devuelve este código cuando el proyecto no tiene el proveedor de
     * email/contraseña habilitado, o cuando la configuración de Authentication
     * todavía no se ha creado en el proyecto.
     */
    private static final String TAG_ERROR_CONFIG_NOT_FOUND = "CONFIGURATION_NOT_FOUND";
    /** La API Key no pertenece a este proyecto o está restringida. */
    private static final String TAG_ERROR_API_KEY_INVALID = "API_KEY_INVALID";
    /** El proveedor está desactivado en Firebase Console. */
    private static final String TAG_ERROR_ADMIN_ONLY = "ADMIN_ONLY_OPERATION";
    private static final String TAG_ERROR_UNAUTHORIZED = "UNAUTHORIZED_DOMAIN";

    private final FirebaseAuthApi api;
    /** Necesario para guardar el idToken con el que Firestore autenticará después. */
    @Nullable
    private final android.content.Context context;

    public FirebaseAuthRepository() {
        this(null);
    }

    /**
     * @param context contexto de aplicación. Si se pasa, tras un login correcto se
     *                guarda el {@code idToken} en {@link FirebaseSession}, de modo que
     *                las peticiones a Firestore viajen ya autenticadas. Si es
     *                {@code null}, la app sigue funcionando (compatible con pruebas).
     */
    public FirebaseAuthRepository(@Nullable android.content.Context context) {
        this.context = context != null ? context.getApplicationContext() : null;

        // Cliente propio: timeouts holgados para redes móviles lentas.
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        this.api = new Retrofit.Builder()
                .baseUrl(FirebaseConfig.authBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FirebaseAuthApi.class);
    }

    /**
     * Guarda la sesión de Firebase para que Firestore pueda autenticar.
     * <p>
     * <b>Por qué es imprescindible.</b> Firestore y Authentication son servicios
     * distintos: iniciar sesión en el segundo no hace que el primero sepa quién eres.
     * El puente es el {@code idToken}, que hay que enviar en cada petición. Si no se
     * guarda, la app sólo puede leer Firestore con reglas públicas, que es justo lo
     * que hay que evitar antes de entregar.
     */
    private void recordarSesion(@NonNull FirebaseAuthApi.AuthResponse response) {
        if (context == null) {
            return;
        }
        FirebaseSession.activate(context, response.idToken, response.refreshToken,
                response.localId);
    }

    @Override
    public void login(@NonNull AuthRequest request,
                      @NonNull final RepositoryCallback<User> callback) {
        // 1) Validación local (misma que el modo mock: mensajes idénticos)
        String localError = AuthValidator.validateLogin(
                request.getEmail(), request.getPassword());
        if (localError != null) {
            callback.onError(localError);
            return;
        }

        // 2) Llamada real a Firebase
        api.signInWithPassword(FirebaseConfig.apiKey(),
                        new FirebaseAuthApi.AuthPayload(
                                request.getEmail().trim(), request.getPassword()))
                .enqueue(new retrofit2.Callback<FirebaseAuthApi.AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<FirebaseAuthApi.AuthResponse> call,
                                           @NonNull Response<FirebaseAuthApi.AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            recordarSesion(response.body());
                            callback.onSuccess(toUser(response.body(), request));
                        } else {
                            // En un HTTP 4xx Retrofit deja body() a null: el JSON del
                            // error está en errorBody(). Sin esto se perdía el motivo.
                            FirebaseAuthApi.AuthResponse cuerpo =
                                    FirebaseErrors.leerRespuestaAuth(response);
                            callback.onError(traducirError(cuerpo, false,
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<FirebaseAuthApi.AuthResponse> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    @Override
    public void register(@NonNull AuthRequest request,
                         @NonNull final RepositoryCallback<User> callback) {
        String localError = AuthValidator.validateRegister(
                request.getFullName(), request.getEmail(),
                request.getPassword(), request.getAge());
        if (localError != null) {
            callback.onError(localError);
            return;
        }

        api.signUp(FirebaseConfig.apiKey(),
                        new FirebaseAuthApi.AuthPayload(
                                request.getEmail().trim(), request.getPassword()))
                .enqueue(new retrofit2.Callback<FirebaseAuthApi.AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<FirebaseAuthApi.AuthResponse> call,
                                           @NonNull Response<FirebaseAuthApi.AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            recordarSesion(response.body());
                            callback.onSuccess(toUser(response.body(), request));
                        } else {
                            FirebaseAuthApi.AuthResponse cuerpo =
                                    FirebaseErrors.leerRespuestaAuth(response);
                            callback.onError(traducirError(cuerpo, true,
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<FirebaseAuthApi.AuthResponse> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    // ───────────────────────── Conversión y errores ─────────────────────────

    /**
     * Construye el {@link User} de dominio a partir de la respuesta de Firebase.
     * <p>
     * El nombre: en el registro lo pone el usuario; en el login Firebase no lo
     * devuelve, así que se deriva del correo igual que hacía la versión mock.
     */
    @NonNull
    private User toUser(@NonNull FirebaseAuthApi.AuthResponse response,
                        @NonNull AuthRequest request) {
        String email = response.email != null && !response.email.isEmpty()
                ? response.email : request.getEmail();
        String name;
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            name = request.getFullName().trim();
        } else if (response.displayName != null && !response.displayName.trim().isEmpty()) {
            name = response.displayName.trim();
        } else {
            name = derivarNombreDeEmail(email);
        }
        int age = request.getAge() > 0 ? request.getAge() : 30;
        // Firebase identifica al usuario con localId: se usa como id de sesión.
        String id = response.localId != null && !response.localId.isEmpty()
                ? response.localId : "user_01";
        return new User(id, name, email, "", age);
    }

    /** Deriva "Ana Pérez" a partir de "ana.perez@correo.com". */
    @NonNull
    private String derivarNombreDeEmail(@NonNull String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        local = local.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (local.isEmpty()) {
            return "Paciente";
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : local.split("\\s+")) {
            if (parte.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(parte.charAt(0)))
                    .append(parte.substring(1).toLowerCase(java.util.Locale.getDefault()))
                    .append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Traduce el error de Firebase a un mensaje en español.
     *
     * @param body       cuerpo ya parseado (puede ser {@code null})
     * @param esRegistro {@code true} si la operación era crear cuenta
     * @param jsonCrudo  texto del {@code errorBody()}, por si el parseo no lo capturó
     */
    @NonNull
    private String traducirError(@Nullable FirebaseAuthApi.AuthResponse body,
                                 boolean esRegistro,
                                 @Nullable String jsonCrudo) {
        String crudo = body != null && body.error != null && body.error.message != null
                ? body.error.message
                : FirebaseErrors.mensajeCrudo(jsonCrudo);
        if (crudo.contains(TAG_ERROR_EMAIL_EXISTS)) {
            return "Ya existe una cuenta con ese correo";
        }
        if (crudo.contains(TAG_ERROR_NOT_FOUND)) {
            return "No hay ninguna cuenta con ese correo";
        }
        if (crudo.contains(TAG_ERROR_INVALID_PASSWORD)) {
            return "La contraseña no es correcta";
        }
        if (crudo.contains(TAG_ERROR_INVALID_EMAIL)) {
            return "El correo no es válido";
        }
        if (crudo.contains(TAG_ERROR_WEAK_PASSWORD)) {
            return "La contraseña es demasiado débil (mínimo 6 caracteres)";
        }
        if (crudo.contains(TAG_ERROR_TOO_MANY)) {
            return "Demasiados intentos. Espera unos minutos e inténtalo de nuevo";
        }
        if (crudo.contains(TAG_ERROR_OPERATION_NOT_ALLOWED)) {
            return "El acceso con email/contraseña está deshabilitado en Firebase Console";
        }
        if (crudo.contains(TAG_ERROR_CONFIG_NOT_FOUND)) {
            return "Firebase Authentication no está configurado en el proyecto. "
                    + "Actívalo en Firebase Console → Authentication → Correo electrónico";
        }
        if (crudo.contains(TAG_ERROR_API_KEY_INVALID)) {
            return "La clave de Firebase no es válida para este proyecto. "
                    + "Revisa FIREBASE_API_KEY";
        }
        if (crudo.contains(TAG_ERROR_ADMIN_ONLY) || crudo.contains(TAG_ERROR_UNAUTHORIZED)) {
            return "La operación no está permitida con la configuración actual de Firebase";
        }
        if (crudo.contains(TAG_ERROR_API_KEY)) {
            return "La clave de Firebase no es válida. Revisa FIREBASE_API_KEY";
        }
        if (crudo.isEmpty()) {
            return esRegistro
                    ? "No se pudo crear la cuenta. Inténtalo de nuevo"
                    : "No se pudo iniciar sesión. Inténtalo de nuevo";
        }
        return crudo;
    }

    /** Mensaje para fallos de red (sin conexión, DNS, timeout...). */
    @NonNull
    private String mensajeDeRed(@NonNull Throwable t) {
        String causa = t.getMessage() != null ? t.getMessage() : "";
        if (causa.contains("Unable to resolve host")
                || causa.contains("UnknownHost")
                || causa.contains("connect")) {
            return "Sin conexión a internet. Revisa tu red e inténtalo de nuevo";
        }
        return "Error de conexión con Firebase: " + causa;
    }
}
