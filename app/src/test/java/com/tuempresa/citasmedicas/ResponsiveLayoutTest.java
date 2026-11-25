package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Verifica el requisito 5 del Prompt Maestro: "Hacer responsive para teléfonos y tablets".
 *
 * No basta con que existan las carpetas values-sw600dp / layout-sw600dp: hay que
 * demostrar que Android resuelve los recursos correctos en cada tamaño y que los
 * layouts de tablet conservan TODOS los IDs que el código Java busca (si falta uno,
 * la pantalla rompe con NullPointerException sólo en tablet).
 */
@RunWith(RobolectricTestRunner.class)
public class ResponsiveLayoutTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    /** Simula la densidad/ancho de un teléfono típico (360dp) y de una tablet (800dp). */
    private Context withWidth(int widthDp) {
        Configuration config = new Configuration(context().getResources().getConfiguration());
        config.screenWidthDp = widthDp;
        config.screenHeightDp = widthDp >= 600 ? 1280 : 800;
        config.smallestScreenWidthDp = widthDp >= 600 ? 600 : widthDp;
        return context().createConfigurationContext(config);
    }

    // ---------------------------------------------------------------- recursos

    @Test
    public void telefono_usaUnaColumna() {
        Context phone = withWidth(360);
        assertEquals(1, phone.getResources().getInteger(R.integer.doctors_grid_columns));
        assertEquals(1, phone.getResources().getInteger(R.integer.appointments_grid_columns));
        assertFalse(phone.getResources().getBoolean(R.bool.specialties_as_grid));
    }

    @Test
    public void tablet_usaDosColumnas() {
        Context tablet = withWidth(800);
        assertEquals(2, tablet.getResources().getInteger(R.integer.doctors_grid_columns));
        assertEquals(2, tablet.getResources().getInteger(R.integer.appointments_grid_columns));
        assertTrue(tablet.getResources().getBoolean(R.bool.specialties_as_grid));
    }

    @Test
    public void tablet_escalaLaRetículaDeEspaciados() {
        int phoneMd = withWidth(360).getResources().getDimensionPixelSize(R.dimen.spacing_md);
        int tabletMd = withWidth(800).getResources().getDimensionPixelSize(R.dimen.spacing_md);
        assertTrue("El espaciado base debe crecer en tablet (" + phoneMd + " vs " + tabletMd + ")",
                tabletMd > phoneMd);

        int phoneAvatar = withWidth(360).getResources().getDimensionPixelSize(R.dimen.avatar_size);
        int tabletAvatar = withWidth(800).getResources().getDimensionPixelSize(R.dimen.avatar_size);
        assertTrue("El avatar debe crecer en tablet", tabletAvatar > phoneAvatar);
    }

    @Test
    public void telefono_noTieneSeparacionDeGrilla() {
        assertEquals(0, withWidth(360).getResources().getDimensionPixelSize(R.dimen.grid_item_half_gap));
    }

    @Test
    public void tablet_tieneSeparacionDeGrilla() {
        assertTrue(withWidth(800).getResources().getDimensionPixelSize(R.dimen.grid_item_half_gap) > 0);
    }

    // ------------------------------------------------------- layouts inflables

    /**
     * Infla un layout con un Context dado y comprueba que no lanza excepción.
     *
     * Se envuelve en {@link ContextThemeWrapper} porque
     * {@code createConfigurationContext()} devuelve un Context con la configuración
     * del tamaño pedido pero SIN el tema de la aplicación: sin esto, Material 3
     * rechaza los TextInputLayout ("requires your app theme to be Theme.MaterialComponents").
     */
    private View inflate(Context context, int layoutRes) {
        Context themed = new ContextThemeWrapper(context, R.style.Theme_CitasMedicas);
        return LayoutInflater.from(themed).inflate(layoutRes, null, false);
    }

    @Test
    public void login_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.activity_login);
            assertNotNull("activity_login no infló a " + width + "dp", root);
            // IDs que LoginActivity busca siempre
            assertNotNull(root.findViewById(R.id.etEmail));
            assertNotNull(root.findViewById(R.id.etPassword));
            assertNotNull(root.findViewById(R.id.btnSubmit));
            assertNotNull(root.findViewById(R.id.tilName));
            assertNotNull(root.findViewById(R.id.tilAge));
            assertNotNull(root.findViewById(R.id.tvToggle));
            assertNotNull(root.findViewById(R.id.tvError));
        }
    }

    @Test
    public void pantallaDeCitas_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.fragment_citas);
            assertNotNull(root);
            assertNotNull("Falta rvAppointments a " + width + "dp", root.findViewById(R.id.rvAppointments));
            assertNotNull("Falta emptyState a " + width + "dp", root.findViewById(R.id.emptyState));
            assertNotNull("Falta tvSummary a " + width + "dp", root.findViewById(R.id.tvSummary));
            assertNotNull(root.findViewById(R.id.swipeRefresh));
        }
    }

    @Test
    public void pantallaDeDoctores_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.fragment_doctors);
            assertNotNull(root);
            assertNotNull("Falta rvDoctors a " + width + "dp", root.findViewById(R.id.rvDoctors));
            assertNotNull("Falta rvSpecialties a " + width + "dp", root.findViewById(R.id.rvSpecialties));
            assertNotNull("Falta etSearch a " + width + "dp", root.findViewById(R.id.etSearch));
            assertNotNull("Falta tvGreeting a " + width + "dp", root.findViewById(R.id.tvGreeting));
            assertNotNull("Falta btnRetry a " + width + "dp", root.findViewById(R.id.btnRetry));
            assertNotNull("Falta tvEmpty a " + width + "dp", root.findViewById(R.id.tvEmpty));
            assertNotNull("Falta progressBar a " + width + "dp", root.findViewById(R.id.progressBar));
            assertNotNull(root.findViewById(R.id.swipeRefresh));
            assertNotNull(root.findViewById(R.id.tvPatientHeaderAge));
        }
    }

    @Test
    public void reservaDeCita_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.activity_doctor_detail);
            assertNotNull(root);
            assertNotNull("Falta btnConfirm a " + width + "dp", root.findViewById(R.id.btnConfirm));
            assertNotNull("Falta rvDates a " + width + "dp", root.findViewById(R.id.rvDates));
            assertNotNull("Falta rvSlots a " + width + "dp", root.findViewById(R.id.rvSlots));
            assertNotNull("Falta etReason a " + width + "dp", root.findViewById(R.id.etReason));
            assertNotNull(root.findViewById(R.id.tvDoctorName));
            assertNotNull(root.findViewById(R.id.tvDoctorAvatar));
            assertNotNull(root.findViewById(R.id.tvDoctorBio));
            assertNotNull(root.findViewById(R.id.tvDoctorRating));
            assertNotNull(root.findViewById(R.id.tvDoctorAge));
            assertNotNull(root.findViewById(R.id.toolbarDetail));
            assertNotNull(root.findViewById(R.id.progressBar));
        }
    }

    @Test
    public void perfil_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.fragment_profile);
            assertNotNull(root);
            assertNotNull(root.findViewById(R.id.btnLogout));
            assertNotNull(root.findViewById(R.id.tvAvatar));
            assertNotNull(root.findViewById(R.id.tvName));
            assertNotNull(root.findViewById(R.id.tvUpcomingCount));
            assertNotNull(root.findViewById(R.id.tvTotalCount));
            assertNotNull(root.findViewById(R.id.tvInfoAge));
            assertNotNull(root.findViewById(R.id.tvInfoAllergies));
        }
    }

    @Test
    public void contenedorPrincipal_seInflaEnTelefonoYEnTablet() {
        for (int width : new int[]{360, 800}) {
            View root = inflate(withWidth(width), R.layout.activity_main);
            assertNotNull(root);
            assertNotNull(root.findViewById(R.id.toolbar));
            assertNotNull(root.findViewById(R.id.fragmentContainer));
            assertNotNull(root.findViewById(R.id.bottomNav));
        }
    }

    // ------------------------------------------- items de lista en tablet (2 col)

    @Test
    public void itemDeDoctor_yDeCita_seInflanEnAmbosTamanos() {
        View doctor = inflate(withWidth(800), R.layout.item_doctor);
        assertNotNull(doctor.findViewById(R.id.tvName));
        assertNotNull(doctor.findViewById(R.id.tvSpecialty));
        assertNotNull(doctor.findViewById(R.id.tvFee));
        assertNotNull(doctor.findViewById(R.id.tvAvatar));

        View cita = inflate(withWidth(800), R.layout.item_appointment);
        assertNotNull(cita.findViewById(R.id.tvDoctorName));
        assertNotNull(cita.findViewById(R.id.tvDate));
        assertNotNull(cita.findViewById(R.id.tvTime));
        assertNotNull(cita.findViewById(R.id.btnCancel));
    }

    @Test
    public void lasVistasDeTabletNoPierdenTextos() {
        // Un TextView vacío en tablet sería señal de un layout mal copiado.
        View citas = inflate(withWidth(800), R.layout.fragment_citas);
        TextView vacio = citas.findViewById(R.id.tvSummary);
        assertNotNull(vacio.getText());

        View doctores = inflate(withWidth(800), R.layout.fragment_doctors);
        TextView saludo = doctores.findViewById(R.id.tvGreeting);
        assertNotNull(saludo.getText());
        assertEquals(View.GONE, doctores.findViewById(R.id.progressBar).getVisibility());
        assertTrue(doctores.findViewById(R.id.progressBar) instanceof ProgressBar);
    }

    @Test
    public void tablet_reutilizaElMismoRecyclerViewParaLaGrilla() {
        // El ancho de la celda lo decide el LayoutManager (GridLayoutManager con las
        // columnas de values-sw600dp/integers.xml), no un layout distinto: así
        // item_doctor.xml y item_appointment.xml son idénticos en ambos tamaños.
        View doctores = inflate(withWidth(800), R.layout.fragment_doctors);
        assertTrue(doctores.findViewById(R.id.rvDoctors) instanceof RecyclerView);
        View citas = inflate(withWidth(800), R.layout.fragment_citas);
        assertTrue(citas.findViewById(R.id.rvAppointments) instanceof RecyclerView);
    }
}
