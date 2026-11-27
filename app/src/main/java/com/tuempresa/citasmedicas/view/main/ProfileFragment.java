package com.tuempresa.citasmedicas.view.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.data.RepositoryProvider;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.viewmodel.AppointmentViewModel;

import java.util.List;

/**
 * Fragmento de perfil del paciente. Combina los datos de la sesión con la
 * información del paciente (incluida la EDAD) obtenida de la API mock.
 */
public class ProfileFragment extends Fragment {

    private AppointmentViewModel appointmentViewModel;
    private User currentUser;

    private TextView tvAvatar, tvName, tvEmail, tvAge, tvUpcoming, tvTotal, tvDataSource;
    private TextView tvInfoAge, tvInfoPhone, tvInfoBlood, tvInfoInsurance, tvInfoAllergies, tvInfoLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvName = view.findViewById(R.id.tvName);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvAge = view.findViewById(R.id.tvAge);
        tvDataSource = view.findViewById(R.id.tvDataSource);
        tvUpcoming = view.findViewById(R.id.tvUpcomingCount);
        tvTotal = view.findViewById(R.id.tvTotalCount);

        tvInfoAge = view.findViewById(R.id.tvInfoAge);
        tvInfoPhone = view.findViewById(R.id.tvInfoPhone);
        tvInfoBlood = view.findViewById(R.id.tvInfoBlood);
        tvInfoInsurance = view.findViewById(R.id.tvInfoInsurance);
        tvInfoAllergies = view.findViewById(R.id.tvInfoAllergies);
        tvInfoLoading = view.findViewById(R.id.tvInfoLoading);

        User user = SessionManager.getInstance(requireContext()).getUser();
        currentUser = user;
        if (user != null) {
            tvAvatar.setText(user.getInitials());
            tvName.setText(user.getFullName());
            tvEmail.setText(user.getEmail());
            if (user.getAge() > 0) {
                tvAge.setText(getResources().getQuantityString(
                        R.plurals.years, user.getAge(), user.getAge()));
            } else {
                tvAge.setText(R.string.age_not_registered);
            }
        }

        // Indicador del origen de datos: ayuda a verificar la conexión con Firebase.
        if (tvDataSource != null) {
            boolean firebase = com.tuempresa.citasmedicas.data.RepositoryProvider.isUsingFirebase();
            tvDataSource.setText(getString(
                    firebase ? R.string.data_source_firebase : R.string.data_source_mock,
                    com.tuempresa.citasmedicas.data.RepositoryProvider.describeDataSource()));
        }

        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> logout());

        // Citas
        appointmentViewModel = new ViewModelProvider(this).get(AppointmentViewModel.class);
        appointmentViewModel.getAppointments().observe(getViewLifecycleOwner(), this::updateStats);

        // El expediente se pinta con los datos del usuario en sesión. Antes se pedía
        // siempre el paciente "pat_01" (Laura Jiménez) del mock, así que el perfil
        // mostraba la información de otra persona.
        renderPatientFromSession();
        sincronizarExpedienteConFirestore();
    }

    /**
     * Pinta el expediente a partir de la sesión.
     * <p>
     * Es la fuente de verdad para el perfil: son exactamente los datos que el
     * paciente escribió al registrarse.
     */
    private void renderPatientFromSession() {
        if (currentUser == null) {
            return;
        }
        if (tvInfoLoading != null) {
            tvInfoLoading.setVisibility(View.GONE);
        }
        int age = currentUser.getAge() > 0 ? currentUser.getAge() : 0;
        if (tvInfoAge != null) {
            tvInfoAge.setText(age > 0
                    ? getResources().getQuantityString(R.plurals.years, age, age)
                    : getString(R.string.value_placeholder));
        }
        if (tvInfoPhone != null) {
            tvInfoPhone.setText(safe(currentUser.getPhone()));
        }
        if (tvInfoBlood != null) {
            tvInfoBlood.setText(safe(currentUser.getBloodType()));
        }
        if (tvInfoInsurance != null) {
            tvInfoInsurance.setText(safe(currentUser.getInsurance()));
        }
        if (tvInfoAllergies != null) {
            tvInfoAllergies.setText(safe(currentUser.getAllergies()));
        }
    }

    /**
     * Copia el expediente a Firestore (colección {@code patients}) para que quede
     * también en la nube.
     * <p>
     * Es <i>best-effort</i>: el perfil ya se ha pintado desde la sesión, así que un
     * fallo de red no deja la pantalla en blanco. El documento usa como id el id de
     * Firebase del usuario, de modo que cada paciente tiene un único expediente y
     * volver a iniciar sesión no crea duplicados.
     * <p>
     * <b>Un médico NO sincroniza expediente.</b> Antes sí lo hacía, y como el formulario
     * de registro no pide teléfono, grupo sanguíneo ni alergias cuando eliges «Médico»,
     * esas escrituras dejaban un documento de paciente vacío con la misma identidad.
     * Además el perfil del médico debe mostrar los datos de su ficha de {@code doctors}
     * (edad 35, especialidad, hospital), no un expediente clínico que nunca rellenó.
     */
    private void sincronizarExpedienteConFirestore() {
        if (currentUser == null) {
            return;
        }
        // Sólo los pacientes tienen expediente clínico.
        if (currentUser.getRole() != null && currentUser.getRole().isDoctor()) {
            return;
        }
        if (currentUser.getRole() != null && currentUser.getRole().isAdmin()) {
            return;
        }
        String userId = currentUser.getId();
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        RepositoryProvider.guardarExpediente(requireContext().getApplicationContext(),
                userId, currentUser);
    }

    private String safe(String s) {
        if (s == null || s.trim().isEmpty()) {
            return getString(R.string.value_placeholder);
        }
        return s;
    }

    private void updateStats(List<Appointment> list) {
        int total = list != null ? list.size() : 0;
        int upcoming = 0;
        if (list != null) {
            for (Appointment e : list) {
                if (e.getStatus() == AppointmentStatus.CONFIRMED) {
                    upcoming++;
                }
            }
        }
        tvTotal.setText(String.valueOf(total));
        tvUpcoming.setText(String.valueOf(upcoming));
    }

    private void logout() {
        SessionManager.getInstance(requireContext()).logout();
        // Se cierra la base local para que el siguiente usuario que entre abra una
        // instancia limpia y no pueda ver en memoria las citas del anterior.
        com.tuempresa.citasmedicas.data.local.AppDatabase.reset();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).goToLogin();
        }
    }
}
