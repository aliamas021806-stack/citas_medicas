package com.tuempresa.citasmedicas.navigation;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.R;

/**
 * Rutas de la aplicación, escritas a mano con verificación de tipos.
 * <p>
 * Equivalencia con Navigation Compose "type-safe": en Kotlin se usan
 * {@code @Serializable data class DoctorDetail(val doctorId: String)}. En Java
 * puro se logra el mismo objetivo sin strings mágicos con esta clase:
 * cada ruta es un objeto inmutable que conoce su id de destino, sus claves de
 * argumentos y los valores tipados que transporta. Ningún {@code String} de
 * ruta viaja sin pasar por aquí, así que un error de nombre falla en compilación.
 */
public final class Routes {

    private Routes() {
    }

    // ---------------------------------------------------------------- claves
    /** Clave del argumento "doctor" en {@link DoctorDetail}. */
    public static final String ARG_DOCTOR = "arg_doctor";
    /** Clave del argumento de pestaña inicial. */
    public static final String ARG_TAB = "arg_tab";

    // ------------------------------------------------------------- destinos
    /** Pantalla de login / registro. */
    public static final class Login implements Route {
        @Override
        public Class<?> destination() {
            return com.tuempresa.citasmedicas.view.auth.LoginActivity.class;
        }
    }

    /** Contenedor principal con las tres pestañas. */
    public static final class Main implements Route {
        @Nullable
        private final Integer initialTabId;

        public Main() {
            this.initialTabId = null;
        }

        public Main(@IdRes int initialTabId) {
            this.initialTabId = initialTabId;
        }

        @Override
        public Class<?> destination() {
            return com.tuempresa.citasmedicas.view.main.MainActivity.class;
        }

        @Nullable
        public Integer initialTabId() {
            return initialTabId;
        }
    }

    /**
     * Detalle del doctor con selección de fecha/hora y confirmación.
     * El argumento es el objeto {@code Doctor} completo (Serializable), tipado.
     */
    public static final class DoctorDetail implements Route {
        private final com.tuempresa.citasmedicas.model.Doctor doctor;

        public DoctorDetail(com.tuempresa.citasmedicas.model.Doctor doctor) {
            this.doctor = doctor;
        }

        public com.tuempresa.citasmedicas.model.Doctor doctor() {
            return doctor;
        }

        @Override
        public Class<?> destination() {
            return com.tuempresa.citasmedicas.view.appointment.DoctorDetailActivity.class;
        }
    }

    // -------------------------------------------------------------- contrato
    /** Contrato común de toda ruta: debe saber a qué componente ir. */
    public interface Route {
        Class<?> destination();
    }

    // ------------------------------------------------------------- pestañas
    /**
     * Identificadores de las pestañas del BottomNavigationView.
     * <p>
     * Hay tres juegos de pestañas porque cada rol ve una cosa distinta:
     * <ul>
     *   <li><b>Paciente:</b> Doctores, Mis Citas, Perfil.</li>
     *   <li><b>Médico:</b> Mi Agenda, Perfil.</li>
     *   <li><b>Administrador:</b> Médicos (alta), Perfil.</li>
     * </ul>
     * El id es el del recurso de menú, así que {@code bottomNav.setSelectedItemId}
     * funciona igual en los tres casos.
     */
    public static final class Tab {
        public static final int DOCTORS = R.id.nav_doctors;
        public static final int APPOINTMENTS = R.id.nav_appointments;
        public static final int PROFILE = R.id.nav_profile;
        /** Agenda del día (sólo médico). */
        public static final int AGENDA = R.id.nav_agenda;
        /** Alta de médicos (sólo administrador). */
        public static final int ADMIN = R.id.nav_admin;

        private Tab() {
        }
    }
}
