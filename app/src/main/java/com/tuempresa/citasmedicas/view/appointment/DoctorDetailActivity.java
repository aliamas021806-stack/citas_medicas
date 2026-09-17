package com.tuempresa.citasmedicas.view.appointment;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.TimeSlot;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.viewmodel.AppointmentViewModel;
import com.tuempresa.citasmedicas.viewmodel.DoctorViewModel;
import com.tuempresa.citasmedicas.viewmodel.PatientViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Detalle del doctor + selección de fecha/hora para agendar la cita.
 * <p>
 * Integra la EDAD del PACIENTE obtenida desde la API mock (datos generados por IA),
 * usándola como dato por defecto al confirmar la cita.
 */
public class DoctorDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DOCTOR = "extra_doctor";
    /** Paciente por defecto usado para recuperar la edad desde la API mock. */
    private static final String DEFAULT_PATIENT_ID = "pat_01";

    private Doctor doctor;
    private User sessionUser;

    private DateAdapter dateAdapter;
    private SlotAdapter slotAdapter;

    private DoctorViewModel doctorViewModel;
    private AppointmentViewModel appointmentViewModel;
    private PatientViewModel patientViewModel;

    private RecyclerView rvDates, rvSlots;
    private TextInputEditText etReason;
    private ProgressBar progressBar;
    private TextView tvDoctorAge;
    private com.google.android.material.button.MaterialButton btnConfirm;

    /** Evita crear la cita dos veces si el usuario toca varias veces el botón. */
    private boolean isSubmitting = false;

    /** Edad del paciente resuelta: primero desde la API, con respaldo en la sesión. */
    private int patientAge;
    /** Nombre del paciente que se guardará en la cita. */
    private String patientName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_detail);

        doctor = (Doctor) getIntent().getSerializableExtra(EXTRA_DOCTOR);
        if (doctor == null) {
            finish();
            return;
        }

        sessionUser = SessionManager.getInstance(this).getUser();
        patientName = sessionUser != null && sessionUser.getFullName() != null
                ? sessionUser.getFullName() : "Paciente";
        patientAge = sessionUser != null ? sessionUser.getAge() : 0;

        bindViews();
        bindDoctor();
        setupLists();
        setupViewModels();

        loadPatientFromApi();
        loadSlotsForSelectedDate(dateAdapter.getSelectedDate());
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        toolbar.setTitle(R.string.book_appointment);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvDoctorAge = findViewById(R.id.tvDoctorAge);
        rvDates = findViewById(R.id.rvDates);
        rvSlots = findViewById(R.id.rvSlots);
        etReason = findViewById(R.id.etReason);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnConfirm).setOnClickListener(v -> onConfirmClicked());
        btnConfirm = findViewById(R.id.btnConfirm);
    }

    private void bindDoctor() {
        ((TextView) findViewById(R.id.tvDoctorAvatar)).setText(doctor.getInitials());
        ((TextView) findViewById(R.id.tvDoctorName)).setText(doctor.getDisplayName());
        ((TextView) findViewById(R.id.tvDoctorSpecialty)).setText(doctor.getSpecialtyName());
        ((TextView) findViewById(R.id.tvDoctorRating)).setText("\u2b50 " + doctor.getRating()
                + "  \u00b7  " + doctor.getYearsExperience() + " a\u00f1os de experiencia");
        tvDoctorAge.setText("\ud83c\udf82 " + doctor.getAge() + " a\u00f1os");
        ((TextView) findViewById(R.id.tvDoctorBio)).setText(doctor.getBio());
    }

    private void setupLists() {
        List<String> dates = MockData.getSelectableDates();
        dateAdapter = new DateAdapter(dates, date -> loadSlotsForSelectedDate(date));
        rvDates.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDates.setAdapter(dateAdapter);

        slotAdapter = new SlotAdapter(new ArrayList<>(), slot -> {
            // El slot queda seleccionado en el adaptador; no requiere acción extra.
        });
        rvSlots.setLayoutManager(new LinearLayoutManager(this));
        rvSlots.setAdapter(slotAdapter);
    }

    private void setupViewModels() {
        doctorViewModel = new ViewModelProvider(this).get(DoctorViewModel.class);
        appointmentViewModel = new ViewModelProvider(this).get(AppointmentViewModel.class);
        patientViewModel = new ViewModelProvider(this).get(PatientViewModel.class);

        doctorViewModel.getSlots().observe(this, this::renderSlots);

        patientViewModel.getPatient().observe(this, this::renderPatient);

        appointmentViewModel.getActionState().observe(this, this::renderAction);
        appointmentViewModel.getNavigateBack().observe(this, created -> {
            if (Boolean.TRUE.equals(created)) {
                // Consumimos el evento para que no se vuelva a disparar al rotar la pantalla
                appointmentViewModel.clearNavigateBack();
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, R.string.appointment_confirmed, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    /**
     * Recupera la información del paciente (incluida su EDAD) desde la API mock.
     * Si la API no responde, se conserva la edad de la sesión.
     */
    private void loadPatientFromApi() {
        patientViewModel.loadPatient(DEFAULT_PATIENT_ID);
    }

    private void renderPatient(@Nullable Resource<Patient> resource) {
        if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) {
            return;
        }
        Patient patient = resource.data;
        // La edad proviene de los datos generados por IA en la API mock.
        patientAge = patient.getAge();
    }

    private void loadSlotsForSelectedDate(@Nullable String date) {
        if (date == null) {
            return;
        }
        doctorViewModel.loadSlots(doctor.getId());
    }

    private void renderSlots(@Nullable Resource<List<TimeSlot>> resource) {
        if (resource == null) {
            return;
        }
        switch (resource.status) {
            case LOADING:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                progressBar.setVisibility(View.GONE);
                String selectedDate = dateAdapter.getSelectedDate();
                List<TimeSlot> filtered = new ArrayList<>();
                if (resource.data != null) {
                    for (TimeSlot slot : resource.data) {
                        if (selectedDate == null || slot.getDate().equals(selectedDate)) {
                            filtered.add(slot);
                        }
                    }
                }
                SlotAdapter newAdapter = new SlotAdapter(filtered, slot -> { });
                rvSlots.setAdapter(newAdapter);
                slotAdapter = newAdapter;
                break;
            case ERROR:
            default:
                progressBar.setVisibility(View.GONE);
                if (resource.message != null) {
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void renderAction(@Nullable Resource<Void> resource) {
        if (resource == null) {
            return;
        }
        if (resource.status == Resource.Status.LOADING) {
            progressBar.setVisibility(View.VISIBLE);
            setConfirmEnabled(false);
        } else if (resource.status == Resource.Status.SUCCESS) {
            // La inserción en Room terminó bien: dejamos de mostrar el spinner.
            // El cierre de pantalla lo maneja el evento navigateBack.
            progressBar.setVisibility(View.GONE);
            setConfirmEnabled(true);
        } else if (resource.status == Resource.Status.ERROR) {
            progressBar.setVisibility(View.GONE);
            setConfirmEnabled(true);
            isSubmitting = false;
            if (resource.message != null) {
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
            }
            appointmentViewModel.clearActionState();
        }
    }

    private void setConfirmEnabled(boolean enabled) {
        if (btnConfirm != null) {
            btnConfirm.setEnabled(enabled);
        }
    }

    private void onConfirmClicked() {
        if (isSubmitting) {
            return;
        }

        TimeSlot selectedSlot = slotAdapter.getSelectedSlot();
        if (selectedSlot == null) {
            Toast.makeText(this, R.string.select_time, Toast.LENGTH_SHORT).show();
            return;
        }

        isSubmitting = true;
        setConfirmEnabled(false);

        String reason = etReason.getText() != null ? etReason.getText().toString().trim() : "";

        Appointment appointment = new Appointment();
        appointment.setDoctorId(doctor.getId());
        appointment.setDoctorName(doctor.getFullName());
        appointment.setSpecialtyName(doctor.getSpecialtyName());
        appointment.setPatientName(patientName);
        appointment.setPatientAge(patientAge);
        appointment.setDate(selectedSlot.getDate());
        appointment.setTime(selectedSlot.getTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setReason(reason);
        appointment.setCreatedAt(System.currentTimeMillis());

        appointmentViewModel.createAppointment(appointment);
    }
}
