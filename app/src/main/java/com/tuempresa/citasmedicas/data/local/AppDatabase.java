package com.tuempresa.citasmedicas.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base de datos local Room (SQLite) para las citas médicas.
 */
@Database(entities = {AppointmentEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AppointmentDao appointmentDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "citas_medicas.db")
                            .fallbackToDestructiveMigration()
                            .addCallback(SEED_CALLBACK)
                            .build();
                    // El seed se pide AQUÍ, cuando INSTANCE ya está asignado.
                    // Antes se lanzaba desde onCreate(), que puede dispararse antes de
                    // que la asignación termine: entonces INSTANCE era null dentro del
                    // executor y el seed moría con NullPointerException, dejando la
                    // base vacía y "Mis Citas" en blanco.
                    seedIfEmpty();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Inserta las citas de ejemplo sólo si la base está vacía.
     * <p>
     * Es idempotente: si el usuario ya tiene citas (o las borró a propósito) no
     * vuelve a sembrar.
     */
    private static void seedIfEmpty() {
        final AppDatabase db = INSTANCE;
        if (db == null) {
            return;
        }
        databaseWriteExecutor.execute(() -> {
            try {
                AppointmentDao dao = db.appointmentDao();
                if (!dao.getAllOnce().isEmpty()) {
                    return;
                }
                java.util.List<String> dates =
                        com.tuempresa.citasmedicas.data.mock.MockData.getSelectableDates();
                if (dates.size() < 4) {
                    // Sin fechas suficientes no se puede sembrar sin salir de rango.
                    return;
                }
                for (AppointmentEntity cita : demoAppointments(dates)) {
                    dao.insert(cita);
                }
            } catch (RuntimeException e) {
                // Un fallo al sembrar nunca debe tumbar el arranque de la app.
                if (android.util.Log.isLoggable("AppDatabase", android.util.Log.WARN)) {
                    android.util.Log.w("AppDatabase", "No se pudo sembrar la base", e);
                }
            }
        });
    }

    /** Citas de ejemplo usadas la primera vez que se crea la base. */
    private static java.util.List<AppointmentEntity> demoAppointments(
            java.util.List<String> dates) {
        java.util.List<AppointmentEntity> lista = new java.util.ArrayList<>(3);
        lista.add(demoCita("doc_01", "Dr. Carlos Ramírez", "Cardiología",
                dates.get(1), "10:00", "Confirmed", "Chequeo cardiológico anual"));
        lista.add(demoCita("doc_02", "Dra. María González", "Pediatría",
                dates.get(3), "16:00", "Confirmed", "Control de crecimiento"));
        lista.add(demoCita("doc_03", "Dra. Andrea López", "Dermatología",
                dates.get(0), "09:00", "Cancelled", "Revisión de lunar"));
        return lista;
    }

    private static AppointmentEntity demoCita(String doctorId, String doctorName,
                                              String specialtyName, String date,
                                              String time, String status, String reason) {
        AppointmentEntity a = new AppointmentEntity();
        a.doctorId = doctorId;
        a.doctorName = doctorName;
        a.specialtyName = specialtyName;
        a.patientName = "Laura Jiménez";
        a.patientAge = 34;
        a.date = date;
        a.time = time;
        a.status = status;
        a.reason = reason;
        a.createdAt = System.currentTimeMillis();
        return a;
    }

    /**
     * Callback vacío a propósito: mantiene el enganche de Room por si en el futuro
     * hace falta reaccionar a onCreate/onOpen, pero YA NO siembra datos (lo hace
     * {@link #seedIfEmpty()} cuando la instancia está disponible).
     */
    private static final RoomDatabase.Callback SEED_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(SupportSQLiteDatabase db) {
            super.onCreate(db);
        }
    };
}
