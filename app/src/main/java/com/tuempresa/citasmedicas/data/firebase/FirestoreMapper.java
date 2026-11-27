package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce documentos de Firestore a los modelos de dominio.
 * <p>
 * <b>Por qué es la pieza clave para no romper nada.</b> La UI y el dominio sólo
 * conocen {@link Doctor}, {@link Specialty} y {@link Patient}. Toda la rareza del
 * formato de Firestore ({@code stringValue}, {@code integerValue"} as texto...)
 * queda encerrada aquí. Si mañana cambia el nombre de un campo en la base de datos,
 * se toca esta clase y nada más.
 * <p>
 * <b>Tolerancia a nulos.</b> Firestore omite los campos vacíos. Cada lectura usa un
 * valor de reserva explícito en lugar de asumir que el campo existe: así un documento
 * incompleto no provoca un {@code NullPointerException} en la pantalla.
 */
public final class FirestoreMapper {

    private FirestoreMapper() {
        // Clase de utilidades.
    }

    // ───────────────────────── Doctor ─────────────────────────

    @Nullable
    public static Doctor toDoctor(@Nullable FirestoreApi.FirestoreDocument doc) {
        if (doc == null) {
            return null;
        }
        Map<String, FirestoreApi.Value> f = doc.fields;
        Doctor d = new Doctor();
        d.setId(documentId(doc));
        d.setFullName(text(f, "fullName", text(f, "name", "")));
        d.setSpecialtyId(text(f, "specialtyId", ""));
        d.setSpecialtyName(text(f, "specialtyName", ""));
        d.setBio(text(f, "bio", ""));
        d.setRating(number(f, "rating", 0d));
        d.setYearsExperience((int) number(f, "yearsExperience", 0d));
        d.setAge((int) number(f, "age", 0d));
        d.setConsultationFee(number(f, "consultationFee", 0d));
        // El hospital/clínica se escribía al dar de alta al médico, pero no se leía
        // aquí: se perdía entre Firestore y la pantalla sin que nadie lo notara.
        d.setHospital(text(f, "hospital", ""));
        d.setPhone(text(f, "phone", ""));
        // Vinculación con una cuenta: 'email' es la invitación que escribe el
        // administrador y 'userId' el uid una vez que el médico entra por primera vez.
        d.setEmail(text(f, "email", ""));
        d.setUserId(text(f, "userId", ""));
        return d;
    }

    @NonNull
    public static List<Doctor> toDoctors(@Nullable List<FirestoreApi.FirestoreDocument> docs) {
        List<Doctor> out = new ArrayList<>();
        if (docs != null) {
            for (FirestoreApi.FirestoreDocument doc : docs) {
                Doctor d = toDoctor(doc);
                if (d != null) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    // ───────────────────────── Especialidad ─────────────────────────

    @Nullable
    public static Specialty toSpecialty(@Nullable FirestoreApi.FirestoreDocument doc) {
        if (doc == null) {
            return null;
        }
        Map<String, FirestoreApi.Value> f = doc.fields;
        Specialty s = new Specialty();
        s.setId(documentId(doc));
        s.setName(text(f, "name", ""));
        s.setDescription(text(f, "description", ""));
        // El emoji puede llamarse "icon", "iconEmoji" o "emoji" según cómo se
        // hayan cargado los datos en Firestore: se aceptan los tres.
        s.setIconEmoji(text(f, "iconEmoji",
                text(f, "icon", text(f, "emoji", ""))));
        // Nº de doctores: si el documento no lo trae, se deja 0 (la UI no lo exige).
        s.setDoctorCount((int) number(f, "doctorCount", 0d));
        return s;
    }

    @NonNull
    public static List<Specialty> toSpecialties(
            @Nullable List<FirestoreApi.FirestoreDocument> docs) {
        List<Specialty> out = new ArrayList<>();
        if (docs != null) {
            for (FirestoreApi.FirestoreDocument doc : docs) {
                Specialty s = toSpecialty(doc);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    // ───────────────────────── Paciente ─────────────────────────

    @Nullable
    public static Patient toPatient(@Nullable FirestoreApi.FirestoreDocument doc) {
        if (doc == null) {
            return null;
        }
        Map<String, FirestoreApi.Value> f = doc.fields;
        Patient p = new Patient();
        p.setId(documentId(doc));
        p.setFullName(text(f, "fullName", text(f, "name", "")));
        p.setAge((int) number(f, "age", 0d));
        p.setPhone(text(f, "phone", ""));
        p.setBloodType(text(f, "bloodType", ""));
        p.setInsurance(text(f, "insurance", ""));
        p.setAllergies(text(f, "allergies", ""));
        return p;
    }

    @NonNull
    public static List<Patient> toPatients(@Nullable List<FirestoreApi.FirestoreDocument> docs) {
        List<Patient> out = new ArrayList<>();
        if (docs != null) {
            for (FirestoreApi.FirestoreDocument doc : docs) {
                Patient p = toPatient(doc);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    // ───────────────────────── Utilidades ─────────────────────────

    /**
     * Extrae el id corto del documento.
     * <p>
     * El campo {@code name} de Firestore es una ruta completa
     * ({@code projects/p/databases/(default)/documents/doctors/doc_01}); para la app
     * sólo interesa {@code doc_01}.
     */
    @NonNull
    public static String documentId(@Nullable FirestoreApi.FirestoreDocument doc) {
        if (doc == null || doc.name == null) {
            return "";
        }
        int slash = doc.name.lastIndexOf('/');
        return slash >= 0 ? doc.name.substring(slash + 1) : doc.name;
    }

    /** Lee un campo de texto con valor de reserva. */
    @NonNull
    public static String text(@Nullable Map<String, FirestoreApi.Value> fields,
                              @NonNull String key, @NonNull String fallback) {
        if (fields == null) {
            return fallback;
        }
        FirestoreApi.Value v = fields.get(key);
        if (v == null) {
            return fallback;
        }
        if (v.stringValue != null) {
            return v.stringValue;
        }
        if (v.integerValue != null) {
            return v.integerValue;
        }
        if (v.booleanValue != null) {
            return String.valueOf(v.booleanValue);
        }
        if (v.doubleValue != null) {
            return String.valueOf(v.doubleValue);
        }
        return fallback;
    }

    /** Lee un campo numérico con valor de reserva. */
    public static double number(@Nullable Map<String, FirestoreApi.Value> fields,
                                @NonNull String key, double fallback) {
        if (fields == null) {
            return fallback;
        }
        FirestoreApi.Value v = fields.get(key);
        if (v == null) {
            return fallback;
        }
        if (v.integerValue != null) {
            try {
                return Double.parseDouble(v.integerValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        if (v.doubleValue != null) {
            return v.doubleValue;
        }
        if (v.stringValue != null && !v.stringValue.trim().isEmpty()) {
            try {
                return Double.parseDouble(v.stringValue.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
