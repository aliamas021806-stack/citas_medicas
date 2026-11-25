package com.tuempresa.citasmedicas.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de horario: contrato JSON de {@code GET /api/slots/{doctorId}}.
 * <p>
 * {@code date} viaja como {@code "yyyy-MM-dd"} y {@code time} como {@code "HH:mm"}
 * (texto). Se mantienen como {@link String} a propósito: son valores de calendario
 * ya formateados por el servidor, no instantes, así que convertirlos a
 * {@code Date} en la capa de red solo añadiría errores de zona horaria.
 */
public class TimeSlotDto {

    @SerializedName("id")
    private String id;

    @SerializedName("doctorId")
    private String doctorId;

    @SerializedName("date")
    private String date;

    @SerializedName("time")
    private String time;

    /**
     * Disponibilidad de la franja. Es {@link Boolean} para distinguir el campo
     * ausente ({@code null}) de un {@code false} explícito del servidor.
     */
    @SerializedName("available")
    private Boolean available;

    public TimeSlotDto() {
    }

    public TimeSlotDto(String id, String doctorId, String date, String time, Boolean available) {
        this.id = id;
        this.doctorId = doctorId;
        this.date = date;
        this.time = time;
        this.available = available;
    }

    public String getId() {
        return id;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public Boolean getAvailable() {
        return available;
    }
}
