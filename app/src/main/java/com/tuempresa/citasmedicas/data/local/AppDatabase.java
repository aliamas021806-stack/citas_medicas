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
                }
            }
        }
        return INSTANCE;
    }

    /** Inserta citas de ejemplo la primera vez que se crea la base de datos. */
    private static final RoomDatabase.Callback SEED_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                AppointmentDao dao = INSTANCE.appointmentDao();
                java.util.List<String> dates =
                        com.tuempresa.citasmedicas.data.mock.MockData.getSelectableDates();

                AppointmentEntity a1 = new AppointmentEntity();
                a1.doctorId = "doc_01";
                a1.doctorName = "Dr. Carlos Ramírez";
                a1.specialtyName = "Cardiología";
                a1.patientName = "Laura Jiménez";
                a1.patientAge = 34;
                a1.date = dates.get(1);
                a1.time = "10:00";
                a1.status = "Confirmed";
                a1.reason = "Chequeo cardiológico anual";
                a1.createdAt = System.currentTimeMillis();
                dao.insert(a1);

                AppointmentEntity a2 = new AppointmentEntity();
                a2.doctorId = "doc_02";
                a2.doctorName = "Dra. María González";
                a2.specialtyName = "Pediatría";
                a2.patientName = "Laura Jiménez";
                a2.patientAge = 34;
                a2.date = dates.get(3);
                a2.time = "16:00";
                a2.status = "Confirmed";
                a2.reason = "Control de crecimiento";
                a2.createdAt = System.currentTimeMillis();
                dao.insert(a2);

                AppointmentEntity a3 = new AppointmentEntity();
                a3.doctorId = "doc_03";
                a3.doctorName = "Dra. Andrea López";
                a3.specialtyName = "Dermatología";
                a3.patientName = "Laura Jiménez";
                a3.patientAge = 34;
                a3.date = dates.get(0);
                a3.time = "09:00";
                a3.status = "Cancelled";
                a3.reason = "Revisión de lunar";
                a3.createdAt = System.currentTimeMillis();
                dao.insert(a3);
            });
        }
    };
}
