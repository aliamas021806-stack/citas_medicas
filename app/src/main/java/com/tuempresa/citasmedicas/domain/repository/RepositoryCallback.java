package com.tuempresa.citasmedicas.domain.repository;

/**
 * Callback simple para operaciones asíncronas de los repositorios.
 * <p>
 * Vive en {@code domain} y no en {@code data} a propósito: es el contrato que el
 * dominio usa para recibir resultados, así que la capa de negocio no necesita
 * conocer la capa de datos. Cualquier implementación (Room, Retrofit, Firestore)
 * puede invocarlo.
 *
 * @param <T> tipo del dato entregado en caso de éxito
 */
public interface RepositoryCallback<T> {

    void onSuccess(T data);

    void onError(String message);
}
