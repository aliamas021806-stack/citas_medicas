package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.data.local.AppDatabase;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.util.AppointmentMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Agenda de un médico: las citas que le han programado.
 * <p>
 * <b>Doble fuente, a propósito.</b>
 * <ul>
 *   <li><b>Firestore</b> es la fuente principal: las citas que agendaron los pacientes
 *       pueden haberse creado en otro teléfono, así que no están en la base local de
 *       este dispositivo.</li>
 *   <li><b>Room</b> actúa de respaldo: si no hay red, el médico al menos ve las citas
 *       que sí se guardaron en su propio teléfono.</li>
 * </ul>
 * El filtrado se hace por {@code doctorId}, que es el campo que ya existía en las
 * citas; no hubo que migrar los documentos de Firestore.
 * <p>
 * <b>Sólo una igualdad.</b> La consulta usa una única condición para no necesitar un
 * índice compuesto: con {@code where(doctorId == X).orderBy(date)} Firestore exige
 * crear un índice a mano y, mientras no exista, la consulta falla con un 400. Por eso
 * la fecha y la hora se ordenan en memoria.
 */
public class FirestoreDoctorAgendaRepository {

    private static final String TAG = "AgendaMedico";

    private final FirestoreApi api;
    private final AppDatabase db;

    public FirestoreDoctorAgendaRepository(@NonNull android.content.Context context) {
        this.api = FirestoreClient.getApi(context.getApplicationContext());
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    /** Resultado de la consulta de agenda. */
    public interface AgendaCallback {
        /**
         * @param citas  citas del médico, ordenadas por fecha y hora
         * @param deNube {@code true} si vienen de Firestore, {@code false} si son el
         *               respaldo local
         */
        void onAgenda(@NonNull List<Appointment> citas, boolean deNube);

        void onError(@NonNull String mensaje);
    }

    /**
     * Carga todas las citas del médico.
     * <p>
     * Se filtra por {@code doctorId} con una igualdad simple: no requiere índice
     * compuesto. El orden por fecha se hace en memoria justamente para evitarlo.
     */
    public void cargarAgenda(@NonNull String doctorId,
                             @NonNull final AgendaCallback callback) {
        if (doctorId == null || doctorId.trim().isEmpty()) {
            callback.onError("Esta cuenta no está vinculada a ningún médico");
            return;
        }

        FirestoreApi.StructuredQuery query =
                new FirestoreApi.StructuredQuery(FirebaseConfig.COLLECTION_APPOINTMENTS);
        query.where = igualdad("doctorId", doctorId);

        api.runQuery(FirebaseConfig.COLLECTION_APPOINTMENTS,
                        new FirestoreApi.QueryRequest(query))
                .enqueue(new retrofit2.Callback<List<FirestoreApi.QueryResult>>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<List<FirestoreApi.QueryResult>> call,
                            @NonNull Response<List<FirestoreApi.QueryResult>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Appointment> citas = leerDocumentos(response.body());
                            ordenarPorFecha(citas);
                            if (!citas.isEmpty()) {
                                callback.onAgenda(citas, true);
                                return;
                            }
                            // Firestore respondió pero sin resultados: puede ser que aún
                            // no tenga citas o que las tenga sólo en este dispositivo.
                            callback.onAgenda(desdeRoom(doctorId), false);
                        } else {
                            // Sin permisos o error del servidor: se usa el respaldo.
                            android.util.Log.w(TAG, "Firestore respondió HTTP "
                                    + response.code() + " al leer la agenda; se usa Room");
                            callback.onAgenda(desdeRoom(doctorId), false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<FirestoreApi.QueryResult>> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión para leer la agenda: "
                                + t.getMessage());
                        callback.onAgenda(desdeRoom(doctorId), false);
                    }
                });
    }

    // ───────────────────────── Respaldo local ─────────────────────────

    /**
     * Citas guardadas en este dispositivo para el médico.
     * <p>
     * Se leen de forma síncrona porque el llamador ya está en un hilo de respuesta de
     * Retrofit; el volumen es pequeño (la agenda de un día).
     */
    @NonNull
    private List<Appointment> desdeRoom(@NonNull String doctorId) {
        List<Appointment> resultado = new ArrayList<>();
        try {
            for (AppointmentEntity e : db.appointmentDao().getAllOnce()) {
                if (doctorId.equals(e.doctorId)) {
                    Appointment cita = AppointmentMapper.toModel(e);
                    if (cita != null) {
                        resultado.add(cita);
                    }
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "No se pudo leer el respaldo local: " + e.getMessage());
        }
        ordenarPorFecha(resultado);
        return resultado;
    }

    // ───────────────────────── Cambio de estado ─────────────────────────

    /** Resultado del cambio de estado de una cita. */
    public interface EstadoCallback {
        void onOk();

        void onError(@NonNull String mensaje);
    }

    /**
     * Marca una cita como atendida (o como no acudió).
     * <p>
     * <b>Sólo el estado.</b> Se envía un {@code PATCH} con {@code updateMask} limitado
     * a {@code status}: sin la máscara, Firestore reemplazaría el documento entero y se
     * perderían el paciente, la fecha y el motivo. La regla de seguridad también lo
     * exige así, sólo permite cambiar {@code status} y {@code reason}.
     * <p>
     * Si la cita no tiene documento remoto (se agendó sin red y nunca subió), se
     * actualiza sólo en Room: el estado local sigue siendo útil para el consultorio.
     */
    public void cambiarEstado(@NonNull Appointment cita, @NonNull AppointmentStatus nuevo,
                              @NonNull final EstadoCallback callback) {
        final String remoteId = cita.getRemoteId();
        final boolean hayRemoto = remoteId != null && !remoteId.trim().isEmpty();

        if (!hayRemoto) {
            actualizarEstadoLocal(cita, nuevo, callback);
            return;
        }

        Map<String, FirestoreApi.Value> campos = new HashMap<>();
        campos.put("status", FirestoreApi.ofString(nuevo.getValue()));

        FirestoreApi.FirestoreDocument parcial = new FirestoreApi.FirestoreDocument();
        parcial.fields = campos;

        api.updateDocument(FirebaseConfig.COLLECTION_APPOINTMENTS, remoteId,
                        Collections.singletonList("status"), parcial)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            actualizarEstadoLocal(cita, nuevo, callback);
                        } else {
                            android.util.Log.w(TAG, "No se pudo marcar la cita '" + remoteId
                                    + "' como " + nuevo.getValue() + ": "
                                    + FirebaseErrors.describirErrorFirestore(
                                    response.code(), FirebaseErrors.cuerpoDeError(response)));
                            callback.onError("Firestore rechazó el cambio (HTTP "
                                    + response.code() + ")");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión al cambiar el estado de '"
                                + remoteId + "': " + t.getMessage());
                        callback.onError("Sin conexión: el cambio no se guardó");
                    }
                });
    }

    /** Refleja el estado en Room, que es la caché local del consultorio. */
    private void actualizarEstadoLocal(@NonNull Appointment cita,
                                       @NonNull AppointmentStatus nuevo,
                                       @NonNull final EstadoCallback callback) {
        final long idLocal;
        try {
            idLocal = Long.parseLong(cita.getId() != null ? cita.getId() : "");
        } catch (NumberFormatException e) {
            // El id visible es el remoto: no hay fila local que tocar, pero la nube ya
            // está actualizada, así que la operación se da por buena.
            callback.onOk();
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.appointmentDao().updateStatus(idLocal, nuevo.getValue());
                callback.onOk();
            } catch (RuntimeException e) {
                android.util.Log.w(TAG, "No se pudo guardar el estado en Room: "
                        + e.getMessage());
                callback.onOk();
            }
        });
    }

    // ───────────────────────── Utilidades ─────────────────────────

    /**
     * Construye un filtro de igualdad simple ({@code campo == valor}).
     * <p>
     * <b>Forma exacta que exige Firestore.</b> Un filtro de una sola condición no se
     * envía como {@code fieldFilter} suelto sino envuelto en un {@code compositeFilter}
     * con {@code op = "AND"} y una lista de un elemento. Enviar sólo el
     * {@code fieldFilter} devuelve un 400 con
     * <i>"Invalid query... unknown field"</i> y la consulta no devuelve nada, que es el
     * fallo silencioso más habitual.
     */
    @NonNull
    static FirestoreApi.CompositeFilter igualdad(@NonNull String campo,
                                                 @NonNull String valor) {
        FirestoreApi.FieldFilterBody condicion = new FirestoreApi.FieldFilterBody(
                campo, "EQUAL", FirestoreApi.ofString(valor));

        FirestoreApi.FieldFilter filtro = new FirestoreApi.FieldFilter(condicion);

        FirestoreApi.CompositeFilterBody cuerpo = new FirestoreApi.CompositeFilterBody(
                Collections.singletonList(filtro));
        cuerpo.op = "AND";

        return new FirestoreApi.CompositeFilter(cuerpo);
    }

    /** Convierte los documentos devueltos por {@code runQuery} en modelos. */
    @NonNull
    List<Appointment> leerDocumentos(@NonNull List<FirestoreApi.QueryResult> resultados) {
        List<Appointment> citas = new ArrayList<>();
        for (FirestoreApi.QueryResult r : resultados) {
            if (r == null || r.document == null) {
                continue;
            }
            Appointment cita = FirestoreAppointmentMapper.toAppointment(r.document);
            if (cita != null) {
                citas.add(cita);
            }
        }
        return citas;
    }

    /**
     * Ordena por fecha y hora.
     * <p>
     * Se hace en memoria y no con {@code orderBy} para no necesitar un índice
     * compuesto (que habría que crear a mano en la consola y bloquea la consulta
     * mientras no existe).
     */
    static void ordenarPorFecha(@NonNull List<Appointment> citas) {
        Collections.sort(citas, FirestoreDoctorAgendaRepository::compararFechaYHora);
    }

    /**
     * Citas de hoy.
     *
     * @param hoy fecha en formato {@code yyyy-MM-dd}, el mismo que usa la app al agendar
     */
    @NonNull
    public static List<Appointment> deHoy(@NonNull List<Appointment> citas,
                                          @NonNull String hoy) {
        List<Appointment> resultado = new ArrayList<>();
        for (Appointment a : citas) {
            if (hoy.equals(a.getDate())) {
                resultado.add(a);
            }
        }
        return resultado;
    }

    /** Citas activas (no canceladas) de una lista. */
    @NonNull
    public static List<Appointment> activas(@NonNull List<Appointment> citas) {
        List<Appointment> resultado = new ArrayList<>();
        for (Appointment a : citas) {
            if (a.getStatus() != AppointmentStatus.CANCELLED) {
                resultado.add(a);
            }
        }
        return resultado;
    }

    /**
     * Citas que <b>faltan por atender</b>, con las de hoy primero.
     * <p>
     * Es el grupo que el médico necesita ver al abrir la agenda, así que se ordena por
     * prioridad y no sólo por hora:
     * <ol>
     *   <li>Primero las del día que se está mirando.</li>
     *   <li>Dentro de ese día, por hora ascendente: la siguiente consulta es la que
     *       apremia.</li>
     * </ol>
     * Se incluye {@link AppointmentStatus#NO_SHOW} porque una inasistencia también
     * exige una decisión (reprogramar o cerrar el hueco).
     */
    @NonNull
    public static List<Appointment> pendientes(@NonNull List<Appointment> citas,
                                               @NonNull String dia) {
        List<Appointment> resultado = new ArrayList<>();
        for (Appointment a : citas) {
            AppointmentStatus estado = a.getStatus() != null
                    ? a.getStatus() : AppointmentStatus.CONFIRMED;
            if (estado.esPendiente()) {
                resultado.add(a);
            }
        }
        Collections.sort(resultado, (a, b) -> {
            // El día consultado va primero; el resto mantiene el orden cronológico.
            boolean aEsDeHoy = dia.equals(a.getDate());
            boolean bEsDeHoy = dia.equals(b.getDate());
            if (aEsDeHoy != bEsDeHoy) {
                return aEsDeHoy ? -1 : 1;
            }
            return compararFechaYHora(a, b);
        });
        return resultado;
    }

    /**
     * Citas <b>ya atendidas</b> del día consultado, ordenadas por hora.
     * <p>
     * Se mantiene el histórico del día pero en un grupo aparte, para que no desplace a
     * lo que queda por hacer. Firestore no admite la consulta por estado sin un índice
     * compuesto, así que el filtro se hace en memoria (igual que la fecha).
     */
    @NonNull
    public static List<Appointment> atendidas(@NonNull List<Appointment> citas,
                                              @NonNull String dia) {
        List<Appointment> resultado = new ArrayList<>();
        for (Appointment a : citas) {
            AppointmentStatus estado = a.getStatus() != null
                    ? a.getStatus() : AppointmentStatus.CONFIRMED;
            if (estado.esAtendida() && dia.equals(a.getDate())) {
                resultado.add(a);
            }
        }
        Collections.sort(resultado, FirestoreDoctorAgendaRepository::compararFechaYHora);
        return resultado;
    }

    /** Compara por fecha y, si coincide, por hora. Orden cronológico ascendente. */
    private static int compararFechaYHora(@NonNull Appointment a, @NonNull Appointment b) {
        String fa = a.getDate() != null ? a.getDate() : "";
        String fb = b.getDate() != null ? b.getDate() : "";
        int cmp = fa.compareTo(fb);
        if (cmp != 0) {
            return cmp;
        }
        String ta = a.getTime() != null ? a.getTime() : "";
        String tb = b.getTime() != null ? b.getTime() : "";
        return ta.compareTo(tb);
    }
}
