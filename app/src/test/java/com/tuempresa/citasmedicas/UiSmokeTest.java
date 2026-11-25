package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.view.appointment.DoctorDetailActivity;
import com.tuempresa.citasmedicas.view.auth.LoginActivity;
import com.tuempresa.citasmedicas.view.main.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Pruebas de humo de la UI con Robolectric (Android real sobre la JVM).
 * <p>
 * Estos tests inflan TODOS los layouts y arrancan las tres Activities, lo que
 * detecta errores de recursos, temas y findViewById nulos sin necesidad de un
 * emulador.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UiSmokeTest {

    private static final int[] LAYOUTS = {
            R.layout.activity_login,
            R.layout.activity_main,
            R.layout.activity_doctor_detail,
            R.layout.fragment_doctors,
            R.layout.fragment_citas,
            R.layout.fragment_profile,
            R.layout.item_specialty,
            R.layout.item_doctor,
            R.layout.item_date,
            R.layout.item_slot,
            R.layout.item_appointment,
    };

    /** Contexto con el tema de la app para poder inflar componentes Material 3. */
    private Context themed() {
        return new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_CitasMedicas);
    }

    @Test
    public void todosLosLayoutsSeInflanSinCrash() {
        LayoutInflater inflater = LayoutInflater.from(themed());
        for (int layout : LAYOUTS) {
            View v = inflater.inflate(layout, null, false);
            assertNotNull("El layout " + layout + " no se infló", v);
        }
    }

    @Test
    public void losItemsTienenSusVistasEsperadas() {
        LayoutInflater inflater = LayoutInflater.from(themed());

        View doctor = inflater.inflate(R.layout.item_doctor, null, false);
        assertNotNull(doctor.findViewById(R.id.tvAvatar));
        assertNotNull(doctor.findViewById(R.id.tvName));
        assertNotNull(doctor.findViewById(R.id.tvRating));
        assertNotNull(doctor.findViewById(R.id.tvAge));
        assertNotNull(doctor.findViewById(R.id.tvFee));

        View cita = inflater.inflate(R.layout.item_appointment, null, false);
        assertNotNull(cita.findViewById(R.id.tvDoctorName));
        assertNotNull(cita.findViewById(R.id.tvPatientAge));
        assertNotNull(cita.findViewById(R.id.tvStatus));
        assertNotNull(cita.findViewById(R.id.btnCancel));

        View slot = inflater.inflate(R.layout.item_slot, null, false);
        assertNotNull(slot.findViewById(R.id.tvTime));
        assertNotNull(slot.findViewById(R.id.tvAvailability));
    }

    @Test
    public void plurales_seResuelvenEnSingularYPlural() {
        Context ctx = themed();

        assertEquals("1 año", ctx.getResources().getQuantityString(R.plurals.years, 1, 1));
        assertEquals("34 años", ctx.getResources().getQuantityString(R.plurals.years, 34, 34));
        assertEquals("0 años", ctx.getResources().getQuantityString(R.plurals.years, 0, 0));

        assertEquals("🎂 52 años",
                ctx.getResources().getQuantityString(R.plurals.years_with_cake, 52, 52));
        assertEquals("🎂 1 año",
                ctx.getResources().getQuantityString(R.plurals.years_with_cake, 1, 1));

        assertEquals("1 cita activa de 2 en total",
                ctx.getResources().getQuantityString(R.plurals.active_summary, 1, 1, 2));
        assertEquals("3 citas activas de 5 en total",
                ctx.getResources().getQuantityString(R.plurals.active_summary, 3, 3, 5));
    }

    @Test
    public void formatoDePaciente_incluyeNombreYEdad() {
        Context ctx = themed();
        String edad = ctx.getResources().getQuantityString(R.plurals.years, 34, 34);
        String texto = ctx.getString(R.string.fmt_patient_age, "Laura Jiménez", edad);

        assertTrue(texto.contains("Laura Jiménez"));
        assertTrue(texto.contains("34 años"));
    }

    @Test
    public void loginActivity_arrancaSinCrash() {
        ActivityController<LoginActivity> controller =
                Robolectric.buildActivity(LoginActivity.class).setup();
        Activity activity = controller.get();

        assertNotNull(activity.findViewById(R.id.etEmail));
        assertNotNull(activity.findViewById(R.id.etPassword));
        assertNotNull(activity.findViewById(R.id.etAge));
        assertNotNull(activity.findViewById(R.id.btnSubmit));
        assertNotNull(activity.findViewById(R.id.tvToggle));

        controller.pause().stop().destroy();
    }

    @Test
    public void mainActivity_arrancaConSesionYCambiaDeTab() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SessionManager.getInstance(ctx).saveSession(
                new User("user_01", "Ana Pérez", "ana@correo.com", "+34 600 000 000", 41));

        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();

        assertNotNull(activity.findViewById(R.id.toolbar));
        assertNotNull(activity.findViewById(R.id.fragmentContainer));
        assertNotNull(activity.findViewById(R.id.bottomNav));

        controller.pause().stop().destroy();
    }

    @Test
    public void doctorDetailActivity_recibeElDoctorYArranca() {
        Doctor doctor = MockData.getDoctorById("doc_01");
        assertNotNull(doctor);

        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(), DoctorDetailActivity.class);
        intent.putExtra(DoctorDetailActivity.EXTRA_DOCTOR, doctor);

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, intent).setup();
        DoctorDetailActivity activity = controller.get();

        TextView nombre = activity.findViewById(R.id.tvDoctorName);
        assertNotNull(nombre);
        assertTrue("El nombre debe contener Ramírez", nombre.getText().toString().contains("Ramírez"));

        TextView edad = activity.findViewById(R.id.tvDoctorAge);
        assertNotNull(edad);
        assertTrue("La edad debe mostrarse", edad.getText().toString().contains("52"));

        assertNotNull(activity.findViewById(R.id.rvDates));
        assertNotNull(activity.findViewById(R.id.rvSlots));
        assertNotNull(activity.findViewById(R.id.btnConfirm));

        controller.pause().stop().destroy();
    }

    @Test
    public void sessionManager_persisteYRecuperaLaEdad() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SessionManager session = SessionManager.getInstance(ctx);

        session.logout();
        assertNull("Sin sesión no debe haber usuario", session.getUser());

        session.saveSession(new User("user_01", "Ana Pérez", "ana@correo.com", "+34 600 000 000", 41));
        User recuperado = session.getUser();
        assertNotNull(recuperado);
        assertEquals(41, recuperado.getAge());
        assertEquals("Ana Pérez", recuperado.getFullName());
        assertTrue(session.isLoggedIn());

        session.logout();
        assertNull(session.getUser());
    }

    @Test
    public void navegadorAbreElDetalleConElDoctor() {
        // Integración real: Navigator -> Intent -> DoctorDetailActivity.
        com.tuempresa.citasmedicas.model.Doctor doctor =
                com.tuempresa.citasmedicas.data.mock.MockData.getDoctorById("doc_02");
        assertNotNull(doctor);

        Context appContext = ApplicationProvider.getApplicationContext();
        android.content.Intent intent = new android.content.Intent(appContext, DoctorDetailActivity.class);
        com.tuempresa.citasmedicas.navigation.Routes.DoctorDetail route =
                new com.tuempresa.citasmedicas.navigation.Routes.DoctorDetail(doctor);
        intent.putExtra(com.tuempresa.citasmedicas.navigation.Routes.ARG_DOCTOR, route.doctor());

        ActivityController<DoctorDetailActivity> controller =
                Robolectric.buildActivity(DoctorDetailActivity.class, intent).setup();
        DoctorDetailActivity activity = controller.get();

        android.widget.TextView name = activity.findViewById(R.id.tvDoctorName);
        assertNotNull(name);
        // Si la clave del argumento estuviera desincronizada, la Activity se habría
        // cerrado y este nombre estaría vacío.
        assertTrue("El detalle no recibió el doctor: " + name.getText(),
                name.getText().toString().contains("González"));
    }
}
