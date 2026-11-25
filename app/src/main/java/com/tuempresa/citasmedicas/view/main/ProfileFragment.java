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
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.viewmodel.AppointmentViewModel;
import com.tuempresa.citasmedicas.viewmodel.PatientViewModel;

import java.util.List;

/**
 * Fragmento de perfil del paciente. Combina los datos de la sesión con la
 * información del paciente (incluida la EDAD) obtenida de la API mock.
 */
public class ProfileFragment extends Fragment {

    private AppointmentViewModel appointmentViewModel;
    private PatientViewModel patientViewModel;
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

        // Información del paciente desde la API mock (datos generados por IA)
        patientViewModel = new ViewModelProvider(this).get(PatientViewModel.class);
        patientViewModel.getPatient().observe(getViewLifecycleOwner(), this::renderPatient);
        patientViewModel.loadPatient("pat_01");
    }

    private void renderPatient(Resource<Patient> resource) {
        if (resource == null) {
            return;
        }
        switch (resource.status) {
            case LOADING:
                tvInfoLoading.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                tvInfoLoading.setVisibility(View.GONE);
                Patient p = resource.data;
                if (p != null) {
                    // La edad registrada en la sesión manda; la de la API es respaldo.
                    int age = (currentUser != null && currentUser.getAge() > 0)
                            ? currentUser.getAge() : p.getAge();
                    tvInfoAge.setText(getResources().getQuantityString(
                            R.plurals.years, age, age));
                    tvInfoPhone.setText(safe(p.getPhone()));
                    tvInfoBlood.setText(safe(p.getBloodType()));
                    tvInfoInsurance.setText(safe(p.getInsurance()));
                    tvInfoAllergies.setText(safe(p.getAllergies()));
                }
                break;
            case ERROR:
                tvInfoLoading.setText(resource.message != null
                        ? resource.message : getString(R.string.loading_error));
                tvInfoLoading.setVisibility(View.VISIBLE);
                break;
        }
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
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).goToLogin();
        }
    }
}
