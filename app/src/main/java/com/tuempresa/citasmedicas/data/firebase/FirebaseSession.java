package com.tuempresa.citasmedicas.data.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.User;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Guarda el <b>idToken</b> de Firebase y lo pone a disposición de Firestore.
 * <p>
 * <b>El problema que resuelve.</b> La app habla con Firestore por REST. Ese endpoint
 * no es anónimo por naturaleza: para que las <i>Reglas de seguridad</i> puedan
 * identificar al usuario, hay que enviar la cabecera
 * {@code Authorization: Bearer <idToken>}. Sin ella, la única forma de que las
 * lecturas funcionaran sería tener reglas públicas ({@code allow read, write: if true}),
 * es decir, dejar la base abierta a cualquiera. Con esto se puede endurecer a
 * {@code if request.auth != null} sin tocar ni una línea de la UI.
 * <p>
 * <b>Qué se guarda y dónde.</b>
 * <ul>
 *   <li>{@code idToken}: caduca a la hora. Es lo que viaja en la cabecera.</li>
 *   <li>{@code refreshToken}: no caduca. Se guarda para no obligar a volver a
 *       escribir la contraseña en cada arranque.</li>
 *   <li>{@code localId}: el identificador del usuario en Firebase (uid).</li>
 * </ul>
 * Se usa {@link SharedPreferences} propio, separado del de {@code SessionManager}.

 * <b>Por qué se comprueba la caducidad.</b> Enviar un token caducado no es inocuo:
 * Firestore responde {@code 401} y la petición se pierde, aunque la app siguiera
 * funcionando en local. Decodificar el campo {@code exp} del JWT (que es JSON en
 * Base64, sin firma que verificar en cliente) permite no enviarlo cuando ya no sirve.
 * La firma la valida el servidor; aquí sólo se lee la fecha para no molestar.
 * <p>
 * <b>Java puro:</b> {@code org.json} y {@code android.util.Base64} vienen en el
 * framework. No se añade ninguna dependencia.
 */
public final class FirebaseSession {

    private static final String PREF_NAME = "citas_medicas_firebase";
    private static final String KEY_TOKEN = "id_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_UID = "local_id";

    /** Margen de seguridad: se descarta un token que caduque en menos de esto. */
    private static final long MARGEN_MS = 60_000L;

    private static volatile SharedPreferences prefs;

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
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor editor = p.edit();
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
     * Devuelve {@code null} también cuando el token ya caducó: es preferible no
     * enviar nada (y que el servidor decida con las reglas públicas) que enviar una
     * credencial inválida y recibir un {@code 401} que rompería la lectura.
     */
    @Nullable
    public static String token(@NonNull Context context) {
        String jwt = prefs(context).getString(KEY_TOKEN, null);
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        return esTokenVigente(jwt) ? jwt : null;
    }

    /** ¿Hay una sesión de Firebase utilizable ahora mismo? */
    public static boolean isActive(@NonNull Context context) {
        return token(context) != null;
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
            long ahoraSegundos = System.currentTimeMillis() / 1000L;
            return (exp * 1000L) - System.currentTimeMillis() > MARGEN_MS
                    && exp > ahoraSegundos;
        } catch (Exception e) {
            // Base64 inválido, JSON mal formado, exp ausente: no se usa el token.
            return false;
        }
    }

    /**
     * Construye el {@link User} de dominio reutilizando el uid de Firebase.
     * <p>
     * Sólo se usa en pruebas y en el arranque: mantiene el identificador coherente
     * entre la sesión local ({@code SessionManager}) y la de Firebase.
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
