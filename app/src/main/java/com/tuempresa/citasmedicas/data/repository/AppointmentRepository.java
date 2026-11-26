package com.tuempresa.citasmedicas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.tuempresa.citasmedicas.data.local.AppDatabase;
import com.tuempresa.citasmedicas.data.local.AppointmentDao;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.util.AppointmentMapper;
import com.tuempresa.citasmedicas.util.SessionManager;

import java.util.List;

/**
 * Implementación LOCAL del repositorio de citas. Persistencia con Room (SQLite).
 * <p>
 * <b>Qué cambió respecto a la versión anterior y por qué.</b>
 * <p>
 * Antes esta clase exponía {@code LiveData<List<AppointmentEntity>>}, es decir,
 * filtraba la tabla de SQLite hasta el {@code ViewModel} y la UI: la pantalla
 * "Mis Citas" importaba {@code AppointmentEntity} y leía sus campos públicos. Eso
 * significaba que la presentación dependía del esquema de la base de datos local,
 * y habría atado también la UI a Firestore en cuanto se añadiera.
 * <p>
 * Ahora:
 * <ul>
 *   <li>Devuelve {@code LiveData<List<Appointment>>} (modelos de dominio).</li>
 *   <li>La conversión la hace {@link AppointmentMapper}, la única clase que
 *       conoce a la vez entidad y modelo.</li>
 *   <li>Los identificadores viajan como {@code String}, el tipo que usa Firestore.</li>
 * </ul>
 * Room sigue siendo la fuente de verdad de la UI, así que la reactividad
 * ({@code LiveData} que emite al insertar o cancelar) se conserva intacta.
 * <p>
 * Cuando se añada Firestore, esta clase no se sustituye: se le añade un
 * sincronizador que empuje los cambios pendientes. La UI no se entera.
 */
public class AppointmentRepository implements IAppointmentRepository {

    private final AppointmentDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Id del paciente en sesión. Es la clave de filtrado: cada usuario ve sólo sus
     * citas. Si es {@code null} o vacío no se muestra ninguna (antes se mostraban
     * todas, incluidas las del sembrado de ejemplo a nombre de otra persona).
     */
    private final String ownerId;

    /**
     * Flujo observable ya mapeado a modelos.
     * <p>
     * {@link Transformations#map} es la forma idiomática de aplicar una función al
     * valor de un {@code LiveData} sin perder la reactividad: cada vez que Room
     * emite una lista de entidades, se transforma a modelos en el hilo principal.
     */
    private final LiveData<List<Appointment>> appointments;

    /**
     * Constructor con dueño: es el que usa la app.
     *
     * @param ownerId id del paciente en sesión ({@code null} = sin sesión)
     */
    public AppointmentRepository(@NonNull Context context, @Nullable String ownerId) {
        this.dao = AppDatabase.getInstance(context).appointmentDao();
        this.ownerId = ownerId != null ? ownerId : "";
        this.appointments = Transformations.map(
                dao.observeByOwner(this.ownerId), AppointmentMapper::toModelList);
    }

    /**
     * Sin dueño explícito: se resuelve el usuario de la sesión.
     * <p>
     * Se conserva para no romper las llamadas anteriores (y las pruebas).
     */
    public AppointmentRepository(@NonNull Context context) {
        this(context, SessionManager.getInstance(context).getUserId());
    }

    @NonNull
    @Override
    public LiveData<List<Appointment>> observeAll() {
        return appointments;
    }

    /** Dueño con el que se filtran las citas de esta instancia. */
    @NonNull
    public String getOwnerId() {
        return ownerId;
    }

    @Override
    public void create(@NonNull Appointment appointment,
                       @NonNull final RepositoryCallback<String> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // La cita pertenece al usuario en sesión, siempre.
                if (ownerId.isEmpty()) {
                    mainHandler.post(() -> callback.onError(
                            "No hay una sesión activa: inicia sesión para agendar"));
                    return;
                }
                appointment.setOwnerId(ownerId);
                AppointmentEntity entity = AppointmentMapper.toEntity(appointment);
                // El id lo asigna SQLite; se fuerza 0 para no reutilizar uno ajeno.
                entity.id = 0;
                entity.ownerId = ownerId;
                final long newId = dao.insert(entity);
                mainHandler.post(() -> callback.onSuccess(String.valueOf(newId)));
            } catch (RuntimeException e) {
                mainHandler.post(() ->
                        callback.onError("No se pudo agendar la cita: " + e.getMessage()));
            }
        });
    }

    @Override
    public void cancel(@NonNull String appointmentId,
                       @NonNull final RepositoryCallback<Void> callback) {
        final long id;
        try {
            id = Long.parseLong(appointmentId);
        } catch (NumberFormatException e) {
            callback.onError("La cita indicada no es válida");
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppointmentEntity cita = dao.getById(id);
                // Sólo el dueño puede cancelar su cita.
                if (cita == null || !ownerId.equals(cita.ownerId)) {
                    mainHandler.post(() ->
                            callback.onError("Esa cita no pertenece a tu cuenta"));
                    return;
                }
                dao.updateStatus(id, "Cancelled");
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (RuntimeException e) {
                mainHandler.post(() ->
                        callback.onError("No se pudo cancelar la cita: " + e.getMessage()));
            }
        });
    }

    @Override
    public void delete(@NonNull Appointment appointment,
                       @NonNull final RepositoryCallback<Void> callback) {
        final AppointmentEntity entity = AppointmentMapper.toEntity(appointment);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppointmentEntity actual = dao.getById(entity.id);
                if (actual == null || !ownerId.equals(actual.ownerId)) {
                    mainHandler.post(() ->
                            callback.onError("Esa cita no pertenece a tu cuenta"));
                    return;
                }
                dao.delete(actual);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError("No se pudo eliminar la cita"));
            }
        });
    }

    /**
     * Acceso directo al DAO para tareas de sincronización futuras.
     * <p>
     * No se usa en la UI: existe para que un futuro {@code AppointmentSyncWorker}
     * pueda leer y escribir sin pasar por {@code LiveData}. Se mantiene aquí, y no
     * expuesto en la interfaz de dominio, para no contaminar el contrato.
     */
    @NonNull
    public AppointmentDao getDaoForSync() {
        return dao;
    }
}
