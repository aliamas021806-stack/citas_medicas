package com.tuempresa.citasmedicas.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entidad Room que representa una cita almacenada localmente (SQLite).
 * Incluye la EDAD del paciente.
 */
@Entity(tableName = "appointments")
public class AppointmentEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "doctor_id")
    public String doctorId = "";

    @ColumnInfo(name = "doctor_name")
    public String doctorName;

    @ColumnInfo(name = "specialty_name")
    public String specialtyName;

    @ColumnInfo(name = "patient_name")
    public String patientName;

    @ColumnInfo(name = "patient_age")
    public int patientAge;

    @NonNull
    public String date = "";

    @NonNull
    public String time = "";

    /** Confirmed / Pending / Cancelled */
    public String status;

    public String reason;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * Id del documento en Firestore, si la cita llegó a subirse.
     * <p>
     * Sirve para dos cosas: no volver a crear el documento (duplicaría la cita en la
     * nube) y saber qué borrar allí si la cita se elimina aquí. Vacío {@code ""}
     * significa "todavía no está en la nube", que es el estado normal cuando se agenda
     * sin internet.
     * <p>
     * Se añadió en la versión 2 de la base de datos (ver {@code AppDatabase}).
     */
    @NonNull
    @ColumnInfo(name = "remote_id", defaultValue = "")
    public String remoteId = "";

    /**
     * Id del paciente <b>dueño</b> de la cita.
     * <p>
     * Es la clave que permite que cada usuario vea únicamente sus citas. Antes no
     * existía: la tabla era única para toda la app, así que un paciente recién
     * registrado veía las citas de otro (las de ejemplo, sembradas a nombre de
     * "Laura Jiménez").
     * <p>
     * Vacío {@code ""} significa "cita antigua, sin dueño conocido": las filas que ya
     * existieran antes de la versión 3 se migran con este valor y quedan ocultas,
     * en lugar de mostrarse a cualquier usuario.
     */
    @NonNull
    @ColumnInfo(name = "owner_id", defaultValue = "")
    public String ownerId = "";
}
