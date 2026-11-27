package com.tuempresa.citasmedicas.view.admin;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.data.firebase.FirebaseConfig;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.viewmodel.AdminDoctoresViewModel;

import java.util.List;

/**
 * Alta de médicos desde la app (sólo administrador).
 * <p>
 * Sustituye a tener que abrir la consola de Firebase y crear el documento a mano:
 * se rellena el formulario, se guarda y el médico aparece de inmediato en el catálogo
 * que ven los pacientes. Cuando ese médico se registre en la app con el correo que se
 * escribió aquí, su cuenta quedará vinculada a la ficha y verá su propia agenda.
 */
public class AdminDoctoresFragment extends Fragment {

    private AdminDoctoresViewModel viewModel;
    private AdminMedicoAdapter adapter;

    private TextInputEditText etNombre, etEmail, etHospital, etEdad,
            etAnios, etTarifa, etBio;
    /**
     * Especialidad: lista desplegable con el catálogo real, no texto libre.
     * <p>
     * Con texto libre se podía escribir cualquier cosa y la ficha quedaba con un
     * {@code specialtyId} que no existía en {@code specialties}, así que el médico no
     * aparecía al filtrar por esa especialidad.
     */
    private com.google.android.material.textfield.MaterialAutoCompleteTextView
            acEspecialidad;
    private MaterialButton btnRegistrar;
    private ProgressBar progress;
    private RecyclerView rvMedicos;
    private TextView tvVacio;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_doctores, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etNombre = view.findViewById(R.id.etAdminNombre);
        etEmail = view.findViewById(R.id.etAdminEmail);
        acEspecialidad = view.findViewById(R.id.acAdminEspecialidad);
        etHospital = view.findViewById(R.id.etAdminHospital);
        etEdad = view.findViewById(R.id.etAdminEdad);
        etAnios = view.findViewById(R.id.etAdminAnios);
        etTarifa = view.findViewById(R.id.etAdminTarifa);
        etBio = view.findViewById(R.id.etAdminBio);
        btnRegistrar = view.findViewById(R.id.btnRegistrarMedico);
        progress = view.findViewById(R.id.progressAdmin);
        rvMedicos = view.findViewById(R.id.rvMedicos);
        tvVacio = view.findViewById(R.id.tvAdminVacio);

        adapter = new AdminMedicoAdapter();
        rvMedicos.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMedicos.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AdminDoctoresViewModel.class);
        viewModel.getMedicos().observe(getViewLifecycleOwner(), this::renderMedicos);
        viewModel.getAlta().observe(getViewLifecycleOwner(), this::renderAlta);
        viewModel.getEspecialidades().observe(getViewLifecycleOwner(), this::renderEspecialidades);

        btnRegistrar.setOnClickListener(v -> registrar());
        viewModel.cargarMedicos();
        viewModel.cargarEspecialidades();
    }

    /**
     * Rellena el desplegable de especialidades con el catálogo real.
     * <p>
     * Se ofrece como sugerencia, no como imposición: si el catálogo viene vacío (sin
     * red) el campo sigue aceptando texto, para que el alta no quede bloqueada.
     */
    private void renderEspecialidades(@Nullable List<String> nombres) {
        if (acEspecialidad == null || nombres == null || nombres.isEmpty()) {
            return;
        }
        acEspecialidad.setSimpleItems(nombres.toArray(new String[0]));
    }

    private String texto(TextInputEditText campo) {
        if (campo == null || campo.getText() == null) {
            return "";
        }
        return campo.getText().toString().trim();
    }

    private int entero(TextInputEditText campo) {
        try {
            return Integer.parseInt(texto(campo));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double decimal(TextInputEditText campo) {
        try {
            return Double.parseDouble(texto(campo).replace(",", "."));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private void registrar() {
        String nombre = texto(etNombre);
        if (nombre.isEmpty()) {
            aviso(getString(R.string.admin_error_nombre));
            return;
        }
        String email = texto(etEmail);
        if (email.isEmpty() || !email.contains("@")) {
            aviso(getString(R.string.admin_error_email));
            return;
        }
        String especialidad = acEspecialidad != null
                && acEspecialidad.getText() != null
                ? acEspecialidad.getText().toString().trim() : "";
        if (especialidad.isEmpty()) {
            aviso(getString(R.string.admin_error_especialidad));
            return;
        }
        // El alta escribe en Firestore: sin configuración no hay nada que guardar.
        if (!FirebaseConfig.isEnabled()) {
            aviso(getString(R.string.admin_requiere_firebase));
            return;
        }

        viewModel.registrarMedico(nombre, email, especialidad, texto(etHospital),
                entero(etEdad), entero(etAnios), decimal(etTarifa), texto(etBio));
    }

    private void renderMedicos(@Nullable Resource<List<Doctor>> recurso) {
        if (recurso == null) {
            return;
        }
        if (recurso.status == Resource.Status.LOADING) {
            if (progress != null) {
                progress.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (progress != null && recurso.status != Resource.Status.LOADING) {
            // El progreso del alta se controla en renderAlta; aquí no se toca.
        }
        List<Doctor> lista = recurso.data;
        if (lista == null || lista.isEmpty()) {
            adapter.submitList(null);
            if (tvVacio != null) {
                tvVacio.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (tvVacio != null) {
            tvVacio.setVisibility(View.GONE);
        }
        adapter.submitList(lista);
    }

    private void renderAlta(@Nullable Resource<String> recurso) {
        if (recurso == null) {
            return;
        }
        if (progress != null) {
            progress.setVisibility(recurso.status == Resource.Status.LOADING
                    ? View.VISIBLE : View.GONE);
        }
        if (btnRegistrar != null) {
            btnRegistrar.setEnabled(recurso.status != Resource.Status.LOADING);
        }
        if (recurso.status == Resource.Status.SUCCESS) {
            String correo = recurso.data != null ? recurso.data : "";
            aviso(getString(R.string.admin_guardado, correo));
            limpiarFormulario();
            viewModel.limpiarAlta();
        } else if (recurso.status == Resource.Status.ERROR) {
            aviso(recurso.message != null ? recurso.message : getString(R.string.loading_error));
            viewModel.limpiarAlta();
        }
    }

    private void limpiarFormulario() {
        TextInputEditText[] campos = {etNombre, etEmail, etHospital,
                etEdad, etAnios, etTarifa, etBio};
        for (TextInputEditText campo : campos) {
            if (campo != null) {
                campo.setText("");
            }
        }
        if (acEspecialidad != null) {
            acEspecialidad.setText("");
        }
    }

    private void aviso(String mensaje) {
        if (getContext() != null && mensaje != null) {
            Toast.makeText(getContext(), mensaje, Toast.LENGTH_LONG).show();
        }
    }
}
