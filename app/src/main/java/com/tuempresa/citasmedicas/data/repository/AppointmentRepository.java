package com.tuempresa.citasmedicas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.tuempresa.citasmedicas.data.local.AppDatabase;
import com.tuempresa.citasmedicas.data.local.AppointmentDao;
import com.tuempresa.citasmedicas.data.local.AppointmentEntity;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.util.AppointmentMapper;

import java.util.List;

/**
 * Repositorio de citas médicas. Persistencia local con Room (SQLite).
 */
public class AppointmentRepository {

    private final AppointmentDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AppointmentRepository(Context context) {
        this.dao = AppDatabase.getInstance(context).appointmentDao();
    }

    public LiveData<List<AppointmentEntity>> observeAll() {
        return dao.observeAll();
    }

    public void create(Appointment appointment, RepositoryCallback<Long> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppointmentEntity entity = AppointmentMapper.toEntity(appointment);
                entity.id = 0;
                long newId = dao.insert(entity);
                mainHandler.post(() -> callback.onSuccess(newId));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("No se pudo agendar la cita: " + e.getMessage()));
            }
        });
    }

    public void cancel(long appointmentId, RepositoryCallback<Void> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                dao.updateStatus(appointmentId, "Cancelled");
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("No se pudo cancelar la cita: " + e.getMessage()));
            }
        });
    }

    public void delete(AppointmentEntity entity, RepositoryCallback<Void> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                dao.delete(entity);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("No se pudo eliminar la cita"));
            }
        });
    }
}
