package com.tuempresa.citasmedicas.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base de datos local Room (SQLite) para las citas médicas.
 * <p>
 * <b>Versión 3.</b> Se añadió la columna {@code owner_id} (dueño de la cita) para
 * que cada paciente vea sólo las suyas, y se <b>eliminó el sembrado de citas de
 * ejemplo</b>: antes se insertaban 3 citas a nombre de "Laura Jiménez", de modo que
 * un usuario recién registrado veía las citas de otra persona.
 * <p>
 * Se incluye una {@link Migration} explícita en lugar de confiar sólo en
 * {@code fallbackToDestructiveMigration()}: así las citas que ya tenga el usuario
 * <b>no se borran</b> al actualizar la app.
 */
@Database(entities = {AppointmentEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AppointmentDao appointmentDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Migración 1 → 2: añade {@code remote_id} sin perder las citas existentes.
     * <p>
     * {@code defaultValue = ""} en la entidad es imprescindible para que el
     * {@code ALTER TABLE} funcione en SQLite con filas ya existentes: una columna
     * {@code NOT NULL} nueva necesita un valor por defecto.
     */
    public static final Migration MIGRACION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE appointments ADD COLUMN remote_id TEXT NOT NULL "
                    + "DEFAULT ''");
        }
    };

    /**
     * Migración 2 → 3: añade {@code owner_id} y borra las citas de ejemplo.
     * <p>
     * Las filas que ya existían se quedan con {@code owner_id = ""}, así que dejan de
     * mostrarse a cualquier usuario. Además se eliminan explícitamente las tres citas
     * de demostración (doctor {@code doc_01}, {@code doc_02} y {@code doc_03}, todas a
     * nombre de "Laura Jiménez") que versiones anteriores sembraban al arrancar.
     */
    public static final Migration MIGRACION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE appointments ADD COLUMN owner_id TEXT NOT NULL "
                    + "DEFAULT ''");
            // Las citas de ejemplo nunca fueron del usuario: se descartan para que no
            // aparezcan en "Mis Citas".
            db.execSQL("DELETE FROM appointments WHERE patient_name = 'Laura Jiménez' "
                    + "AND owner_id = ''");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "citas_medicas.db")
                            .addMigrations(MIGRACION_1_2, MIGRACION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Cierra y olvida la instancia abierta.
     * <p>
     * Necesario en pruebas y al cerrar sesión si se quiere reabrir la base con otro
     * usuario. El sembrado de citas de ejemplo ya <b>no existe</b>: cada cita nace del
     * formulario del paciente y con su {@code owner_id}.
     */
    public static synchronized void reset() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}
