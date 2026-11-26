package com.tuempresa.citasmedicas.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.model.UserRole;

/**
 * Gestiona la sesión del usuario y su <b>expediente</b> persistidos con
 * SharedPreferences.
 * <p>
 * Guarda no sólo las credenciales básicas, sino también el teléfono, el grupo
 * sanguíneo, la aseguradora y las alergias que el paciente escribe al registrarse.
 * Antes esos datos se tomaban del mock y el perfil mostraba los de otro paciente.
 */
public class SessionManager {

    private static final String PREF_NAME = "citas_medicas_session";
    private static final String KEY_LOGGED = "logged_in";
    private static final String KEY_ID = "user_id";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_LAST_NAME = "user_last_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_PHONE = "user_phone";
    private static final String KEY_AGE = "user_age";
    private static final String KEY_BLOOD = "user_blood_type";
    private static final String KEY_INSURANCE = "user_insurance";
    private static final String KEY_ALLERGIES = "user_allergies";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_DOCTOR_ID = "user_doctor_id";

    private final SharedPreferences prefs;
    private static SessionManager instance;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveSession(User user) {
        prefs.edit()
                .putBoolean(KEY_LOGGED, true)
                .putString(KEY_ID, user.getId())
                .putString(KEY_NAME, user.getFirstName())
                .putString(KEY_LAST_NAME, user.getLastName())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_PHONE, user.getPhone())
                .putInt(KEY_AGE, user.getAge())
                .putString(KEY_BLOOD, user.getBloodType())
                .putString(KEY_INSURANCE, user.getInsurance())
                .putString(KEY_ALLERGIES, user.getAllergies())
                .putString(KEY_ROLE, user.getRole().getValue())
                .putString(KEY_DOCTOR_ID, user.getDoctorId())
                .apply();
    }

    /**
     * Guarda el rol del usuario cuando llega desde Firestore.
     * <p>
     * Se usa al iniciar sesión: el rol del servidor es el que manda, así que si un
     * administrador asciende a alguien, la app lo refleja en el siguiente acceso sin
     * necesidad de reinstalar.
     */
    public void updateRole(UserRole role, String doctorId) {
        prefs.edit()
                .putString(KEY_ROLE, (role != null ? role : UserRole.PATIENT).getValue())
                .putString(KEY_DOCTOR_ID, doctorId)
                .apply();
    }

    /** Rol del usuario en sesión. Sin sesión o sin dato, {@link UserRole#PATIENT}. */
    public UserRole getRole() {
        return UserRole.fromValue(prefs.getString(KEY_ROLE, UserRole.PATIENT.getValue()));
    }

    /** Id del médico vinculado a esta cuenta (vacío si no lo es). */
    public String getDoctorId() {
        return prefs.getString(KEY_DOCTOR_ID, "");
    }

    /**
     * Actualiza sólo el expediente médico, sin tocar las credenciales.
     * <p>
     * Se usa cuando el perfil se rellena por primera vez o se edita.
     */
    public void updateMedicalRecord(String phone, String bloodType, String insurance,
                                    String allergies) {
        prefs.edit()
                .putString(KEY_PHONE, phone)
                .putString(KEY_BLOOD, bloodType)
                .putString(KEY_INSURANCE, insurance)
                .putString(KEY_ALLERGIES, allergies)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED, false);
    }

    /**
     * Id del usuario en sesión, o {@code null} si no hay sesión.
     * <p>
     * Es la clave para filtrar las citas: cada paciente debe ver sólo las suyas.
     */
    public String getUserId() {
        if (!isLoggedIn()) {
            return null;
        }
        return prefs.getString(KEY_ID, null);
    }

    public User getUser() {
        if (!isLoggedIn()) {
            return null;
        }
        User user = new User(
                prefs.getString(KEY_ID, ""),
                prefs.getString(KEY_NAME, "Paciente"),
                prefs.getString(KEY_EMAIL, ""),
                prefs.getString(KEY_PHONE, ""),
                prefs.getInt(KEY_AGE, 0));
        user.setLastName(prefs.getString(KEY_LAST_NAME, ""));
        user.setBloodType(prefs.getString(KEY_BLOOD, ""));
        user.setInsurance(prefs.getString(KEY_INSURANCE, ""));
        user.setAllergies(prefs.getString(KEY_ALLERGIES, ""));
        user.setRole(UserRole.fromValue(prefs.getString(KEY_ROLE, UserRole.PATIENT.getValue())));
        user.setDoctorId(prefs.getString(KEY_DOCTOR_ID, ""));
        return user;
    }

    /**
     * ¿El expediente está completo?
     * <p>
     * Sirve para que el perfil no muestre huecos y para saber si hay que pedir los
     * datos antes de sincronizar con Firestore.
     */
    public boolean hasMedicalRecord() {
        return !isBlank(prefs.getString(KEY_PHONE, ""))
                && !isBlank(prefs.getString(KEY_BLOOD, ""));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}
