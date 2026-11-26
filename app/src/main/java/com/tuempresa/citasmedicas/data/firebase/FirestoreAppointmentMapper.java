package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce documentos de la colección {@code appointments} de Firestore al modelo de
 * dominio {@link Appointment}.
 * <p>
 * <b>Por qué una clase aparte y no un método más en {@link FirestoreMapper}.</b>
 * {@code FirestoreMapper} traduce el <i>catálogo</i> (doctores, especialidades,
 * pacientes). Las citas se leen en el sentido inverso al habitual (de la nube al
 * móvil) y sólo lo hace la agenda del médico, así que se mantienen separadas para no
 * mezclar responsabilidades: si mañana cambia el esquema de una cita, se toca aquí.
 * <p>
 * <b>Tolerancia a nulos.</b> Firestore omite los campos vacíos y los documentos
 * antiguos pueden no tener {@code ownerId}. Cada lectura usa un valor de reserva
 * explícito: un documento incompleto no debe provocar un {@code NullPointerException}
 * en la pantalla.
 */
public final class FirestoreAppointmentMapper {

    private FirestoreAppointmentMapper() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Convierte un documento de Firestore en una cita de dominio.
     *
     * @return la cita, o {@code null} si el documento es nulo
     */
    @Nullable
    public static Appointment toAppointment(@Nullable FirestoreApi.FirestoreDocument doc) {
        if (doc == null) {
            return null;
        }
        Map<String, FirestoreApi.Value> f = doc.fields;

        Appointment a = new Appointment();
        // El id que ve la UI es el del documento remoto: no colisiona con los ids
        // locales de Room (que son numéricos) y permite abrir la cita después.
        a.setId(FirestoreMapper.documentId(doc));
        a.setRemoteId(FirestoreMapper.documentId(doc));
        a.setDoctorId(FirestoreMapper.text(f, "doctorId", ""));
        a.setDoctorName(FirestoreMapper.text(f, "doctorName", ""));
        a.setSpecialtyName(FirestoreMapper.text(f, "specialtyName", ""));
        a.setPatientName(FirestoreMapper.text(f, "patientName", ""));
        a.setPatientAge((int) FirestoreMapper.number(f, "patientAge", 0d));
        a.setDate(FirestoreMapper.text(f, "date", ""));
        a.setTime(FirestoreMapper.text(f, "time", ""));
        a.setStatus(AppointmentStatus.fromValue(FirestoreMapper.text(f, "status", "Confirmed")));
        a.setReason(FirestoreMapper.text(f, "reason", ""));
        a.setCreatedAt((long) FirestoreMapper.number(f, "createdAt", 0d));
        a.setOwnerId(FirestoreMapper.text(f, "ownerId", ""));
        return a;
    }

    /** Convierte una lista de documentos, descartando los que no se puedan leer. */
    @NonNull
    public static List<Appointment> toAppointments(
            @Nullable List<FirestoreApi.FirestoreDocument> docs) {
        List<Appointment> out = new ArrayList<>();
        if (docs != null) {
            for (FirestoreApi.FirestoreDocument doc : docs) {
                Appointment a = toAppointment(doc);
                if (a != null) {
                    out.add(a);
                }
            }
        }
        return out;
    }
}
