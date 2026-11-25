package com.tuempresa.citasmedicas.domain;

import androidx.annotation.NonNull;

/**
 * Caso de uso base de la capa de dominio (Clean Architecture adaptada a Java puro).
 * <p>
 * Equivalencia con el flujo Jetpack Compose solicitado:
 * <ul>
 *   <li>{@code suspend fun invoke(...)} de Kotlin  →  {@link #execute(Object, Callback)}</li>
 *   <li>{@code StateFlow<UiState>}                   →  {@code LiveData<Resource<T>>} en el ViewModel</li>
 *   <li>{@code viewModelScope.launch { }}            →  callbacks ya publicados en el hilo principal</li>
 * </ul>
 * La capa de dominio NO conoce Android UI, ni Retrofit, ni Room: solo depende de
 * las interfaces de repositorio. Esto es lo que permite testearla en JVM puro.
 *
 * @param <P> parámetro de entrada (usa {@link Void} si no hay)
 * @param <R> tipo del resultado
 */
public abstract class UseCase<P, R> {

    /** Salida del caso de uso (equivalente al valor emitido por un StateFlow). */
    public interface Callback<R> {
        void onSuccess(R result);

        void onError(String message);
    }

    /**
     * Punto de entrada único del caso de uso.
     *
     * @param params   entrada; puede ser {@code null} si el caso de uso no requiere datos
     * @param callback resultado de la operación
     */
    public abstract void execute(P params, @NonNull Callback<R> callback);

    /** Azúcar sintáctico para casos de uso sin parámetros. */
    public final void execute(@NonNull Callback<R> callback) {
        execute(null, callback);
    }
}
