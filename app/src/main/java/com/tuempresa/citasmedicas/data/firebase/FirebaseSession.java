package com.tuempresa.citasmedicas.data.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.User;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Guarda el <b>idToken</b> de Firebase y lo renueva para que Firestore pueda
 * identificar al usuario en cada petición.
 * <p>
 * <b>El problema que resuelve.</b> La app habla con Firestore por REST. Ese endpoint
 * no es anónimo por naturaleza: para que las <i>Reglas de seguridad</i> puedan
 * identificar al usuario, hay que enviar la cabecera
 * {@code Authorization: Bearer <idToken>}. Sin ella, la única forma de que las
 * lecturas funcionaran sería tener reglas públicas ({@code allow read, write: if true}),
 * es decir, dejar la base abierta a cualquiera.
 * <p>
 * <b>Por qué también renueva.</b> El {@code idToken} caduca a la hora. Si sólo se
 * guardara, al día siguiente la app dejaría de autenticar en silencio: las reglas
 * endurecidas devolverían 403, el catálogo caería al respaldo local y las citas
 * dejarían de subirse, sin ningún error visible. Con el {@code refreshToken} (que no
 * caduca) se pide un token nuevo automáticamente y el problema desaparece.
 * <p>
 * <b>Qué se guarda y dónde.</b> {@code idToken}, {@code refreshToken} y {@code localId}
 * en {@link SharedPreferences} propio, separado del de {@code SessionManager}.
 * <p>
 * <b>Java puro:</b> {@code org.json}, {@code android.util.Base64} y OkHttp (ya
 * presente para Retrofit). No se añade ninguna dependencia nueva.
 */
public final class FirebaseSession {

    private static final String PREF_NAME = "citas_medicas_firebase";
    private static final String KEY_TOKEN = "id_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_UID = "local_id";

    /** Margen de seguridad: se renueva un token que caduque en menos de esto. */
    private static final long MARGEN_MS = 60_000L;

    /** Endpoint de renovación de Firebase (securetoken). */
    private static final String URL_REFRESH =
            "https://securetoken.googleapis.com/v1/token";

    private static volatile SharedPreferences prefs;
    private static volatile OkHttpClient http;

    private FirebaseSession() {
        // Clase de utilidades.
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        SharedPreferences local = prefs;
        if (local == null) {
            synchronized (FirebaseSession.class) {
                local = prefs;
                if (local == null) {
                    local = context.getApplicationContext()
                            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    prefs = local;
                }
            }
        }
        return local;
    }

    private static OkHttpClient http() {
        OkHttpClient local = http;
        if (local == null) {
            synchronized (FirebaseSession.class) {
                local = http;
                if (local == null) {
                    local = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build();
                    http = local;
                }
            }
        }
        return local;
    }

    /**
     * Guarda la sesión devuelta por Firebase tras un login o registro correcto.
     * <p>
     * Se llama desde {@link FirebaseAuthRepository} justo después de recibir el
     * {@code 200} de Identity Toolkit.
     */
    public static void activate(@NonNull Context context,
                                @Nullable String idToken,
                                @Nullable String refreshToken,
                                @Nullable String localId) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (idToken == null || idToken.isEmpty()) {
            editor.remove(KEY_TOKEN);
        } else {
            editor.putString(KEY_TOKEN, idToken);
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            editor.putString(KEY_REFRESH, refreshToken);
        }
        if (localId != null && !localId.isEmpty()) {
            editor.putString(KEY_UID, localId);
        }
        editor.apply();
    }

    /**
     * Token válido para autenticar peticiones, o {@code null} si no hay sesión.
     * <p>
     * Si el token guardado ya caducó pero existe un {@code refreshToken}, se pide uno
     * nuevo. Si la renovación falla (sin red, token revocado), devuelve {@code null} y
     * la petición sale como anónima: es preferible eso a enviar una credencial muerta
     * que provocaría un {@code 401}.
     * <p>
     * <b>Nota:</b> puede bloquear si hay que renovar. Se invoca desde un interceptor de
     * OkHttp, que ya se ejecuta fuera del hilo principal.
     */
    @Nullable
    public static String token(@NonNull Context context) {
        String jwt = prefs(context).getString(KEY_TOKEN, null);
        if (jwt != null && !jwt.isEmpty() && esTokenVigente(jwt)) {
            return jwt;
        }
        // Caducado o ausente: intentar renovar.
        return renovar(context);
    }

    /**
     * Pide un {@code idToken} nuevo usando el {@code refreshToken}.
     *
     * @return el token nuevo, o {@code null} si no se pudo renovar
     */
    @Nullable
    private static String renovar(@NonNull Context context) {
        String refresh = prefs(context).getString(KEY_REFRESH, null);
        if (refresh == null || refresh.isEmpty()) {
            return null;
        }
        String apiKey = FirebaseConfig.apiKey();
        if (apiKey.isEmpty()) {
            return null;
        }

        RequestBody cuerpo = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build();

        Request peticion = new Request.Builder()
                .url(URL_REFRESH + "?key=" + apiKey)
                .post(cuerpo)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response respuesta = http().newCall(peticion).execute()) {
            if (!respuesta.isSuccessful()) {
                android.util.Log.w("FirebaseSession", "No se pudo renovar el token: HTTP "
                        + respuesta.code());
                return null;
            }
            String texto = respuesta.body() != null ? respuesta.body().string() : "";
            if (texto.isEmpty()) {
                return null;
            }
            JSONObject json = new JSONObject(texto);
            String nuevoId = json.optString("id_token", "");
            String nuevoRefresh = json.optString("refresh_token", "");
            String uid = json.optString("user_id", "");

            if (nuevoId.isEmpty()) {
                return null;
            }
            // Firebase rota el refreshToken: guardar el nuevo o se pierde la sesión.
            activate(context, nuevoId,
                    nuevoRefresh.isEmpty() ? refresh : nuevoRefresh,
                    uid.isEmpty() ? null : uid);
            return nuevoId;
        } catch (IOException | org.json.JSONException e) {
            android.util.Log.w("FirebaseSession", "Error renovando el token: " + e.getMessage());
            return null;
        }
    }

    /** ¿Hay una sesión de Firebase utilizable ahora mismo? */
    public static boolean isActive(@NonNull Context context) {
        return token(context) != null;
    }

    /**
     * ¿Hay sesión guardada (aunque el token haya caducado)?
     * <p>
     * Útil para diagnósticos: distingue "nunca inició sesión" de "la sesión caducó y
     * no se pudo renovar", que son problemas distintos.
     */
    public static boolean hasStoredSession(@NonNull Context context) {
        String jwt = prefs(context).getString(KEY_TOKEN, null);
        String refresh = prefs(context).getString(KEY_REFRESH, null);
        return (jwt != null && !jwt.isEmpty()) || (refresh != null && !refresh.isEmpty());
    }

    /** uid del usuario en Firebase, o {@code null} si no hay sesión. */
    @Nullable
    public static String uid(@NonNull Context context) {
        return prefs(context).getString(KEY_UID, null);
    }

    /** Borra la sesión de Firebase. Se llama al cerrar sesión en la app. */
    public static void clear(@NonNull Context context) {
        prefs(context).edit().clear().apply();
    }

    /**
     * ¿El JWT todavía sirve?
     * <p>
     * Un JWT tiene tres partes separadas por puntos: {@code cabecera.payload.firma}.
     * El {@code payload} es JSON en Base64URL y contiene {@code exp} (segundos desde
     * 1970). Si no se puede leer, se asume <b>no</b> vigente: fallar hacia el lado
     * seguro evita mandar basura al servidor.
     * <p>
     * Es {@code public} para poder probarlo sin red.
     */
    public static boolean esTokenVigente(@Nullable String jwt) {
        if (jwt == null) {
            return false;
        }
        String[] partes = jwt.split("\\.");
        if (partes.length < 2 || partes[1].isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.decode(partes[1], Base64.URL_SAFE | Base64.NO_WRAP
                    | Base64.NO_PADDING);
            String json = new String(decoded, StandardCharsets.UTF_8);
            long exp = new JSONObject(json).optLong("exp", 0L);
            if (exp <= 0L) {
                return false;
            }
            return (exp * 1000L) - System.currentTimeMillis() > MARGEN_MS;
        } catch (Exception e) {
            // Base64 inválido, JSON mal formado, exp ausente: no se usa el token.
            return false;
        }
    }

    /**
     * Construye el {@link User} de dominio reutilizando el uid de Firebase.
     */
    @NonNull
    public static User withFirebaseId(@NonNull User user, @NonNull Context context) {
        String uid = uid(context);
        if (uid == null || uid.isEmpty()) {
            return user;
        }
        return new User(uid, user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAge());
    }
}
