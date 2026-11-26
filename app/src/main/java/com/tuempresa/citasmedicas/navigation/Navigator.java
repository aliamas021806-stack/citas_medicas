package com.tuempresa.citasmedicas.navigation;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.UserRole;

/**
 * Navegador único de la aplicación.
 * <p>
 * Equivalencia Java del {@code NavHostController} de Navigation Compose: centraliza
 * todas las transiciones y aplica las banderas (flags) en un solo lugar, en vez de
 * repartir {@code new Intent(...)} por las pantallas. Sustituye al NavGraph de Compose
 * manteniendo el grafo explícito en {@link Routes}.
 * <p>
 * <b>Un mismo contenedor, tres aplicaciones.</b> {@code MainActivity} es la única
 * pantalla contenedora, pero el paciente, el médico y el administrador no ven lo
 * mismo. El rol decide qué menú inferior se instala y, por tanto, qué fragmentos se
 * pueden montar: un médico nunca llega a "Mis Citas" porque esa pestaña no existe en
 * su menú, no porque se oculte con un {@code if} en cada pantalla.
 */
public final class Navigator {

    private Navigator() {
    }

    /** Navega a cualquier ruta registrada en {@link Routes}. */
    public static void navigate(@NonNull Activity from, @NonNull Routes.Route route) {
        Intent intent = new Intent(from, route.destination());
        applyArguments(intent, route);
        from.startActivity(intent);
    }

    /**
     * Navega limpiando la pila (login/logout): equivale a
     * {@code popUpTo(0) { inclusive = true }} en Navigation Compose.
     */
    public static void navigateClearingStack(@NonNull Activity from, @NonNull Routes.Route route) {
        Intent intent = new Intent(from, route.destination());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        applyArguments(intent, route);
        from.startActivity(intent);
        from.finish();
    }

    /** Cierra la pantalla actual y devuelve el resultado al destino anterior. */
    public static void back(@NonNull Activity from) {
        from.finish();
    }

    /** Resuelve el Fragment que corresponde a una pestaña del menú inferior. */
    @Nullable
    public static Fragment fragmentForTab(int tabId) {
        if (tabId == Routes.Tab.APPOINTMENTS) {
            return new com.tuempresa.citasmedicas.view.appointment.CitasFragment();
        }
        if (tabId == Routes.Tab.PROFILE) {
            return new com.tuempresa.citasmedicas.view.main.ProfileFragment();
        }
        if (tabId == Routes.Tab.DOCTORS) {
            return new com.tuempresa.citasmedicas.view.main.DoctorsFragment();
        }
        if (tabId == Routes.Tab.AGENDA) {
            return new com.tuempresa.citasmedicas.view.doctor.AgendaDoctorFragment();
        }
        if (tabId == Routes.Tab.ADMIN) {
            return new com.tuempresa.citasmedicas.view.admin.AdminDoctoresFragment();
        }
        return null;
    }

    /** Título de la barra superior para cada pestaña. */
    public static int titleForTab(int tabId) {
        if (tabId == Routes.Tab.APPOINTMENTS) {
            return R.string.tab_appointments;
        }
        if (tabId == Routes.Tab.PROFILE) {
            return R.string.profile_title;
        }
        if (tabId == Routes.Tab.AGENDA) {
            return R.string.tab_agenda;
        }
        if (tabId == Routes.Tab.ADMIN) {
            return R.string.tab_admin;
        }
        return R.string.tab_doctors;
    }

    /**
     * Menú inferior que corresponde a un rol.
     * <p>
     * Es un recurso de menú y no una lista de pestañas construida a mano: así el
     * {@code BottomNavigationView} sigue gestionando selección, estado guardado y
     * accesibilidad sin código extra.
     */
    @MenuRes
    public static int menuForRole(@Nullable UserRole role) {
        if (role == UserRole.DOCTOR) {
            return R.menu.menu_bottom_nav_doctor;
        }
        if (role == UserRole.ADMIN) {
            return R.menu.menu_bottom_nav_admin;
        }
        return R.menu.menu_bottom_nav;
    }

    /**
     * Primera pestaña del rol: la que se muestra al entrar.
     * <p>
     * Un médico aterriza en su agenda (es a lo que viene) y un administrador en el
     * alta de médicos. El paciente sigue entrando en Doctores, como siempre.
     */
    public static int homeTabForRole(@Nullable UserRole role) {
        if (role == UserRole.DOCTOR) {
            return Routes.Tab.AGENDA;
        }
        if (role == UserRole.ADMIN) {
            return Routes.Tab.ADMIN;
        }
        return Routes.Tab.DOCTORS;
    }

    /** Traslada los argumentos tipados de la ruta al Intent de destino. */
    private static void applyArguments(@NonNull Intent intent, @NonNull Routes.Route route) {
        if (route instanceof Routes.DoctorDetail) {
            Routes.DoctorDetail detail = (Routes.DoctorDetail) route;
            intent.putExtra(Routes.ARG_DOCTOR, detail.doctor());
        } else if (route instanceof Routes.Main) {
            Routes.Main main = (Routes.Main) route;
            if (main.initialTabId() != null) {
                intent.putExtra(Routes.ARG_TAB, main.initialTabId().intValue());
            }
        }
    }
}
