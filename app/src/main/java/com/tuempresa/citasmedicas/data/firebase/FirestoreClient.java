package com.tuempresa.citasmedicas.data.firebase;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.remote.ApiClient;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente Retrofit para Firestore (singleton).
 * <p>
 * Separado de {@link ApiClient} a propósito: aquél habla con la API simulada
 * ({@code MockInterceptor}) y éste con la nube real. Mantenerlos aparte permite
 * usar uno u otro sin que se contaminen, y deja el modo mock intacto para los tests
 * y para el APK del profesor.
 * <p>
 * <b>Credenciales.</b> A diferencia de la primera versión, el cliente se construye
 * con {@link AuthHeaderInterceptor}, que adjunta el {@code idToken} del usuario y la
 * API Key. Sin él, la app dependía de que las Reglas de Firestore fuesen públicas:
 * al endurecerlas a {@code if request.auth != null}, todo habría empezado a devolver
 * 403. Ese fallo no se veía en local porque las reglas estaban abiertas.
 * <p>
 * Se construye de forma perezosa: si la app arranca en modo mock, esta clase nunca
 * se inicializa y no gasta memoria ni red.
 */
public final class FirestoreClient {

    private static volatile Retrofit retrofit;
    private static volatile FirestoreApi api;

    private FirestoreClient() {
    }

    /**
     * Construye el cliente HTTP con las credenciales de la app.
     * <p>
     * {@code Context} es necesario porque el token vive en {@code SharedPreferences}.
     * Se usa el de aplicación, así que no se retiene ninguna Activity.
     */
    @NonNull
    private static OkHttpClient buildClient(@NonNull Context context) {
        return new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new AuthHeaderInterceptor(context, FirebaseConfig.apiKey()))
                .build();
    }

    /**
     * Versión con {@code Context}: la que deben usar los repositorios.
     * <p>
     * Si ya existe una instancia construida sin contexto (por ejemplo desde una
     * prueba antigua), se descarta y se reconstruye con credenciales: es preferible
     * una reconstrucción a seguir usando un cliente que no autentica.
     */
    @NonNull
    public static Retrofit getRetrofit(@NonNull Context context) {        Retrofit local = retrofit;
        if (local == null) {
            synchronized (FirestoreClient.class) {
                local = retrofit;
                if (local == null) {
                    local = new Retrofit.Builder()
                            .baseUrl(FirebaseConfig.firestoreBaseUrl())
                            .client(buildClient(context))
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    retrofit = local;
                }
            }
        }
        return local;
    }

    @NonNull
    public static FirestoreApi getApi(@NonNull Context context) {
        FirestoreApi local = api;
        if (local == null) {
            synchronized (FirestoreClient.class) {
                local = api;
                if (local == null) {
                    local = getRetrofit(context).create(FirestoreApi.class);
                    api = local;
                }
            }
        }
        return local;
    }

    /**
     * Variante sin {@code Context} que conserva la firma anterior.
     * <p>
     * <b>Uso restringido:</b> sólo para pruebas antiguas y para código que ya tenía
     * un cliente construido. Como no puede leer el token, crea un cliente
     * <i>sin</i> credenciales de usuario, así que las reglas endurecidas lo
     * rechazarán. El código de producción debe llamar a {@link #getApi(Context)}.
     *
     * @deprecated usar {@link #getApi(Context)} para que las peticiones se autentiquen
     */
    @Deprecated
    @NonNull
    public static Retrofit getRetrofit() {
        Retrofit local = retrofit;
        if (local == null) {
            synchronized (FirestoreClient.class) {
                local = retrofit;
                if (local == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build();
                    local = new Retrofit.Builder()
                            .baseUrl(FirebaseConfig.firestoreBaseUrl())
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    retrofit = local;
                }
            }
        }
        return local;
    }

    /**
     * Variante sin {@code Context}.
     *
     * @deprecated usar {@link #getApi(Context)} para que las peticiones se autentiquen
     */
    @Deprecated
    @NonNull
    public static FirestoreApi getApi() {
        FirestoreApi local = api;
        if (local == null) {
            synchronized (FirestoreClient.class) {
                local = api;
                if (local == null) {
                    local = getRetrofit().create(FirestoreApi.class);
                    api = local;
                }
            }
        }
        return local;
    }

    /**
     * Devuelve el {@link OkHttpClient} <b>de producción</b>, con el interceptor de
     * credenciales ya instalado.
     * <p>
     * Existe para las pruebas de extremo a extremo: permite lanzar una petición real
     * contra un servidor local y comprobar qué cabeceras salen de verdad. Sin esto,
     * una prueba podría instanciar el interceptor a mano y pasar en verde aunque
     * {@link #getApi(Context)} se hubiera quedado sin él (que es exactamente el fallo
     * que se corrigió).
     * <p>
     * No abre ninguna conexión ni toca la red: sólo construye el cliente.
     */
    @NonNull
    public static OkHttpClient clienteRealParaPruebas(@NonNull Context context) {
        return buildClient(context);
    }

    /**
     * Olvida la instancia cacheada.
     * <p>
     * Necesario en pruebas: la URL base depende de {@code BuildConfig}, y entre
     * tests queremos evitar arrastrar un cliente construido con otra configuración.
     */
    public static void reset() {
        synchronized (FirestoreClient.class) {
            api = null;
            retrofit = null;
        }
    }
}
