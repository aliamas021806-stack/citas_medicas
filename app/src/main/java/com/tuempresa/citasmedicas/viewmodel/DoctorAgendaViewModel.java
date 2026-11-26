package com.tuempresa.citasmedicas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tuempresa.citasmedicas.data.firebase.FirestoreDoctorAgendaRepository;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel de la <b>agenda del médico</b>: las citas que le tocan hoy.
 * <p>
 * A diferencia del ViewModel del paciente, aquí no se observa Room: la fuente es
 * Firestore, porque un paciente puede haber agendado desde otro teléfono y esa cita
 * no existe en la base local del consultorio. Room queda como respaldo cuando no hay
 * red (lo resuelve {@link FirestoreDoctorAgendaRepository}).
 */
public class DoctorAgendaViewModel extends AndroidViewModel {

    private final FirestoreDoctorAgendaRepository repositorio;

    private final MutableLiveData<Resource<List<Appointment>>> agenda = new MutableLiveData<>();
    /** Fecha del día que se está mostrando, en formato {@code yyyy-MM-dd}. */
    private final MutableLiveData<String> dia = new MutableLiveData<>();
    /** {@code true} si los datos vienen de Firestore y no del respaldo local. */
    private final MutableLiveData<Boolean> deNube = new MutableLiveData<>(Boolean.FALSE);
    private final MutableLiveData<String> doctorId = new MutableLiveData<>();

    /** Todas las citas del médico (sin filtrar por día), ya ordenadas. */
    private List<Appointment> todas = new ArrayList<>();

    public DoctorAgendaViewModel(@NonNull Application application) {
        super(application);
        this.repositorio = new FirestoreDoctorAgendaRepository(application.getApplicationContext());
        this.dia.setValue(hoy());
    }

    public LiveData<Resource<List<Appointment>>> getAgenda() {
        return agenda;
    }

    public LiveData<String> getDia() {
        return dia;
    }

    public LiveData<Boolean> getDeNube() {
        return deNube;
    }

    public LiveData<String> getDoctorId() {
        return doctorId;
    }

    /** Fecha de hoy en el formato que usa la app para las citas. */
    @NonNull
    public static String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /**
     * Carga la agenda del médico en sesión.
     * <p>
     * El {@code doctorId} se toma de la sesión (lo escribe el resolutor de rol al
     * iniciar sesión). Si no hay vínculo, se informa con un mensaje claro en lugar de
     * mostrar una pantalla vacía sin explicación.
     */
    public void cargar() {
        String id = SessionManager.getInstance(getApplication()).getDoctorId();
        if (id == null || id.trim().isEmpty()) {
            agenda.setValue(Resource.error(
                    "Esta cuenta no está vinculada a ningún médico. "
                            + "Pide a un administrador que registre tu correo."));
            return;
        }
        doctorId.setValue(id);

        agenda.setValue(Resource.loading());
        repositorio.cargarAgenda(id, new FirestoreDoctorAgendaRepository.AgendaCallback() {
            @Override
            public void onAgenda(@NonNull List<Appointment> citas, boolean desdeNube) {
                deNube.setValue(desdeNube);
                todas = citas;
                publicarDelDia();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                agenda.setValue(Resource.error(mensaje));
            }
        });
    }

    /** Cambia el día que se está consultando (navegación día anterior / siguiente). */
    public void seleccionarDia(@NonNull String nuevoDia) {
        dia.setValue(nuevoDia);
        publicarDelDia();
    }

    /** Recalcula lo que se muestra para el día seleccionado. */
    private void publicarDelDia() {
        String actual = dia.getValue() != null ? dia.getValue() : hoy();
        List<Appointment> delDia = FirestoreDoctorAgendaRepository.deHoy(todas, actual);
        agenda.setValue(Resource.success(delDia));
    }

    /** Número de citas activas (no canceladas) de hoy. Útil para la cabecera. */
    public int activasDeHoy() {
        String actual = dia.getValue() != null ? dia.getValue() : hoy();
        return FirestoreDoctorAgendaRepository
                .activas(FirestoreDoctorAgendaRepository.deHoy(todas, actual)).size();
    }

    /** Total de citas que tiene el médico, de cualquier día. */
    public int total() {
        return todas.size();
    }

    /** Día anterior en formato {@code yyyy-MM-dd}. */
    @NonNull
    public static String diaAnterior(@NonNull String fecha) {
        return desplazar(fecha, -1);
    }

    /** Día siguiente en formato {@code yyyy-MM-dd}. */
    @NonNull
    public static String diaSiguiente(@NonNull String fecha) {
        return desplazar(fecha, 1);
    }

    /**
     * Suma días a una fecha {@code yyyy-MM-dd}.
     * <p>
     * Se usa {@code java.util.Calendar} y no {@code java.time} porque {@code java.time}
     * exige API 26 y el proyecto mantiene {@code minSdk 24}. Aunque el <i>desugaring</i>
     * lo reescribe, estas fechas también se calculan en pruebas unitarias donde el
     * desugaring no siempre está activo.
     */
    @NonNull
    static String desplazar(@NonNull String fecha, int dias) {
        try {
            SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            formato.setLenient(false);
            Date parsed = formato.parse(fecha);
            if (parsed == null) {
                return fecha;
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(parsed);
            cal.add(java.util.Calendar.DAY_OF_YEAR, dias);
            return formato.format(cal.getTime());
        } catch (java.text.ParseException e) {
            return fecha;
        }
    }

    /** Recarga manual (botón de refrescar / gesto de deslizar). */
    public void refrescar() {
        cargar();
    }

    @Nullable
    public List<Appointment> citasCargadas() {
        return todas;
    }
}
