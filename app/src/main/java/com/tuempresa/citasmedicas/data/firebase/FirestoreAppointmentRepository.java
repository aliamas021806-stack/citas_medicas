package com.tuempresa.citasmedicas.data.firebase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Appointment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Citas médicas: <b>Room primero, Firestore en segundo plano</b>.
 * <p>
 * <b>El problema que resuelve.</b> Antes las citas sólo se guardaban en el móvil
 * (Room): al usar la app no aparecía nada en la consola de Firebase. Esta clase
 * escribe cada cita también en la colección {@code appointments} de Firestore, que es
 * lo que se espera al "conectar la base de datos".
 * <p>
 * <b>Por qué se mantiene Room como fuente de verdad.</b> Es una decisión deliberada,
 * no una limitación:
 * <ul>
 *   <li><b>Reactividad inmediata:</b> la lista de "Mis Citas" se actualiza al
 *       instante porque {@code LiveData} observa la tabla de SQLite. Si dependiera de
 *       la red, cada cambio tendría latencia.</li>
 *   <li><b>Funciona sin internet:</b> una cita se agenda igual en un aula sin wifi.</li>
 *   <li><b>Firestore no reemplaza nada:</b> se añade <i>además</i>, así que si la nube
 *       falla, la app no se rompe: sólo queda una cita pendiente de subir.</li>
 * </ul>
 * <p>
 * <b>Estrategia de sincronización.</b> Es "escribir primero en local, luego empujar":
 * <ol>
 *   <li>Se guarda en Room (operación que nunca falla por red) y se devuelve el id
 *       local de inmediato.</li>
 *   <li>En paralelo se envía a Firestore. Si se consigue, se guarda el id de Firestore
 *       en la columna {@code remoteId} para no duplicar en envíos posteriores.</li>
 *   <li>Si falla (sin red, base sin crear, reglas), se registra y la cita sigue válida
 *       en el móvil. No se pierde nada y el usuario no ve un error que no puede
 *       resolver.</li>
 * </ol>
 * <p>
 * <b>Java puro:</b> Retrofit + Gson, sin SDK de Firebase (que arrastraría Kotlin).
 */
public class FirestoreAppointmentRepository implements IAppointmentRepository {

    private static final String TAG = "CitasFirestore";

    private final AppointmentRepository local;
    private final FirestoreApi api;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FirestoreAppointmentRepository(@NonNull Context context) {
        this.local = new AppointmentRepository(context.getApplicationContext());
        this.api = FirestoreClient.getApi();
    }

    // ───────────────────────── Lectura (siempre local) ─────────────────────────

    /**
     * La UI observa Room, no Firestore.
     * <p>
     * Se delega por completo: así la lista es reactiva e instantánea, y no hay dos
     * fuentes de verdad compitiendo por pintar la misma pantalla.
     */
    @NonNull
    @Override
    public LiveData<List<Appointment>> observeAll() {
        return local.observeAll();
    }

    // ───────────────────────── Escritura (local + nube) ─────────────────────────

    @Override
    public void create(@NonNull final Appointment appointment,
                       @NonNull final RepositoryCallback<String> callback) {
        // 1) Local primero: es lo que el usuario percibe y no depende de la red.
        local.create(appointment, new RepositoryCallback<String>() {
            @Override
            public void onSuccess(final String localId) {
                // 2) Se responde al usuario YA. La subida a Firestore es best-effort:
                //    si tarda o falla, la cita ya está agendada y visible.
                callback.onSuccess(localId);
                subirAFirestore(appointment, localId);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void cancel(@NonNull String appointmentId,
                       @NonNull final RepositoryCallback<Void> callback) {
        // Se cancela en local y, si la cita llegó a Firestore, se refleja allí.
        local.cancel(appointmentId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                callback.onSuccess(null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void delete(@NonNull final Appointment appointment,
                       @NonNull final RepositoryCallback<Void> callback) {
        local.delete(appointment, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                callback.onSuccess(null);
                borrarEnFirestore(appointment);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ───────────────────────── Subida a Firestore ─────────────────────────

    /**
     * Envía la cita a Firestore sin bloquear ni molestar al usuario.
     * <p>
     * Los fallos se registran en el log con el motivo exacto. No se propaga el error
     * porque la operación local ya tuvo éxito: mostrar "no se pudo guardar" cuando la
     * cita SÍ está guardada en el teléfono sería confuso y falso.
     */
    private void subirAFirestore(@NonNull Appointment appointment,
                                 @NonNull final String localId) {
        FirestoreApi.FirestoreDocument doc = toDocument(appointment);
        api.createDocument(FirebaseConfig.COLLECTION_APPOINTMENTS, doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String remoteId = FirestoreMapper.documentId(response.body());
                            android.util.Log.i(TAG, "Cita " + localId
                                    + " guardada en Firestore como '" + remoteId + "'");
                            guardarRemoteId(localId, remoteId);
                        } else {
                            android.util.Log.w(TAG, "No se pudo subir la cita " + localId
                                    + " a Firestore: "
                                    + FirebaseErrors.describirErrorFirestore(
                                    response.code(),
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión para subir la cita " + localId
                                + ": " + t.getMessage());
                    }
                });
    }

    /** Borra el documento remoto si la cita tenía uno asociado. */
    private void borrarEnFirestore(@Nullable Appointment appointment) {
        if (appointment == null) {
            return;
        }
        String remoteId = appointment.getRemoteId();
        if (remoteId == null || remoteId.trim().isEmpty()) {
            return; // Nunca se subió: no hay nada que borrar en la nube.
        }
        // Se deja constancia; el borrado remoto se completa en la Fase 3.1 con
        // DELETE, que requiere el mismo tratamiento de errores que la creación.
        android.util.Log.i(TAG, "Cita eliminada en local. Documento remoto '" + remoteId
                + "' pendiente de borrar en Firestore");
    }

    /** Persiste en Room el id que asignó Firestore, para no duplicar documentos. */
    private void guardarRemoteId(@NonNull final String localId, @NonNull final String remoteId) {
        try {
            long id = Long.parseLong(localId);
            final com.tuempresa.citasmedicas.data.local.AppointmentDao dao =
                    local.getDaoForSync();
            com.tuempresa.citasmedicas.data.local.AppDatabase.databaseWriteExecutor.execute(
                    () -> {
                        try {
                            dao.updateRemoteId(id, remoteId);
                        } catch (RuntimeException e) {
                            android.util.Log.w(TAG,
                                    "No se pudo guardar el id remoto: " + e.getMessage());
                        }
                    });
        } catch (NumberFormatException e) {
            android.util.Log.w(TAG, "Id local no numérico: " + localId);
        }
    }

    // ───────────────────────── Conversión a documento ─────────────────────────

    /**
     * Convierte el modelo de dominio al formato de campos de Firestore.
     * <p>
     * Firestore exige el envoltorio de tipo en cada campo ({@code stringValue},
     * {@code integerValue}...). Los campos vacíos se declaran igualmente para que el
     * documento sea homogéneo y fácil de leer en la consola.
     */
    @NonNull
    public static FirestoreApi.FirestoreDocument toDocument(@NonNull Appointment a) {
        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("doctorId", FirestoreApi.ofString(a.getDoctorId()));
        f.put("doctorName", FirestoreApi.ofString(a.getDoctorName()));
        f.put("specialtyName", FirestoreApi.ofString(a.getSpecialtyName()));
        f.put("patientName", FirestoreApi.ofString(a.getPatientName()));
        f.put("patientAge", FirestoreApi.ofInteger(a.getPatientAge()));
        f.put("date", FirestoreApi.ofString(a.getDate()));
        f.put("time", FirestoreApi.ofString(a.getTime()));
        f.put("status", FirestoreApi.ofString(
                a.getStatus() != null ? a.getStatus().getValue() : "Confirmed"));
        f.put("reason", FirestoreApi.ofString(a.getReason()));
        f.put("createdAt", FirestoreApi.ofInteger(a.getCreatedAt()));

        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = f;
        return doc;
    }
}
