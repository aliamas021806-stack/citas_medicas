package com.tuempresa.citasmedicas.view.main;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.navigation.Navigator;
import com.tuempresa.citasmedicas.navigation.Routes;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.viewmodel.DoctorViewModel;

import java.util.List;

/**
 * Fragmento de inicio: especialidades + doctores con buscador.
 * Muestra en la cabecera el saludo del paciente con su EDAD.
 */
public class DoctorsFragment extends Fragment {

    private DoctorViewModel viewModel;
    private SpecialtyAdapter specialtyAdapter;
    private DoctorAdapter doctorAdapter;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvGreeting, tvPatientHeaderAge;
    private MaterialButton btnRetry;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_doctors, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DoctorViewModel.class);

        progressBar = view.findViewById(R.id.progressBar);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnRetry = view.findViewById(R.id.btnRetry);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvPatientHeaderAge = view.findViewById(R.id.tvPatientHeaderAge);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        btnRetry.setOnClickListener(v -> {
            tvEmpty.setVisibility(View.GONE);
            btnRetry.setVisibility(View.GONE);
            viewModel.loadHome();
        });

        User user = SessionManager.getInstance(requireContext()).getUser();
        if (user != null) {
            tvGreeting.setText(getString(R.string.fmt_greeting, user.getFullName()));
            if (user.getAge() > 0) {
                tvPatientHeaderAge.setText(getResources().getQuantityString(
                        R.plurals.cake_years_specialist, user.getAge(), user.getAge()));
            } else {
                tvPatientHeaderAge.setText(R.string.fmt_specialist_only);
            }
        }

        RecyclerView rvSpecialties = view.findViewById(R.id.rvSpecialties);
        rvSpecialties.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        specialtyAdapter = new SpecialtyAdapter(this::onSpecialtySelected);
        rvSpecialties.setAdapter(specialtyAdapter);

        RecyclerView rvDoctors = view.findViewById(R.id.rvDoctors);
        rvDoctors.setLayoutManager(new LinearLayoutManager(getContext()));
        doctorAdapter = new DoctorAdapter(this::onDoctorSelected);
        rvDoctors.setAdapter(doctorAdapter);

        TextInputEditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.filterDoctors(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadHome();
            swipeRefresh.setRefreshing(false);
        });

        observeViewModel();
        viewModel.loadHome();
    }

    private void observeViewModel() {
        viewModel.getSpecialties().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                specialtyAdapter.submitList(resource.data);
            }
        });

        viewModel.getDoctors().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    progressBar.setVisibility(View.GONE);
                    List<Doctor> doctors = resource.data;
                    boolean empty = doctors == null || doctors.isEmpty();
                    doctorAdapter.submitList(doctors);
                    tvEmpty.setText(R.string.no_doctors_found);
                    tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    btnRetry.setVisibility(empty ? View.VISIBLE : View.GONE);
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setText(resource.message != null
                            ? resource.message : getString(R.string.loading_error));
                    tvEmpty.setVisibility(View.VISIBLE);
                    btnRetry.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void onSpecialtySelected(Specialty specialty) {
        viewModel.loadDoctorsBySpecialty(specialty.getId());
    }

    private void onDoctorSelected(Doctor doctor) {
        Navigator.navigate(requireActivity(), new Routes.DoctorDetail(doctor));
    }
}
