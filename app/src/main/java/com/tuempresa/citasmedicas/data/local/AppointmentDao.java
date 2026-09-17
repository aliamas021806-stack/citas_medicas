package com.tuempresa.citasmedicas.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * DAO de citas. Devuelve LiveData para observabilidad automática en la UI.
 */
@Dao
public interface AppointmentDao {

    @Query("SELECT * FROM appointments ORDER BY date ASC, time ASC")
    LiveData<List<AppointmentEntity>> observeAll();

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

    @Query("SELECT COUNT(*) FROM appointments WHERE status = 'Confirmed'")
    int countConfirmed();
}
