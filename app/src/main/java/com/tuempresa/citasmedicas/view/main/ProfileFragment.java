package com.tuempresa.citasmedicas.view.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
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
    /** Tarjeta del expediente clínico: sólo se muestra a los pacientes. */
    private CardView cardExpediente;
    /** Tarjeta de la ficha profesional: sólo se muestra a los médicos. */
    private CardView cardFichaMedico;
    private TextView tvDoctorEspecialidad, tvDoctorHospital, tvDoctorTelefono;

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

        cardExpediente = view.findViewById(R.id.cardExpediente);
        cardFichaMedico = view.findViewById(R.id.cardFichaMedico);
        tvDoctorEspecialidad = view.findViewById(R.id.tvDoctorEspecialidad);
        tvDoctorHospital = view.findViewById(R.id.tvDoctorHospital);
        tvDoctorTelefono = view.findViewById(R.id.tvDoctorTelefono);
        MaterialButton btnCompletarFicha = view.findViewById(R.id.btnCompletarFicha);
        if (btnCompletarFicha != null) {
            btnCompletarFicha.setOnClickListener(v -> abrirFormularioDeFicha());
        }

        // El perfil se pinta según el rol: un médico no tiene expediente clínico y un
        // administrador tampoco. Antes se les pintaba la misma tarjeta que a un paciente,
        // con grupo sanguíneo y aseguradora que nunca rellenaron.
        boolean esMedico = user != null && user.getRole() != null && user.getRole().isDoctor();
        boolean esAdmin = user != null && user.getRole() != null && user.getRole().isAdmin();
        if (cardFichaMedico != null) {
            cardFichaMedico.setVisibility(esMedico ? View.VISIBLE : View.GONE);
        }
        if (cardExpediente != null) {
            cardExpediente.setVisibility(
                    (esMedico || esAdmin) ? View.GONE : View.VISIBLE);
        }

        if (esMedico) {
            cargarFichaDelMedico();
        } else {
            // El expediente se pinta con los datos del usuario en sesión. Antes se pedía
            // siempre el paciente "pat_01" (Laura Jiménez) del mock, así que el perfil
            // mostraba la información de otra persona.
            renderPatientFromSession();
            sincronizarExpedienteConFirestore();
        }
    }

    /**
     * Trae la ficha del médico desde Firestore y la pinta.
     * <p>
     * <b>Por qué hay que leerla.</b> La sesión guarda lo que devolvió el login (correo y
     * poco más). La especialidad, el consultorio y el teléfono viven en {@code doctors};
     * sin esta lectura la tarjeta saldría con guiones y el médico no sabría qué le falta
     * por completar.
     */
    private void cargarFichaDelMedico() {
        final SessionManager session = SessionManager.getInstance(requireContext());
        final String doctorId = session.getDoctorId();
        if (doctorId == null || doctorId.trim().isEmpty()) {
            if (tvDoctorEspecialidad != null) {
                tvDoctorEspecialidad.setText(R.string.profile_sin_ficha);
            }
            return;
        }

        RepositoryProvider.doctors(requireContext().getApplicationContext())
                .getDoctorById(doctorId,
                        new com.tuempresa.citasmedicas.domain.repository
                                .RepositoryCallback<com.tuempresa.citasmedicas.model.Doctor>() {
                            @Override
                            public void onSuccess(
                                    com.tuempresa.citasmedicas.model.Doctor ficha) {
                                if (!isAdded() || ficha == null) {
                                    return;
                                }
                                renderFichaDeMedico(ficha);
                                // Se copia a la sesión para que el nombre y la edad del
                                // encabezado sean los de la ficha y no los del correo.
                                session.aplicarFichaDeMedico(ficha.getFullName(),
                                        ficha.getAge(), ficha.getPhone());
                                actualizarEncabezado();
                            }

                            @Override
                            public void onError(String message) {
                                if (!isAdded()) {
                                    return;
                                }
                                // Sin red se mantienen los datos de la sesión: peor es
                                // dejar la tarjeta en blanco.
                                actualizarEncabezado();
                            }
                        });
    }

    /** Pinta especialidad, consultorio y teléfono de la ficha. */
    private void renderFichaDeMedico(
            @NonNull com.tuempresa.citasmedicas.model.Doctor ficha) {
        if (tvDoctorEspecialidad != null) {
            String esp = safe(ficha.getSpecialtyName());
            if (getString(R.string.value_placeholder).equals(esp)
                    && ficha.getSpecialtyId() != null
                    && !ficha.getSpecialtyId().trim().isEmpty()) {
                esp = ficha.getSpecialtyId();
            }
            tvDoctorEspecialidad.setText(esp);
        }
        if (tvDoctorHospital != null) {
            tvDoctorHospital.setText(safe(ficha.getHospital()));
        }
        if (tvDoctorTelefono != null) {
            tvDoctorTelefono.setText(safe(ficha.getPhone()));
        }
        MaterialButton btn = getView() != null
                ? getView().findViewById(R.id.btnCompletarFicha) : null;
        if (btn != null) {
            // El botón cambia de texto según falte algo o no: así el médico sabe si
            // tiene algo pendiente sin abrir el formulario.
            btn.setText(ficha.necesitaCompletarDatos()
                    ? R.string.profile_completar_ficha
                    : R.string.profile_editar_ficha);
        }
    }

    /** Vuelve a pintar avatar, nombre, correo y edad con lo que haya en la sesión. */
    private void actualizarEncabezado() {
        User user = SessionManager.getInstance(requireContext()).getUser();
        if (user == null || !isAdded()) {
            return;
        }
        currentUser = user;
        if (tvAvatar != null) {
            tvAvatar.setText(user.getInitials());
        }
        if (tvName != null) {
            tvName.setText(user.getFullName());
        }
        if (tvEmail != null) {
            tvEmail.setText(user.getEmail());
        }
        if (tvAge != null) {
            if (user.getAge() > 0) {
                tvAge.setText(getResources().getQuantityString(
                        R.plurals.years, user.getAge(), user.getAge()));
                tvAge.setVisibility(View.VISIBLE);
            } else {
                // Un médico del que no se conoce la edad no debe ver "Edad no
                // registrada" en su propio perfil: simplemente no se muestra.
                tvAge.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Abre el formulario para que el médico complete su ficha (teléfono, consultorio,
     * reseña y tarifa).
     * <p>
     * <b>Es lo que cierra el círculo del alta.</b> El administrador crea la ficha con
     * el correo del médico; el médico entra con ese correo y aquí termina de rellenarla.
     * Sin esto último, la ficha se quedaba a medias para siempre.
     */
    private void abrirFormularioDeFicha() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        final String doctorId = SessionManager.getInstance(requireContext()).getDoctorId();
        if (doctorId == null || doctorId.trim().isEmpty()) {
            Toast.makeText(getContext(), R.string.profile_sin_ficha, Toast.LENGTH_LONG).show();
            return;
        }

        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_ficha_medico, null);
        final android.widget.EditText etTelefono = form.findViewById(R.id.etFichaTelefono);
        final android.widget.EditText etHospital = form.findViewById(R.id.etFichaHospital);
        final android.widget.EditText etBio = form.findViewById(R.id.etFichaBio);
        final android.widget.EditText etTarifa = form.findViewById(R.id.etFichaTarifa);

        // Se precargan los valores que ya tenga la ficha: el formulario es para
        // completar, no para reescribir desde cero.
        if (tvDoctorTelefono != null) {
            etTelefono.setText(textoReal(tvDoctorTelefono.getText()));
        }
        if (tvDoctorHospital != null) {
            etHospital.setText(textoReal(tvDoctorHospital.getText()));
        }

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle(R.string.ficha_title)
                        .setMessage(R.string.ficha_subtitle)
                        .setView(form)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.ficha_guardar, null)
                        .create();

        dialog.setOnShowListener(d -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String telefono = etTelefono.getText().toString().trim();
                    if (telefono.isEmpty()) {
                        etTelefono.setError(getString(R.string.ficha_error_telefono));
                        return;
                    }
                    double tarifa = 0d;
                    try {
                        tarifa = Double.parseDouble(
                                etTarifa.getText().toString().trim().replace(",", "."));
                    } catch (NumberFormatException ignored) {
                        tarifa = 0d;
                    }
                    guardarFicha(doctorId, telefono, etHospital.getText().toString(),
                            etBio.getText().toString(), tarifa, dialog);
                }));
        dialog.show();
    }

    /** Envía los datos de la ficha y refresca la pantalla. */
    private void guardarFicha(@NonNull String doctorId, @NonNull String telefono,
                              @Nullable String hospital, @Nullable String bio,
                              double tarifa, @NonNull androidx.appcompat.app.AlertDialog dialog) {
        new com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository(requireContext())
                .actualizarMiFicha(doctorId, telefono, hospital, bio, tarifa,
                        new com.tuempresa.citasmedicas.data.firebase
                                .FirestoreUserRepository.FichaCallback() {
                            @Override
                            public void onOk() {
                                if (!isAdded()) {
                                    return;
                                }
                                dialog.dismiss();
                                // El teléfono se guarda también en la sesión para que el
                                // perfil no dependa de una segunda lectura a Firestore.
                                SessionManager.getInstance(requireContext())
                                        .actualizarTelefono(telefono);
                                if (tvDoctorTelefono != null) {
                                    tvDoctorTelefono.setText(telefono);
                                }
                                if (tvDoctorHospital != null && hospital != null
                                        && !hospital.trim().isEmpty()) {
                                    tvDoctorHospital.setText(hospital.trim());
                                }
                                Toast.makeText(getContext(), R.string.ficha_guardada,
                                        Toast.LENGTH_SHORT).show();
                                cargarFichaDelMedico();
                            }

                            @Override
                            public void onError(@NonNull String mensaje) {
                                if (!isAdded()) {
                                    return;
                                }
                                dialog.dismiss();
                                Toast.makeText(getContext(), mensaje, Toast.LENGTH_LONG).show();
                            }
                        });
    }

    /** Convierte el marcador "—" de la interfaz en cadena vacía. */
    @NonNull
    private String textoReal(@Nullable CharSequence cs) {
        if (cs == null) {
            return "";
        }
        String s = cs.toString().trim();
        return getString(R.string.value_placeholder).equals(s) ? "" : s;
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
