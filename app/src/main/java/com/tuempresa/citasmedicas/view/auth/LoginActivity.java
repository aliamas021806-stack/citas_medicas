package com.tuempresa.citasmedicas.view.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.User;
import com.tuempresa.citasmedicas.util.AuthValidator;
import com.tuempresa.citasmedicas.util.Resource;
import com.tuempresa.citasmedicas.util.SessionManager;
import com.tuempresa.citasmedicas.navigation.Navigator;
import com.tuempresa.citasmedicas.navigation.Routes;
import com.tuempresa.citasmedicas.viewmodel.AuthViewModel;

/**
 * Pantalla de Login / Registro (View).
 * <p>
 * En modo registro pide los datos del <b>expediente</b> del paciente (nombre,
 * apellidos, edad, teléfono, grupo sanguíneo, aseguradora y alergias) para que el
 * perfil muestre información real en lugar de datos aleatorios del mock.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilLastName, tilAge, tilPhone, tilBloodType,
            tilInsurance, tilAllergies;
    private TextInputEditText etName, etLastName, etAge, etPhone, etBloodType,
            etInsurance, etAllergies, etEmail, etPassword;
    private MaterialButton btnSubmit, btnTipoPaciente, btnTipoMedico;
    private ProgressBar progressBar;
    private TextView tvToggle, tvError, tvTipoCuentaAyuda;
    private View groupTipoCuenta;

    private boolean isRegisterMode = false;
    /** {@code true} si en el registro se ha elegido «Médico». */
    private boolean tipoMedico = false;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        bindViews();
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        btnSubmit.setOnClickListener(v -> submit());
        tvToggle.setOnClickListener(v -> toggleMode());

        configurarTipoCuenta();

        observeViewModel();
    }

    /**
     * Selector «Paciente / Médico» del registro.
     * <p>
     * <b>Qué decide y qué no.</b> No da el rol: eso lo resuelve Firestore al iniciar
     * sesión. Lo único que hace es intentar vincular la ficha que el administrador haya
     * creado con este correo justo después del registro, para que el médico no tenga que
     * cerrar sesión y volver a entrar. Si no hay ficha, la cuenta sigue siendo válida como
     * paciente y se le explica qué falta.
     */
    private void configurarTipoCuenta() {
        if (groupTipoCuenta == null) {
            return;
        }
        btnTipoPaciente.setChecked(!tipoMedico);
        btnTipoMedico.setChecked(tipoMedico);
        actualizarTipoCuenta(tipoMedico);

        btnTipoPaciente.setOnClickListener(v -> actualizarTipoCuenta(false));
        btnTipoMedico.setOnClickListener(v -> actualizarTipoCuenta(true));
    }

    /** Aplica la elección de tipo de cuenta a la visibilidad de los campos. */
    private void actualizarTipoCuenta(boolean medico) {
        tipoMedico = medico;
        if (tvTipoCuentaAyuda != null) {
            tvTipoCuentaAyuda.setText(medico
                    ? R.string.registro_ayuda_medico
                    : R.string.registro_ayuda_paciente);
        }
        // Los datos clínicos (sangre, seguro, alergias) son del expediente del paciente:
        // a un médico no se le piden, pero el teléfono sí (para su ficha de contacto).
        for (TextInputLayout campo : new TextInputLayout[]{
                tilBloodType, tilInsurance, tilAllergies}) {
            if (campo != null) {
                campo.setVisibility(
                        isRegisterMode && !medico ? View.VISIBLE : View.GONE);
            }
        }
        hideError();
    }

    private void bindViews() {
        tilName = findViewById(R.id.tilName);
        tilLastName = findViewById(R.id.tilLastName);
        tilAge = findViewById(R.id.tilAge);
        tilPhone = findViewById(R.id.tilPhone);
        tilBloodType = findViewById(R.id.tilBloodType);
        tilInsurance = findViewById(R.id.tilInsurance);
        tilAllergies = findViewById(R.id.tilAllergies);
        etName = findViewById(R.id.etName);
        etLastName = findViewById(R.id.etLastName);
        etAge = findViewById(R.id.etAge);
        etPhone = findViewById(R.id.etPhone);
        etBloodType = findViewById(R.id.etBloodType);
        etInsurance = findViewById(R.id.etInsurance);
        etAllergies = findViewById(R.id.etAllergies);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);
        tvToggle = findViewById(R.id.tvToggle);
        tvError = findViewById(R.id.tvError);
        groupTipoCuenta = findViewById(R.id.groupTipoCuenta);
        btnTipoPaciente = findViewById(R.id.btnTipoPaciente);
        btnTipoMedico = findViewById(R.id.btnTipoMedico);
        tvTipoCuentaAyuda = findViewById(R.id.tvTipoCuentaAyuda);
    }

    /** Campos que sólo existen en modo registro. */
    private TextInputLayout[] camposDeRegistro() {
        return new TextInputLayout[]{tilName, tilLastName, tilAge, tilPhone,
                tilBloodType, tilInsurance, tilAllergies};
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        int visibilidad = isRegisterMode ? View.VISIBLE : View.GONE;
        for (TextInputLayout campo : camposDeRegistro()) {
            if (campo != null) {
                campo.setVisibility(visibilidad);
            }
        }
        if (groupTipoCuenta != null) {
            groupTipoCuenta.setVisibility(visibilidad);
        }
        // Al volver al login se ocultan los datos clínicos: en el login no se elige nada,
        // el rol lo resuelve Firestore.
        if (isRegisterMode) {
            actualizarTipoCuenta(tipoMedico);
        } else {
            for (TextInputLayout campo : new TextInputLayout[]{
                    tilBloodType, tilInsurance, tilAllergies}) {
                if (campo != null) {
                    campo.setVisibility(View.GONE);
                }
            }
        }
        btnSubmit.setText(isRegisterMode ? R.string.register_button : R.string.login_button);
        tvToggle.setText(isRegisterMode ? R.string.toggle_login : R.string.toggle_register);
        hideError();
    }

    private String textoDe(TextInputEditText campo) {
        if (campo == null || campo.getText() == null) {
            return "";
        }
        return campo.getText().toString().trim();
    }

    private void submit() {
        String email = textoDe(etEmail);
        String password = etPassword != null && etPassword.getText() != null
                ? etPassword.getText().toString() : "";
        hideError();

        if (isRegisterMode) {
            String name = textoDe(etName);
            if (TextUtils.isEmpty(name)) {
                showError(getString(R.string.error_name_required));
                return;
            }
            String ageStr = textoDe(etAge);
            int age = 0;
            if (!TextUtils.isEmpty(ageStr)) {
                try {
                    age = Integer.parseInt(ageStr);
                } catch (NumberFormatException ignored) {
                    age = 0;
                }
            }
            if (ageStr.isEmpty() || age < 1 || age > 120) {
                showError(getString(R.string.error_age_invalid));
                return;
            }
            // El teléfono se escribe a mano: antes lo inventaba el mock.
            String phone = textoDe(etPhone);
            String errorTelefono = AuthValidator.validateMedicalRecord(phone);
            if (errorTelefono != null) {
                showError(errorTelefono);
                return;
            }
            viewModel.register(name, textoDe(etLastName), email, password, age, phone,
                    textoDe(etBloodType), textoDe(etInsurance), textoDe(etAllergies));
        } else {
            viewModel.login(email, password);
        }
    }

    private void observeViewModel() {
        viewModel.getAuthState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    hideError();
                    break;
                case SUCCESS:
                    setLoading(false);
                    onAuthSuccess(resource.data);
                    break;
                case ERROR:
                    setLoading(false);
                    showError(resource.message);
                    break;
            }
        });
    }

    /**
     * Guarda la sesión y <b>resuelve el rol</b> antes de entrar.
     * <p>
     * Sin este paso el rol nunca se sabría: Firebase Authentication sólo devuelve el
     * correo y el {@code uid}. {@link FirestoreUserRepository} comprueba si la cuenta
     * es administrador ({@code /users/{uid}}) o si tiene una ficha en {@code doctors}
     * (por {@code uid} ya vinculado o por el correo de la invitación) y lo deja escrito
     * en la sesión. Después se entra en la pestaña inicial del rol: el médico aterriza
     * en su agenda y el administrador en el alta de médicos.
     * <p>
     * Si Firebase no está configurado (modo sin conexión) se entra directamente como
     * paciente: no hay servidor a quien preguntar y la app debe seguir siendo usable.
     */
    private void onAuthSuccess(User user) {
        SessionManager.getInstance(this).saveSession(user);
        Toast.makeText(this,
                isRegisterMode ? R.string.welcome_register : R.string.welcome_login,
                Toast.LENGTH_SHORT).show();

        if (!com.tuempresa.citasmedicas.data.firebase.FirebaseConfig.isEnabled()) {
            Navigator.navigateClearingStack(this, new Routes.Main(Routes.Tab.DOCTORS));
            return;
        }

        final SessionManager session = SessionManager.getInstance(this);
        final String uid = user.getId();

        // Si acaba de registrarse eligiendo «Médico», se intenta vincular (o crear) su
        // ficha antes de resolver el rol: así entra directamente en su agenda.
        if (isRegisterMode && tipoMedico) {
            setLoading(true);
            viewModel.vincularMedicoRecienRegistrado(user,
                    (vinculado, doctorId) -> {
                        setLoading(false);
                        if (!vinculado) {
                            // La cuenta existe y es válida, pero no hay ficha: se entra
                            // como paciente y se explica qué falta. No se bloquea.
                            Toast.makeText(LoginActivity.this,
                                    R.string.registro_medico_no_invitado,
                                    Toast.LENGTH_LONG).show();
                            Navigator.navigateClearingStack(LoginActivity.this,
                                    new Routes.Main(Routes.Tab.DOCTORS));
                            return;
                        }
                        Toast.makeText(LoginActivity.this,
                                getString(R.string.registro_medico_vinculado, doctorId),
                                Toast.LENGTH_SHORT).show();
                        Navigator.navigateClearingStack(LoginActivity.this,
                                new Routes.Main(Routes.Tab.AGENDA));
                    });
            return;
        }

        com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository roles =
                new com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository(this);

        setLoading(true);
        roles.cargarRol(uid, user.getEmail(),
                new com.tuempresa.citasmedicas.data.firebase.FirestoreUserRepository
                        .RoleCallback() {
                    @Override
                    public void onRole(@NonNull com.tuempresa.citasmedicas.model.UserRole role,
                                       @Nullable String doctorId) {
                        session.updateRole(role, doctorId);
                        setLoading(false);
                        Navigator.navigateClearingStack(LoginActivity.this,
                                new Routes.Main(Navigator.homeTabForRole(role)));
                    }

                    @Override
                    public void onFallback(@NonNull String motivo) {
                        // Sin red o sin documento de rol: se entra como paciente. Es el
                        // permiso mínimo y no impide usar la app.
                        setLoading(false);
                        Navigator.navigateClearingStack(LoginActivity.this,
                                new Routes.Main(Routes.Tab.DOCTORS));
                    }
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
    }

    private void showError(String message) {
        if (message == null) {
            return;
        }
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }
}
