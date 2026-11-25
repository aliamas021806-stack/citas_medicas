package com.tuempresa.citasmedicas.data.remote.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conversión entre los DTO de red y los modelos de dominio.
 * <p>
 * Es la única clase que conoce a la vez ambas representaciones, de modo que:
 * <ul>
 *   <li>La UI y el dominio nunca ven clases {@code *Dto}.</li>
 *   <li>Un {@code null} del JSON se traduce a un valor por defecto explícito aquí
 *       y no revienta más adelante con un NullPointerException.</li>
 *   <li>Los envoltorios ({@link Integer}, {@link Double}, {@link Boolean}) se
 *       desempaquetan con valores de reserva seguros.</li>
 * </ul>
 *
 * Reglas de saneamiento aplicadas:
 * <ul>
 *   <li>Textos nulos o en blanco → cadena vacía (la UI decide qué mostrar).</li>
 *   <li>Edad ausente → 0, que significa "no informada" (nunca se inventa una edad).</li>
 *   <li>Rating fuera de 0..5 → se acota al rango.</li>
 *   <li>Importes negativos → 0.</li>
 *   <li>Estatura/peso negativos → 0 (evita un IMC negativo o división por cero).</li>
 *   <li>Elementos nulos dentro de una lista → se descartan.</li>
 * </ul>
 */
public final class DtoMapper {

    private DtoMapper() {
        // Clase de utilidad.
    }

    // --------------------------------------------------------------- Specialty

    @NonNull
    public static Specialty toModel(@Nullable SpecialtyDto dto) {
        if (dto == null) {
            return new Specialty("", "", "", "", 0);
        }
        return new Specialty(
                text(dto.getId()),
                text(dto.getName()),
                text(dto.getIconEmoji()),
                text(dto.getDescription()),
                Math.max(0, intOf(dto.getDoctorCount())));
    }

    @NonNull
    public static List<Specialty> toSpecialtyList(@Nullable List<SpecialtyDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<Specialty> out = new ArrayList<>(dtos.size());
        for (SpecialtyDto dto : dtos) {
            if (dto != null) {
                out.add(toModel(dto));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Doctor

    @NonNull
    public static Doctor toModel(@Nullable DoctorDto dto) {
        if (dto == null) {
            return new Doctor("", "", "", "", "", 0d, 0, 0, 0d, "", "");
        }
        return new Doctor(
                text(dto.getId()),
                text(dto.getFullName()),
                text(dto.getSpecialtyId()),
                text(dto.getSpecialtyName()),
                text(dto.getBio()),
                clampRating(dto.getRating()),
                Math.max(0, intOf(dto.getYearsExperience())),
                Math.max(0, intOf(dto.getAge())),
                Math.max(0d, doubleOf(dto.getConsultationFee())),
                text(dto.getHospital()),
                text(dto.getPhotoUrl()));
    }

    @NonNull
    public static List<Doctor> toDoctorList(@Nullable List<DoctorDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<Doctor> out = new ArrayList<>(dtos.size());
        for (DoctorDto dto : dtos) {
            if (dto != null) {
                out.add(toModel(dto));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Patient

    @NonNull
    public static Patient toModel(@Nullable PatientDto dto) {
        if (dto == null) {
            return new Patient("", "", "", "", 0, "", "", "", "", "", "",
                    0d, 0d, "", 0L);
        }
        return new Patient(
                text(dto.getId()),
                text(dto.getFullName()),
                text(dto.getEmail()),
                text(dto.getPhone()),
                Math.max(0, intOf(dto.getAge())),
                text(dto.getBloodType()),
                text(dto.getGender()),
                text(dto.getAddress()),
                text(dto.getInsurance()),
                text(dto.getAllergies()),
                text(dto.getChronicConditions()),
                Math.max(0d, doubleOf(dto.getHeightCm())),
                Math.max(0d, doubleOf(dto.getWeightKg())),
                text(dto.getEmergencyContact()),
                Math.max(0L, longOf(dto.getCreatedAt())));
    }

    @NonNull
    public static List<Patient> toPatientList(@Nullable List<PatientDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<Patient> out = new ArrayList<>(dtos.size());
        for (PatientDto dto : dtos) {
            if (dto != null) {
                out.add(toModel(dto));
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- TimeSlot

    @NonNull
    public static TimeSlot toModel(@Nullable TimeSlotDto dto) {
        if (dto == null) {
            return new TimeSlot("", "", "", "", false);
        }
        return new TimeSlot(
                text(dto.getId()),
                text(dto.getDoctorId()),
                text(dto.getDate()),
                text(dto.getTime()),
                // Si el servidor no envía el campo, la franja se considera NO
                // disponible: es el valor seguro (nunca se ofrece una hora dudosa).
                Boolean.TRUE.equals(dto.getAvailable()));
    }

    @NonNull
    public static List<TimeSlot> toTimeSlotList(@Nullable List<TimeSlotDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        List<TimeSlot> out = new ArrayList<>(dtos.size());
        for (TimeSlotDto dto : dtos) {
            if (dto != null) {
                out.add(toModel(dto));
            }
        }
        return out;
    }

    // ------------------------------------------------------------- helpers

    /** Texto seguro: {@code null} → cadena vacía (recortada de espacios sobrantes). */
    @NonNull
    private static String text(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static int intOf(@Nullable Integer value) {
        return value == null ? 0 : value;
    }

    private static long longOf(@Nullable Long value) {
        return value == null ? 0L : value;
    }

    private static double doubleOf(@Nullable Double value) {
        return value == null ? 0d : value;
    }

    /** Acota la valoración al rango 0..5 (una media nunca puede salirse de ahí). */
    private static double clampRating(@Nullable Double value) {
        double rating = doubleOf(value);
        if (rating < 0d) {
            return 0d;
        }
        return Math.min(rating, 5d);
    }

    /** Lista vacía inmutable, útil como valor de reserva en pruebas. */
    @NonNull
    public static <T> List<T> empty() {
        return Collections.emptyList();
    }
}
