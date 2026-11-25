package com.tuempresa.citasmedicas.data.firebase;

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
 * Se construye de forma perezosa: si la app arranca en modo mock, esta clase nunca
 * se inicializa y no gasta memoria ni red.
 */
public final class FirestoreClient {

    private static volatile Retrofit retrofit;
    private static volatile FirestoreApi api;

    private FirestoreClient() {
    }

    /** Double-checked locking, igual que en {@link ApiClient}. */
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
