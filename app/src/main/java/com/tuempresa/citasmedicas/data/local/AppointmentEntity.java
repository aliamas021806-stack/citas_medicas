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
}
