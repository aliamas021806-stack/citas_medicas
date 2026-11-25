package com.tuempresa.citasmedicas.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.data.remote.dto.DtoMapper;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lógica de red compartida por los repositorios.
 * <p>
 * Evita duplicar en cada repositorio el manejo de errores y el reenvío al hilo
 * principal, y garantiza que <b>todos</b> los puntos de entrada devuelvan modelos
 * de dominio mapeados desde DTO.
 * <p>
 * Reglas de error homogéneas:
 * <ul>
 *   <li>HTTP 2xx con cuerpo → {@code onSuccess} con el modelo mapeado.</li>
 *   <li>HTTP 2xx sin cuerpo → {@code onError}: el recurso solicitado no existe.
 *       (Antes se trataba como éxito y la UI pintaba datos vacíos.)</li>
 *   <li>HTTP no 2xx → {@code onError} con el código, útil para distinguir 404 de 500.</li>
 *   <li>Fallo de red/IO → {@code onError} con el mensaje de la excepción.</li>
 * </ul>
 * Los callbacks se publican siempre en el hilo principal, de modo que un ViewModel
 * puede escribir en {@code LiveData} sin comprobaciones adicionales.
 *
 * @param <D> tipo del DTO que devuelve la API
 * @param <M> tipo del modelo de dominio que espera la app
 */
final class RemoteCall<D, M> {

    /** Traduce un DTO de red al modelo de dominio correspondiente. */
    interface Mapper<D, M> {
        @NonNull
        M map(@Nullable D dto);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RemoteCall() {
    }

    /** Ejecuta la llamada y entrega un único resultado mapeado. */
    static <D, M> void single(@NonNull Call<D> call,
                              @NonNull Mapper<D, M> mapper,
                              @NonNull RepositoryCallback<M> callback) {
        call.enqueue(new Callback<D>() {
            @Override
            public void onResponse(@NonNull Call<D> c, @NonNull Response<D> response) {
                if (response.isSuccessful() && response.body() != null) {
                    final M model = mapper.map(response.body());
                    MAIN.post(() -> callback.onSuccess(model));
                } else if (response.isSuccessful()) {
                    MAIN.post(() -> callback.onError(
                            "No se encontró la información solicitada"));
                } else {
                    final int code = response.code();
                    MAIN.post(() -> callback.onError(errorForCode(code)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<D> c, @NonNull Throwable t) {
                final String message = t.getMessage();
                MAIN.post(() -> callback.onError(
                        message != null ? "Error de red: " + message : "Error de red"));
            }
        });
    }

    /** Ejecuta la llamada y entrega una lista mapeada (nunca {@code null}). */
    static <D, M> void list(@NonNull Call<List<D>> call,
                            @NonNull Mapper<D, M> mapper,
                            @NonNull RepositoryCallback<List<M>> callback) {
        call.enqueue(new Callback<List<D>>() {
            @Override
            public void onResponse(@NonNull Call<List<D>> c, @NonNull Response<List<D>> response) {
                if (response.isSuccessful()) {
                    List<D> body = response.body();
                    List<M> mapped = new ArrayList<>();
                    if (body != null) {
                        for (D dto : body) {
                            if (dto != null) {
                                mapped.add(mapper.map(dto));
                            }
                        }
                    }
                    MAIN.post(() -> callback.onSuccess(mapped));
                } else {
                    final int code = response.code();
                    MAIN.post(() -> callback.onError(errorForCode(code)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<D>> c, @NonNull Throwable t) {
                final String message = t.getMessage();
                MAIN.post(() -> callback.onError(
                        message != null ? "Error de red: " + message : "Error de red"));
            }
        });
    }

    /** Mensaje de error legible según el código HTTP. */
    @NonNull
    static String errorForCode(int code) {
        switch (code) {
            case 400:
                return "Solicitud incorrecta al servidor";
            case 401:
            case 403:
                return "No tienes permiso para acceder a este recurso";
            case 404:
                return "No se encontró el recurso solicitado";
            case 429:
                return "Demasiadas peticiones. Inténtalo en unos segundos";
            default:
                if (code >= 500) {
                    return "El servidor no está disponible en este momento";
                }
                return "Respuesta inválida del servidor (" + code + ")";
        }
    }

    /** Mapper reutilizable para listas de DTO sin transformación adicional. */
    @NonNull
    static <T> List<T> copyOf(@Nullable List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
