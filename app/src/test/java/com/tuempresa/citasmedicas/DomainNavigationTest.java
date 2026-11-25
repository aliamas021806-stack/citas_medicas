package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.domain.usecase.GetDoctorsUseCase;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.navigation.Routes;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pruebas de la capa de dominio y del grafo de navegación tipado.
 * Se ejecutan en JVM puro (sin Android, sin Robolectric).
 */
public class DomainNavigationTest {

    // ------------------------------------------------- capa domain: filtrado

    @Test
    public void getDoctorsUseCase_filtraPorNombreDeDoctor() {
        GetDoctorsUseCase useCase = new GetDoctorsUseCase(null);
        List<Doctor> source = MockData.getDoctors();

        final List<Doctor>[] out = new List[1];
        useCase.executeWithLocalData(source, "Ramírez", new com.tuempresa.citasmedicas.domain.UseCase
                .Callback<List<Doctor>>() {
            @Override
            public void onSuccess(List<Doctor> result) {
                out[0] = result;
            }

            @Override
            public void onError(String message) {
                out[0] = new ArrayList<>();
            }
        });

        assertNotNull(out[0]);
        assertFalse(out[0].isEmpty());
        for (Doctor d : out[0]) {
            assertTrue(d.getFullName().contains("Ramírez"));
        }
    }

    @Test
    public void getDoctorsUseCase_filtraPorEspecialidad() {
        GetDoctorsUseCase useCase = new GetDoctorsUseCase(null);
        final List<Doctor>[] out = new List[1];

        useCase.executeWithLocalData(MockData.getDoctors(), "cardio",
                new com.tuempresa.citasmedicas.domain.UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        out[0] = result;
                    }

                    @Override
                    public void onError(String message) {
                        out[0] = new ArrayList<>();
                    }
                });

        assertNotNull(out[0]);
        assertFalse("Debe encontrar cardiólogos", out[0].isEmpty());
        for (Doctor d : out[0]) {
            assertTrue(d.getSpecialtyName().toLowerCase().contains("cardio"));
        }
    }

    @Test
    public void getDoctorsUseCase_sinConsultaDevuelveTodo() {
        GetDoctorsUseCase useCase = new GetDoctorsUseCase(null);
        final List<Doctor>[] out = new List[1];
        List<Doctor> source = MockData.getDoctors();

        useCase.executeWithLocalData(source, "   ",
                new com.tuempresa.citasmedicas.domain.UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        out[0] = result;
                    }

                    @Override
                    public void onError(String message) {
                        out[0] = new ArrayList<>();
                    }
                });

        assertNotNull(out[0]);
        assertEquals(source.size(), out[0].size());
    }

    @Test
    public void getDoctorsUseCase_consultaSinResultadosDevuelveListaVacia() {
        GetDoctorsUseCase useCase = new GetDoctorsUseCase(null);
        final List<Doctor>[] out = new List[1];

        useCase.executeWithLocalData(MockData.getDoctors(), "zzzznoexiste",
                new com.tuempresa.citasmedicas.domain.UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        out[0] = result;
                    }

                    @Override
                    public void onError(String message) {
                        out[0] = new ArrayList<>();
                    }
                });

        assertNotNull(out[0]);
        assertTrue(out[0].isEmpty());
    }

    @Test
    public void getDoctorsUseCase_listaNulaNoLanzaExcepcion() {
        GetDoctorsUseCase useCase = new GetDoctorsUseCase(null);
        final List<Doctor>[] out = new List[1];

        useCase.executeWithLocalData(null, "cardiologia",
                new com.tuempresa.citasmedicas.domain.UseCase.Callback<List<Doctor>>() {
                    @Override
                    public void onSuccess(List<Doctor> result) {
                        out[0] = result;
                    }

                    @Override
                    public void onError(String message) {
                        out[0] = new ArrayList<>();
                    }
                });

        assertNotNull(out[0]);
        assertTrue(out[0].isEmpty());
    }

    // -------------------------------------------------- navegación tipada

    @Test
    public void routeDoctorDetail_transportaElDoctor() {
        Doctor doctor = MockData.getDoctorById("doc_01");
        Routes.DoctorDetail route = new Routes.DoctorDetail(doctor);

        assertSame(doctor, route.doctor());
        assertEquals(com.tuempresa.citasmedicas.view.appointment.DoctorDetailActivity.class,
                route.destination());
    }

    @Test
    public void routeLogin_apuntaAlLogin() {
        assertEquals(com.tuempresa.citasmedicas.view.auth.LoginActivity.class,
                new Routes.Login().destination());
    }

    @Test
    public void routeMain_apuntaAMainYGuardaLaPestana() {
        Routes.Main route = new Routes.Main(Routes.Tab.PROFILE);

        assertEquals(com.tuempresa.citasmedicas.view.main.MainActivity.class,
                route.destination());
        assertNotNull(route.initialTabId());
        assertEquals(Routes.Tab.PROFILE, route.initialTabId().intValue());
    }

    @Test
    public void routeMain_sinPestanaNoTieneTabInicial() {
        assertNull(new Routes.Main().initialTabId());
    }

    @Test
    public void navegador_resuelveLasTresPestanas() {
        assertNotNull(com.tuempresa.citasmedicas.navigation.Navigator
                .fragmentForTab(Routes.Tab.DOCTORS));
        assertNotNull(com.tuempresa.citasmedicas.navigation.Navigator
                .fragmentForTab(Routes.Tab.APPOINTMENTS));
        assertNotNull(com.tuempresa.citasmedicas.navigation.Navigator
                .fragmentForTab(Routes.Tab.PROFILE));
    }

    @Test
    public void navegador_pestanaDesconocidaDevuelveNull() {
        assertNull(com.tuempresa.citasmedicas.navigation.Navigator
                .fragmentForTab(-999));
    }

    @Test
    public void navegador_asignaTituloACadaPestana() {
        assertEquals(R.string.tab_doctors, com.tuempresa.citasmedicas.navigation.Navigator
                .titleForTab(Routes.Tab.DOCTORS));
        assertEquals(R.string.tab_appointments, com.tuempresa.citasmedicas.navigation.Navigator
                .titleForTab(Routes.Tab.APPOINTMENTS));
        assertEquals(R.string.profile_title, com.tuempresa.citasmedicas.navigation.Navigator
                .titleForTab(Routes.Tab.PROFILE));
    }

    @Test
    public void claveDeArgumentoDelDoctor_esEstable() {
        // Si cambiara, las pantallas dejarían de recibir el doctor.
        assertEquals("arg_doctor", Routes.ARG_DOCTOR);
        assertEquals(Routes.ARG_DOCTOR,
                com.tuempresa.citasmedicas.view.appointment.DoctorDetailActivity.EXTRA_DOCTOR);
    }
}
