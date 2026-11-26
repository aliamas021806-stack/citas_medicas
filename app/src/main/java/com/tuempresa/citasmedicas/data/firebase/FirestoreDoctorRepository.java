package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Repositorio de catálogo médico contra <b>Cloud Firestore</b> (REST).
 * <p>
 * Implementa {@link IDoctorRepository}, el mismo contrato que la versión mock. Por
 * eso {@code DoctorViewModel}, los casos de uso y las pantallas <b>no cambian</b>:
 * sólo cambia qué devuelve {@code RepositoryProvider}.
 * <p>
 * <b>Los horarios siguen siendo locales.</b> Las franjas disponibles son un cálculo
 * de agenda que hoy genera {@code MockData}; moverlas a Firestore exigiría modelar
 * la ocupación por doctor. Se mantienen locales para no romper el flujo de reserva
 * ya probado, y quedan como mejora futura documentada.
 * <p>
 * <b>Errores.</b> Si Firestore no está accesible o las reglas de seguridad bloquean
 * la lectura, se informa por el callback con un mensaje en español. El
 * {@code RepositoryProvider} puede entonces caer al modo mock, así que la app nunca
 * se queda en blanco.
 */
public class FirestoreDoctorRepository implements IDoctorRepository {

    private final FirestoreApi api;
    /** Reserva local para los horarios (no se migran a Firestore en esta fase). */
    private final IDoctorRepository slotsFallback;

    /**
     * @param slotsFallback repositorio que resuelve los horarios disponibles
     * @param context       contexto de aplicación. Se necesita para que las peticiones
     *                      viajen con el {@code idToken} del usuario (ver
     *                      {@code AuthHeaderInterceptor}): sin él, la lectura sólo
     *                      funcionaría con reglas públicas.
     */
    public FirestoreDoctorRepository(@NonNull IDoctorRepository slotsFallback,
                                     @NonNull android.content.Context context) {
        this.api = FirestoreClient.getApi(context.getApplicationContext());
        this.slotsFallback = slotsFallback;
    }

    /**
     * Constructor sin contexto.
     * <p>
     * Crea el cliente sin credenciales de usuario, así que con reglas endurecidas
     * ({@code if request.auth != null}) las peticiones serán rechazadas. Se conserva
     * sólo por compatibilidad: el código de producción usa el constructor con
     * {@code Context}.
     *
     * @deprecated usar {@link #FirestoreDoctorRepository(IDoctorRepository, android.content.Context)}
     */
    @Deprecated
    public FirestoreDoctorRepository(@NonNull IDoctorRepository slotsFallback) {
        this.api = FirestoreClient.getApi();
        this.slotsFallback = slotsFallback;
    }

    @Override
    public void getSpecialties(@NonNull final RepositoryCallback<List<Specialty>> callback) {
        api.listDocuments(FirebaseConfig.COLLECTION_SPECIALTIES)
                .enqueue(new retrofit2.Callback<FirestoreApi.DocumentList>() {
                    @Override
                    public void onResponse(@NonNull Call<FirestoreApi.DocumentList> call,
                                           @NonNull Response<FirestoreApi.DocumentList> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Specialty> lista =
                                    FirestoreMapper.toSpecialties(response.body().documents);
                            if (lista.isEmpty()) {
                                callback.onError("La colección 'specialties' está vacía en Firestore");
                                return;
                            }
                            callback.onSuccess(lista);
                        } else {
                            callback.onError(mensajeDeError(response.code(), "especialidades",
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.DocumentList> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    @Override
    public void getDoctors(@NonNull final RepositoryCallback<List<Doctor>> callback) {
        api.listDocuments(FirebaseConfig.COLLECTION_DOCTORS)
                .enqueue(new retrofit2.Callback<FirestoreApi.DocumentList>() {
                    @Override
                    public void onResponse(@NonNull Call<FirestoreApi.DocumentList> call,
                                           @NonNull Response<FirestoreApi.DocumentList> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Doctor> lista =
                                    FirestoreMapper.toDoctors(response.body().documents);
                            if (lista.isEmpty()) {
                                callback.onError("La colección 'doctors' está vacía en Firestore");
                                return;
                            }
                            callback.onSuccess(lista);
                        } else {
                            callback.onError(mensajeDeError(response.code(), "doctores",
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.DocumentList> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    /**
     * Doctores de una especialidad.
     * <p>
     * Se filtra <b>en memoria</b> tras traer la colección completa: así se evita
     * necesitar un índice compuesto en Firestore, que es la causa más habitual de
     * que una app "no muestre datos" sin error visible.
     */
    @Override
    public void getDoctorsBySpecialty(@NonNull final String specialtyId,
                                      @NonNull final RepositoryCallback<List<Doctor>> callback) {
        getDoctors(new RepositoryCallback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> data) {
                if (specialtyId == null || specialtyId.trim().isEmpty()) {
                    callback.onSuccess(data);
                    return;
                }
                List<Doctor> filtrados = new ArrayList<>();
                if (data != null) {
                    for (Doctor d : data) {
                        if (specialtyId.equals(d.getSpecialtyId())) {
                            filtrados.add(d);
                        }
                    }
                }
                callback.onSuccess(filtrados);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void getDoctorById(@NonNull String doctorId,
                              @NonNull final RepositoryCallback<Doctor> callback) {
        api.getDocument(FirebaseConfig.COLLECTION_DOCTORS, doctorId)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Doctor d = FirestoreMapper.toDoctor(response.body());
                            if (d == null) {
                                callback.onError("No se encontró el doctor solicitado");
                                return;
                            }
                            callback.onSuccess(d);
                        } else {
                            callback.onError(mensajeDeError(response.code(), "doctor",
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    /** Los horarios se delegan: no se migran a Firestore en esta fase. */
    @Override
    public void getAvailableSlots(@NonNull String doctorId,
                                  @NonNull RepositoryCallback<List<TimeSlot>> callback) {
        if (slotsFallback != null) {
            slotsFallback.getAvailableSlots(doctorId, callback);
        } else {
            callback.onSuccess(Collections.emptyList());
        }
    }

    // ───────────────────────── Errores ─────────────────────────

    /**
     * Mensaje de error a partir del código HTTP y del cuerpo real de la respuesta.
     * <p>
     * Se lee {@code errorBody()} porque en un HTTP 4xx/5xx Retrofit deja
     * {@code body()} a {@code null} y el motivo concreto de Google ({@code
     * "The database (default) does not exist..."}, {@code "Missing or insufficient
     * permissions"}) sólo está en ese cuerpo. Sin esto el usuario vería un "404" o
     * "403" sin explicación.
     */
    @NonNull
    private String mensajeDeError(int httpCode, @NonNull String recurso,
                                  @Nullable String jsonError) {
        // Primero, el motivo real que da Google (base sin crear, reglas, colección).
        String detalle = FirebaseErrors.describirErrorFirestore(httpCode, jsonError);
        if (!detalle.isEmpty() && !detalle.equals(jsonError == null ? "" : jsonError.trim())) {
            return detalle;
        }

        switch (httpCode) {
            case 400:
                return "La consulta a Firestore no es válida (400). Revisa los nombres de campo";
            case 401:
            case 403:
                return "Firestore ha rechazado la lectura (" + httpCode
                        + "). Revisa las Reglas de seguridad de la colección de " + recurso;
            case 404:
                return "No se encontró la colección de " + recurso + " en Firestore (404)";
            case 429:
                return "Demasiadas peticiones a Firestore (429). Espera unos segundos";
            default:
                return "Firestore respondió con HTTP " + httpCode + " al leer " + recurso;
        }
    }

    @NonNull
    private String mensajeDeRed(@NonNull Throwable t) {
        String causa = t.getMessage() != null ? t.getMessage() : "";
        if (causa.contains("Unable to resolve host") || causa.contains("UnknownHost")) {
            return "Sin conexión a internet: no se pudo contactar con Firestore";
        }
        return "Error de conexión con Firestore: " + causa;
    }
}
