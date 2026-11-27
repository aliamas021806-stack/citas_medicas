package com.tuempresa.citasmedicas.model;

/**
 * Estado de una cita médica.
 * <p>
 * <b>La distinción que importa en el portal del médico.</b> Una cita pendiente y
 * una ya atendida no se mezclan: el médico necesita ver de un golpe lo que le queda
 * por atender, y aparte el histórico. Por eso {@link #esPendiente()} agrupa
 * {@link #CONFIRMED}, {@link #PENDING} y {@link #NO_SHOW} (falta por resolver), y
 * {@link #esAtendida()} sólo {@link #ATTENDED}.
 * <p>
 * {@link #CANCELLED} no cuenta en ninguno de los dos grupos: se excluye de la agenda
 * igual que se hacía antes, porque una cita cancelada no requiere trabajo.
 */
public enum AppointmentStatus {
    /** Reservada y aún por atender. */
    CONFIRMED("Confirmed"),
    /** Reservada, esperando confirmación explícita. */
    PENDING("Pending"),
    /** Cancelada (por el paciente o por el consultorio). */
    CANCELLED("Cancelled"),
    /** El paciente acudió: la consulta se dio por terminada. */
    ATTENDED("Attended"),
    /** El paciente no acudió. Cuenta como pendiente de resolver, no como atendida. */
    NO_SHOW("NoShow");

    private final String value;

    AppointmentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * ¿Falta por atender?
     * <p>
     * {@link #NO_SHOW} cuenta como pendiente a propósito: el médico todavía tiene que
     * decidir qué hacer con ella (reprogramar, cerrar el hueco), así que sacarla de la
     * lista principal la escondería justo cuando más falta hace verla.
     */
    public boolean esPendiente() {
        return this == CONFIRMED || this == PENDING || this == NO_SHOW;
    }

    /** ¿La consulta ya se ha dado por atendida? */
    public boolean esAtendida() {
        return this == ATTENDED;
    }

    /** ¿Se puede marcar como atendida desde la agenda? */
    public boolean sePuedeAtender() {
        return esPendiente();
    }

    /**
     * Convierte el texto guardado al estado correspondiente.
     * <p>
     * Acepta tanto el valor de Firestore ({@code "Attended"}, {@code "NoShow"}) como el
     * nombre del enum, y también las variantes con guion bajo o espacio
     * ({@code "no_show"}, {@code "No Show"}) para no depender de cómo se escriba el
     * dato a mano en la consola. Cualquier valor desconocido cae en {@link #CONFIRMED},
     * que es el estado neutro: una cita no se considera atendida por accidente.
     */
    public static AppointmentStatus fromValue(String value) {
        if (value == null) {
            return CONFIRMED;
        }
        String normalizado = value.trim().replace("_", "").replace(" ", "");
        for (AppointmentStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)
                    || s.name().equalsIgnoreCase(normalizado)
                    || s.value.replace(" ", "").equalsIgnoreCase(normalizado)) {
                return s;
            }
        }
        return CONFIRMED;
    }
}
