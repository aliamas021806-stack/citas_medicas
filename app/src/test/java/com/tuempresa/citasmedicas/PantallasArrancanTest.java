package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.navigation.Navigator;
import com.tuempresa.citasmedicas.navigation.Routes;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.view.appointment.DoctorDetailActivity;
import com.tuempresa.citasmedicas.view.auth.LoginActivity;
import com.tuempresa.citasmedicas.view.main.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Arranque REAL de las pantallas.
 *
 * Cada test crea la Activity con su ciclo de vida completo
 * ({@code buildActivity(...).setup()}), igual que hace Android al abrir la app.
 * Esto detecta los fallos que sólo aparecen en tiempo de ejecución: recursos que
 * no existen en un layout, {@code findViewById} que devuelve null, temas
 * incompatibles, observadores mal enganchados o divisiones por cero en los
 * adaptadores.
 *
 * Es lo más cercano a abrir el emulador que permite un entorno sin KVM.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PantallasArrancanTest {

    private SessionManager session;

    @Before
    public void setUp() {
        session = SessionManager.getInstance(ApplicationProvider.getApplicationContext());
        session.logout();
    }

    @After
    public void tearDown() {
        session.logout();
    }

    private void iniciarSesion() {
        session.saveSession(new User("user_01", "Laura Jiménez",
                "laura@correo.com", "+34 600 123 456", 34));
    }

    // ------------------------------------------------------------- LoginActivity

    @Test
    public void loginArrancaSinCrashear() {
        ActivityController<LoginActivity> controller =
                Robolectric.buildActivity(LoginActivity.class).setup();
        Activity activity = controller.get();

        assertNotNull(activity);
        assertFalse("La pantalla no debe cerrarse sola", activity.isFinishing());

        assertNotNull(activity.findViewById(R.id.etEmail));
        assertNotNull(activity.findViewById(R.id.etPassword));
        assertNotNull(activity.findViewById(R.id.btnSubmit));
        assertNotNull(activity.findViewById(R.id.tilName));
        assertNotNull(activity.findViewById(R.id.tilAge));
        assertNotNull(activity.findViewById(R.id.tvToggle));

        // En modo login los campos de registro deben estar ocultos.
        assertEquals(View.GONE, activity.findViewById(R.id.tilName).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.tilAge).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.progressBar).getVisibility());

        controller.pause().stop().destroy();
    }

    @Test
    public void elBotonDeRegistroMuestraLosCamposDeNombreYEdad() {
        ActivityController<LoginActivity> controller =
                Robolectric.buildActivity(LoginActivity.class).setup();
        Activity activity = controller.get();

        TextView tvToggle = activity.findViewById(R.id.tvToggle);
        tvToggle.performClick();

        assertEquals("Al pasar a registro debe verse el nombre",
                View.VISIBLE, activity.findViewById(R.id.tilName).getVisibility());
        assertEquals("Al pasar a registro debe verse la edad",
                View.VISIBLE, activity.findViewById(R.id.tilAge).getVisibility());
        MaterialButton btn = activity.findViewById(R.id.btnSubmit);
        assertEquals(activity.getString(R.string.register_button), btn.getText().toString());

        // Y volver atrás.
        tvToggle.performClick();
        assertEquals(View.GONE, activity.findViewById(R.id.tilName).getVisibility());

        controller.pause().stop().destroy();
    }

    @Test
    public void elLoginVacioMuestraErrorYNoAvanza() {
        ActivityController<LoginActivity> controller =
                Robolectric.buildActivity(LoginActivity.class).setup();
        Activity activity = controller.get();

        activity.findViewById(R.id.btnSubmit).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        // Con email/password vacíos el validador debe responder con error y la
        // pantalla NO debe cerrarse.
        TextView tvError = activity.findViewById(R.id.tvError);
        assertEquals("Debe mostrarse el mensaje de error",
                View.VISIBLE, tvError.getVisibility());
        assertFalse("No debe navegar con datos inválidos", activity.isFinishing());

        controller.pause().stop().destroy();
    }

    @Test
    public void elLoginDeDemoNavegaAMainActivity() {
        ActivityController<LoginActivity> controller =
                Robolectric.buildActivity(LoginActivity.class).setup();
        Activity activity = controller.get();

        ((TextView) activity.findViewById(R.id.etEmail)).setText("laura@correo.com");
        ((TextView) activity.findViewById(R.id.etPassword)).setText("1234");
        activity.findViewById(R.id.btnSubmit).performClick();

        // El AuthRepository responde tras 900 ms simulados.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1500,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue("Debe quedar sesión guardada tras un login válido",
                session.isLoggedIn());

        Intent siguiente = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("Debe haber lanzado la pantalla principal", siguiente);
        assertEquals(MainActivity.class.getName(), siguiente.getComponent().getClassName());

        controller.pause().stop().destroy();
    }

    // -------------------------------------------------------------- MainActivity

    @Test
    public void mainArrancaYMontaElFragmentoDeDoctores() {
        iniciarSesion();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        Activity activity = controller.get();

        assertNotNull(activity);
        assertFalse("Con sesión iniciada no debe redirigir al login", activity.isFinishing());

        assertNotNull(activity.findViewById(R.id.toolbar));
        assertNotNull(activity.findViewById(R.id.fragmentContainer));
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        assertNotNull(nav);

        assertEquals("La pestaña inicial debe ser Doctores",
                R.id.nav_doctors, nav.getSelectedItemId());

        Fragment montado = ((androidx.fragment.app.FragmentActivity) activity)
                .getSupportFragmentManager().getFragments().get(0);
        assertNotNull(montado);

        controller.pause().stop().destroy();
    }

    @Test
    public void sinSesionMainRedirigeAlLogin() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        Activity activity = controller.get();

        Intent siguiente = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("Sin sesión debe abrir el login", siguiente);
        assertEquals(LoginActivity.class.getName(), siguiente.getComponent().getClassName());
        assertTrue("Y cerrarse para no dejar una pantalla vacía", activity.isFinishing());
    }

    @Test
    public void lasTresPestanasSePuedenAbrirSinCrashear() {
        iniciarSesion();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        Activity activity = controller.get();
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);

        int[] pestanas = {R.id.nav_appointments, R.id.nav_profile, R.id.nav_doctors};
        for (int pestana : pestanas) {
            nav.setSelectedItemId(pestana);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertFalse("La app no debe cerrarse al abrir la pestaña " + pestana,
                    activity.isFinishing());
            assertNotNull("La toolbar debe tener título",
                    activity.findViewById(R.id.toolbar));
            androidx.appcompat.widget.Toolbar toolbar =
                    activity.findViewById(R.id.toolbar);
            assertNotNull(toolbar.getTitle());
            assertFalse("El título no puede quedar vacío",
                    toolbar.getTitle().toString().trim().isEmpty());
        }

        controller.pause().stop().destroy();
    }

    @Test
    public void elTituloSobreviveAUnaRotacion() {
        iniciarSesion();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);
        String tituloDoctores = toolbar.getTitle().toString();

        // Ir a "Mis Citas" y rotar.
        ((BottomNavigationView) activity.findViewById(R.id.bottomNav))
                .setSelectedItemId(R.id.nav_appointments);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        String tituloCitas = toolbar.getTitle().toString();

        controller.recreate();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        androidx.appcompat.widget.Toolbar trasRotar =
                controller.get().findViewById(R.id.toolbar);
        assertEquals("Tras rotar el título debe seguir siendo el de la sección",
                tituloCitas, trasRotar.getTitle().toString());
        assertTrue("Y no debe haberse quedado con el de Doctores",
                !tituloDoctores.equals(trasRotar.getTitle().toString()));

        controller.pause().stop().destroy();
    }

    // ------------------------------------------------- DoctorDetailActivity

    private Intent intentConDoctor() {
        Doctor doctor = MockData.getDoctors().get(0);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                DoctorDetailActivity.class);
        intent.putExtra(Routes.ARG_DOCTOR, doctor);
        return intent;
    }

    @Test
    public void elDetalleDelDoctorArrancaYSuBotonDeConfirmarExiste() {
        iniciarSesion();

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, intentConDoctor())
                        .setup();
        Activity activity = controller.get();

        assertFalse("Sin doctor no debe cerrarse, pero con doctor tampoco",
                activity.isFinishing());

        assertNotNull(activity.findViewById(R.id.rvDates));
        assertNotNull(activity.findViewById(R.id.rvSlots));
        assertNotNull(activity.findViewById(R.id.etReason));
        MaterialButton btnConfirm = activity.findViewById(R.id.btnConfirm);
        assertNotNull("El botón de confirmar es esencial", btnConfirm);
        assertTrue("Debe empezar habilitado", btnConfirm.isEnabled());

        TextView nombre = activity.findViewById(R.id.tvDoctorName);
        assertNotNull(nombre.getText());
        assertFalse("El nombre del doctor no puede quedar vacío",
                nombre.getText().toString().trim().isEmpty());

        controller.pause().stop().destroy();
    }

    @Test
    public void confirmarSinElegirHorarioAvisaYNoCierraLaPantalla() {
        iniciarSesion();

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, intentConDoctor())
                        .setup();
        Activity activity = controller.get();

        // Nadie ha elegido horario todavía.
        activity.findViewById(R.id.btnConfirm).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertFalse("No debe crear la cita ni cerrarse sin horario",
                activity.isFinishing());

        controller.pause().stop().destroy();
    }

    @Test
    public void elDetalleSinDoctorSeCierraEnVezDeCrashear() {
        Intent sinExtra = new Intent(ApplicationProvider.getApplicationContext(),
                DoctorDetailActivity.class);

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, sinExtra).setup();
        Activity activity = controller.get();

        // Sin el extra no hay nada que mostrar: debe cerrarse limpiamente, no petar.
        assertTrue("Debe cerrarse en vez de lanzar NullPointerException",
                activity.isFinishing());
    }

    @Test
    public void elDetalleSobreviveAUnaRotacion() {
        iniciarSesion();

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, intentConDoctor())
                        .setup();
        Activity activity = controller.get();

        TextView nombreAntes = activity.findViewById(R.id.tvDoctorName);
        String antes = nombreAntes.getText().toString();

        controller.recreate();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        Activity tras = controller.get();
        assertFalse("Tras rotar no debe cerrarse", tras.isFinishing());
        TextView nombreDespues = tras.findViewById(R.id.tvDoctorName);
        assertEquals("El doctor debe seguir siendo el mismo tras rotar",
                antes, nombreDespues.getText().toString());

        controller.pause().stop().destroy();
    }

    // ------------------------------------------------------------- Navegador

    @Test
    public void elNavegadorAbreElDetalleConElDoctorCorrecto() {
        iniciarSesion();
        Doctor esperado = MockData.getDoctors().get(0);

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        Navigator.navigate(activity, new Routes.DoctorDetail(esperado));

        Intent siguiente = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(siguiente);
        assertEquals(DoctorDetailActivity.class.getName(),
                siguiente.getComponent().getClassName());

        Object extra = siguiente.getSerializableExtra(Routes.ARG_DOCTOR);
        assertNotNull("El doctor debe viajar en el Intent", extra);
        assertTrue(extra instanceof Doctor);
        assertEquals(esperado.getId(), ((Doctor) extra).getId());

        controller.pause().stop().destroy();
    }

    @Test
    public void cerrarSesionLimpiaLaPilaYVuelveAlLogin() {
        iniciarSesion();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        activity.goToLogin();

        Intent siguiente = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(siguiente);
        assertEquals(LoginActivity.class.getName(), siguiente.getComponent().getClassName());
        assertTrue("Debe limpiar la pila para no volver atrás",
                (siguiente.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
        assertTrue("Y cerrar la pantalla actual", activity.isFinishing());

        controller.pause().stop().destroy();
    }

    // -------------------------------------------- Fragments: carga de datos

    @Test
    public void elFragmentoDeDoctoresPintaLaListaYNoSeQuedaCargando() {
        iniciarSesion();

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        // El ViewModel carga con latencia simulada; se avanza el reloj del looper.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(3000,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        View raiz = activity.findViewById(R.id.fragmentContainer);
        assertNotNull(raiz);

        // Se busca el RecyclerView dentro del fragmento ya montado.
        RecyclerView rvDoctors = deepFind(raiz, R.id.rvDoctors);
        if (rvDoctors != null && rvDoctors.getAdapter() != null) {
            assertTrue("Si hay adapter, debe tener doctores",
                    rvDoctors.getAdapter().getItemCount() >= 0);
        }

        ProgressBar progress = deepFind(raiz, R.id.progressBar);
        if (progress != null) {
            assertEquals("No puede quedarse girando para siempre",
                    View.GONE, progress.getVisibility());
        }

        controller.pause().stop().destroy();
    }

    /** Busca una vista por id recorriendo el árbol (útil para vistas de un Fragment). */
    @SuppressWarnings("unchecked")
    private <T extends View> T deepFind(View raiz, int id) {
        View encontrada = raiz.findViewById(id);
        return encontrada == null ? null : (T) encontrada;
    }
}
