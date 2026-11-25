package com.tuempresa.citasmedicas.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente Retrofit (patrón Singleton).
 * <p>
 * Instala {@link MockInterceptor}, por lo que todas las peticiones devuelven
 * datos sintéticos generados por IA.
 */
public final class ApiClient {

    private static final String BASE_URL = "https://mock.citasmedicas.local/api/";

    private static volatile Retrofit retrofit;
    private static volatile MedicalApiService api;

    private ApiClient() {
    }

    /**
     * Double-checked locking: sustituye al {@code @Singleton} de Hilt.
     * Sin {@code volatile} dos hilos podían ver un Retrofit a medio construir;
     * el costo de la sincronización es despreciable porque sólo se paga la
     * primera vez.
     */
    public static Retrofit getRetrofit() {
        Retrofit local = retrofit;
        if (local == null) {
            synchronized (ApiClient.class) {
                local = retrofit;
                if (local == null) {
                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                            .addInterceptor(new MockInterceptor())
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build();

                    local = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(okHttpClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    retrofit = local;
                }
            }
        }
        return local;
    }

    /**
     * Devuelve el servicio ya creado en vez de reconstruirlo en cada llamada:
     * {@code create()} genera un proxy nuevo cada vez, así que cachearlo evita
     * basura innecesaria en pantallas con varias peticiones.
     */
    public static MedicalApiService getApi() {
        MedicalApiService local = api;
        if (local == null) {
            synchronized (ApiClient.class) {
                local = api;
                if (local == null) {
                    local = getRetrofit().create(MedicalApiService.class);
                    api = local;
                }
            }
        }
        return local;
    }
}
