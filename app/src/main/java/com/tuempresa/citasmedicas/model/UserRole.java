package com.tuempresa.citasmedicas.model;

/**
 * Rol del usuario en la aplicación.
 * <p>
 * <b>Por qué existe.</b> Hasta la v5.0 la app era sólo de paciente: no había forma de
 * distinguir quién eres, así que un médico no podía entrar a ver sus citas ni un
 * administrador podía dar de alta a nuevos médicos.
 * <p>
 * <b>Dónde se guarda.</b> El rol viaja en la sesión ({@code SharedPreferences}) y, si
 * Firestore está configurado, también en el documento {@code /users/{uid}}. Las reglas
 * de seguridad pueden leer ese documento, así que el rol del lado del servidor manda:
 * la copia local es sólo para que la interfaz decida qué pestañas mostrar.
 * <p>
 * <b>Límite importante.</b> Un usuario <u>no</u> puede ascenderse solo: el rol se
 * escribe una única vez al crear la cuenta (siempre {@link #PATIENT}) y, a partir de
 * ahí, únicamente un administrador puede cambiarlo.
 */
public enum UserRole {

    /** Paciente: agenda citas y ve las suyas. */
    PATIENT("patient", "Paciente"),

    /** Médico: ve las citas que le han programado. */
    DOCTOR("doctor", "Médico"),

    /** Administrador: da de alta médicos y especialidades. */
    ADMIN("admin", "Administrador");

    private final String value;
    private final String label;

    UserRole(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** Valor que se guarda en Firestore y en la sesión. */
    public String getValue() {
        return value;
    }

    /** Nombre para mostrar en la interfaz. */
    public String getLabel() {
        return label;
    }

    /**
     * Convierte el texto guardado al rol correspondiente.
     * <p>
     * Cualquier valor desconocido o vacío se interpreta como {@link #PATIENT}: es la
     * opción segura (el paciente tiene los permisos mínimos).
     */
    public static UserRole fromValue(String value) {
        if (value != null) {
            String normalizado = value.trim().toLowerCase(java.util.Locale.ROOT);
            for (UserRole rol : values()) {
                if (rol.value.equals(normalizado)) {
                    return rol;
                }
            }
        }
        return PATIENT;
    }

    /** ¿Puede ver la agenda de un médico? */
    public boolean isDoctor() {
        return this == DOCTOR;
    }

    /** ¿Puede gestionar médicos y especialidades? */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
