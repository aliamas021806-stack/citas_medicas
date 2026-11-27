package com.tuempresa.citasmedicas.view.doctor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.viewmodel.DoctorAgendaViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla del médico: <b>las citas que le tocan en el día</b>.
 * <p>
 * Se carga desde Firestore filtrada por su {@code doctorId}, así que aparecen las
 * citas que agendaron los pacientes desde <i>cualquier</i> teléfono, no sólo las que
 * existen en el móvil del consultorio. Con el selector de día se puede repasar la
 * agenda de ayer o de mañana sin pedir otra consulta: todas las citas del médico se
 * traen una vez y el filtro por fecha se hace en memoria.
 */
public class AgendaDoctorFragment extends Fragment {

    private DoctorAgendaViewModel viewModel;
    private AgendaDoctorAdapter adapter;

    private RecyclerView rvAgenda;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvSummary, tvDia, tvEmptyTitle, tvEmptyHint;
    private MaterialButton btnReintentar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agenda_doctor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvAgenda = view.findViewById(R.id.rvAgenda);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        progressBar = view.findViewById(R.id.progressBar);
        emptyState = view.findViewById(R.id.emptyState);
        tvSummary = view.findViewById(R.id.tvSummary);
        tvDia = view.findViewById(R.id.tvDia);
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tvEmptyHint = view.findViewById(R.id.tvEmptyHint);
        btnReintentar = view.findViewById(R.id.btnReintentar);

        adapter = new AgendaDoctorAdapter(
                getString(R.string.agenda_seccion_pendientes),
                getString(R.string.agenda_seccion_atendidas),
                new AgendaDoctorAdapter.OnCitaAccion() {
                    @Override
                    public void onMarcarAtendida(@NonNull Appointment cita) {
                        viewModel.cambiarEstado(cita, AppointmentStatus.ATTENDED);
                        avisar(R.string.agenda_atendida_ok);
                    }

                    @Override
                    public void onMarcarNoShow(@NonNull Appointment cita) {
                        viewModel.cambiarEstado(cita, AppointmentStatus.NO_SHOW);
                        avisar(R.string.agenda_no_show_ok);
                    }
                });
        rvAgenda.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAgenda.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(DoctorAgendaViewModel.class);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary);
            swipeRefresh.setOnRefreshListener(() -> viewModel.refrescar());
        }
        if (btnReintentar != null) {
            btnReintentar.setOnClickListener(v -> viewModel.refrescar());
        }

        view.findViewById(R.id.btnDiaAnterior).setOnClickListener(v -> {
            String actual = viewModel.getDia().getValue();
            if (actual != null) {
                viewModel.seleccionarDia(DoctorAgendaViewModel.diaAnterior(actual));
            }
        });
        view.findViewById(R.id.btnDiaSiguiente).setOnClickListener(v -> {
            String actual = viewModel.getDia().getValue();
            if (actual != null) {
                viewModel.seleccionarDia(DoctorAgendaViewModel.diaSiguiente(actual));
            }
        });
        view.findViewById(R.id.btnHoy).setOnClickListener(v ->
                viewModel.seleccionarDia(DoctorAgendaViewModel.hoy()));

        viewModel.getAgenda().observe(getViewLifecycleOwner(), this::render);
        viewModel.getDia().observe(getViewLifecycleOwner(), this::renderDia);
        viewModel.getDeNube().observe(getViewLifecycleOwner(), this::renderOrigen);

        // Las dos secciones se pintan juntas: cada vez que cambia una de las dos listas
        // se vuelve a montar la pantalla completa, para que la cabecera de sección
        // aparezca y desaparezca con el contenido real.
        viewModel.getPendientes().observe(getViewLifecycleOwner(), p -> pintarSecciones());
        viewModel.getAtendidas().observe(getViewLifecycleOwner(), a -> pintarSecciones());

        viewModel.getErrorEstado().observe(getViewLifecycleOwner(), mensaje -> {
            if (mensaje != null && !mensaje.trim().isEmpty()) {
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show();
                viewModel.limpiarErrorEstado();
            }
        });

        // Si Firestore rechazó la lectura, el estado vacío debe decir eso y no «no tienes
        // citas»: son dos situaciones distintas y confundirlas fue el origen del reporte.
        viewModel.getAvisoLectura().observe(getViewLifecycleOwner(), this::renderAvisoLectura);

        viewModel.cargar();
    }

    /** Guarda el aviso de lectura para que el estado vacío no mienta. */
    @Nullable
    private String avisoLectura;

    /**
     * Refleja si Firestore permitió leer la agenda.
     * <p>
     * El texto se recuerda y se reevalúa el estado vacío, porque el aviso llega después
     * de las listas y sin esto la pantalla podría quedarse con el mensaje genérico.
     */
    private void renderAvisoLectura(@Nullable String aviso) {
        this.avisoLectura = (aviso != null && !aviso.trim().isEmpty()) ? aviso : null;
        if (this.avisoLectura != null) {
            if (tvEmptyHint != null) {
                tvEmptyHint.setText(this.avisoLectura);
                tvEmptyHint.setVisibility(View.VISIBLE);
            }
            if (tvSummary != null) {
                tvSummary.setText(R.string.agenda_subtitle_sin_permiso);
            }
        }
    }

    /**
     * Pinta las dos secciones a partir de los LiveData del ViewModel.
     * <p>
     * Se leen los dos valores actuales en lugar de recibir uno por parámetro porque las
     * secciones deben dibujarse siempre juntas: pintar sólo la que cambió dejaría una
     * cabecera huérfana o una tarjeta duplicada.
     */
    private void pintarSecciones() {
        List<Appointment> pendientes = viewModel.getPendientes().getValue();
        List<Appointment> atendidas = viewModel.getAtendidas().getValue();
        if (pendientes == null) {
            pendientes = new ArrayList<>();
        }
        if (atendidas == null) {
            atendidas = new ArrayList<>();
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (btnReintentar != null) {
            btnReintentar.setVisibility(View.GONE);
        }

        boolean vacio = pendientes.isEmpty() && atendidas.isEmpty();
        if (vacio) {
            adapter.submit(new ArrayList<>(), new ArrayList<>());
            if (rvAgenda != null) {
                rvAgenda.setVisibility(View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.VISIBLE);
            }
            if (tvEmptyTitle != null) {
                tvEmptyTitle.setText(avisoLectura != null
                        ? R.string.agenda_lectura_bloqueada
                        : R.string.agenda_sin_citas);
            }
            if (tvEmptyHint != null) {
                tvEmptyHint.setVisibility(View.VISIBLE);
                tvEmptyHint.setText(avisoLectura != null
                        ? avisoLectura : getString(R.string.agenda_sin_citas_hint));
            }
            return;
        }

        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }
        if (rvAgenda != null) {
            rvAgenda.setVisibility(View.VISIBLE);
        }
        adapter.submit(pendientes, atendidas);

        if (tvSummary != null) {
            tvSummary.setText(getResources().getQuantityString(R.plurals.agenda_resumen,
                    pendientes.size(), pendientes.size(), pendientes.size() + atendidas.size()));
        }
    }

    /** Aviso breve de que la acción se ha aplicado. */
    private void avisar(int stringRes) {
        if (isAdded()) {
            Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderDia(@Nullable String dia) {
        if (tvDia == null || dia == null || dia.isEmpty()) {
            return;
        }
        String texto = AgendaDoctorAdapter.formatearFecha(dia);
        tvDia.setText(dia.equals(DoctorAgendaViewModel.hoy())
                ? getString(R.string.agenda_hoy_fecha, texto)
                : texto);
    }

    private void renderOrigen(@Nullable Boolean deNube) {
        if (tvSummary == null || deNube == null) {
            return;
        }
        tvSummary.setText(deNube
                ? R.string.agenda_subtitle
                : R.string.agenda_subtitle_local);
    }

    private void render(@Nullable Resource<List<Appointment>> recurso) {
        if (recurso == null) {
            return;
        }
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }

        if (recurso.status == Resource.Status.LOADING) {
            mostrarCargando();
            return;
        }

        if (recurso.status == Resource.Status.ERROR) {
            mostrarError(recurso.message);
            return;
        }

        mostrarLista(recurso.data);
    }

    private void mostrarCargando() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }
        if (rvAgenda != null) {
            rvAgenda.setVisibility(View.GONE);
        }
    }

    private void mostrarError(@Nullable String mensaje) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (rvAgenda != null) {
            rvAgenda.setVisibility(View.GONE);
        }
        adapter.submit(new ArrayList<>(), new ArrayList<>());
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(mensaje != null && !mensaje.trim().isEmpty()
                    ? mensaje : getString(R.string.loading_error));
        }
        if (tvEmptyHint != null) {
            tvEmptyHint.setVisibility(View.GONE);
        }
        if (btnReintentar != null) {
            btnReintentar.setVisibility(View.VISIBLE);
        }
    }

    private void mostrarLista(@Nullable List<Appointment> citas) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        // El contenido real lo pinta pintarSecciones(), que es quien conoce las dos
        // secciones. Aquí sólo se deja constancia de que la carga terminó.
        pintarSecciones();
    }
}
