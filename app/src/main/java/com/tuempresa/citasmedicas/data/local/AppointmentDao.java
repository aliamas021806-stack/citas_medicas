package com.tuempresa.citasmedicas.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * DAO de citas. Devuelve LiveData para observabilidad automática en la UI.
 * <p>
 * Todas las consultas de lectura están <b>filtradas por dueño</b> ({@code owner_id}):
 * cada paciente ve únicamente sus citas. Antes la tabla se leía completa y un usuario
 * recién creado veía las citas de otro paciente.
 */
@Dao
public interface AppointmentDao {

    /** Citas de un paciente concreto, ordenadas por fecha y hora. */
    @Query("SELECT * FROM appointments WHERE owner_id = :ownerId "
            + "ORDER BY date ASC, time ASC")
    LiveData<List<AppointmentEntity>> observeByOwner(String ownerId);

    /** Versión puntual (sin LiveData) del listado de un paciente. */
    @Query("SELECT * FROM appointments WHERE owner_id = :ownerId "
            + "ORDER BY date ASC, time ASC")
    List<AppointmentEntity> getByOwnerOnce(String ownerId);

    /** Todas las citas, sin filtrar. Reservado para tareas de sincronización. */
    @Query("SELECT * FROM appointments ORDER BY date ASC, time ASC")
    List<AppointmentEntity> getAllOnce();

    @Query("SELECT * FROM appointments WHERE id = :id LIMIT 1")
    AppointmentEntity getById(long id);

    @Insert
    long insert(AppointmentEntity appointment);

    @Delete
    void delete(AppointmentEntity appointment);

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    void updateStatus(long id, String status);

    /**
     * Guarda el id que asignó Firestore al documento de esta cita.
     * <p>
     * Se usa después de subirla, para no volver a crearla (duplicaría la cita en la
     * nube) y para poder borrarla allí si se elimina aquí.
     */
    @Query("UPDATE appointments SET remote_id = :remoteId WHERE id = :id")
    void updateRemoteId(long id, String remoteId);

    /** Citas confirmadas de un paciente (para el contador del perfil). */
    @Query("SELECT COUNT(*) FROM appointments WHERE status = 'Confirmed' "
            + "AND owner_id = :ownerId")
    int countConfirmedByOwner(String ownerId);

    /**
     * Citas confirmadas en total, sin filtrar por paciente.
     * <p>
     * Se conserva por compatibilidad con el código y las pruebas anteriores.
     * Para la UI hay que usar {@link #countConfirmedByOwner(String)}: el contador
     * del perfil debe mostrar sólo las citas del usuario en sesión.
     */
    @Query("SELECT COUNT(*) FROM appointments WHERE status = 'Confirmed'")
    int countConfirmed();
}
