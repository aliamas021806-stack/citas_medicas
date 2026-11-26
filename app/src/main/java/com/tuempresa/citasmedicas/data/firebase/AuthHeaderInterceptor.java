package com.tuempresa.citasmedicas.data.firebase;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Añade la cabecera {@code Authorization: Bearer <idToken>} a cada petición a
 * Firestore, y la API Key como {@code key} si la URL no la trae.
 * <p>
 * <b>El fallo que corrige.</b> Antes el cliente de Firestore se construía sin
 * ninguna credencial. Funcionaba sólo porque las Reglas de Firestore estaban en
 * {@code allow read, write: if true}: en cuanto se endurecieran a
 * {@code if request.auth != null} —que es el paso previsto antes de entregar—,
 * <b>todas</b> las lecturas y escrituras habrían empezado a fallar con
 * {@code 403 Missing or insufficient permissions}. La app habría mostrado el catálogo
 * de respaldo y las citas habrían dejado de subir, sin ningún error visible.
 * <p>
 * <b>Por qué un interceptor y no un parámetro.</b> Los endpoints de Firestore ya
 * declaran {@code @Path} para la colección; añadir {@code @Query("key")} obligaría a
 * cambiar la firma de todos los métodos y de todos sus llamadores. Un interceptor
 * resuelve lo mismo en un único sitio, y de paso mantiene la credencial fuera de la
 * capa de repositorios.
 * <p>
 * <b>Qué hace exactamente.</b>
 * <ol>
 *   <li>Si hay un {@code idToken} vigente, añade {@code Authorization: Bearer ...}.</li>
 *   <li>Si no lo hay, no añade la cabecera: la petición sigue siendo anónima y la
 *       decisión la toman las reglas del servidor.</li>
 *   <li>Si la URL no lleva {@code key}, añade la API Key, que identifica el proyecto
 *       ante Google (no autoriza: eso lo hacen las reglas y el token).</li>
 * </ol>
 * <p>
 * <b>Java puro:</b> una clase de OkHttp, sin Kotlin.
 */
public final class AuthHeaderInterceptor implements Interceptor {

    private final Context context;
    private final String apiKey;

    public AuthHeaderInterceptor(@NonNull Context context, @NonNull String apiKey) {
        this.context = context.getApplicationContext();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();

        // 1) Credencial de usuario, si la hay y sigue vigente.
        String token = FirebaseSession.token(context);
        if (token != null && !token.isEmpty()
                && original.header("Authorization") == null) {
            builder.header("Authorization", "Bearer " + token);
        }

        // 2) API Key, sólo si la URL no la incluye ya.
        String url = original.url().toString();
        if (!apiKey.isEmpty() && !url.contains("key=")) {
            String separador = original.url().query() == null ? "?" : "&";
            builder.url(url + separador + "key=" + apiKey);
        }

        return chain.proceed(builder.build());
    }

    /** Sólo para pruebas: comprueba que la clave se inyecta cuando procede. */
    @Nullable
    public String apiKeyForTesting() {
        return apiKey;
    }
}
