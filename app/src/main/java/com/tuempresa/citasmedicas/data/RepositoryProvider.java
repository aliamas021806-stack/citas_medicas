package com.tuempresa.citasmedicas.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.PatientRepository;
import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;

/**
 * Raíz de composición del proyecto: <b>el único sitio donde se decide qué
 * implementación concreta usa cada repositorio</b>.
 * <p>
 * <b>Por qué existe.</b> Antes cada {@code ViewModel} hacía
 * {@code new DoctorRepository()} y el {@code AuthViewModel} hacía
 * {@code new AuthRepository()}. Eso significaba que cambiar la fuente de datos
 * obligaba a buscar y editar todos esos puntos —y las pruebas no podían inyectar
 * una fuente falsa—. Concentrar la construcción aquí convierte ese problema en un
 * cambio de una sola línea.
 * <p>
 * <b>Cómo se conectará Firestore sin romper nada.</b> La gráfica de decisión queda
 * reducida a los métodos de esta clase. Para migrar bastará:
 * <ol>
 *   <li>Crear {@code FirebaseAuthRepository implements IAuthRepository} y ponerlo en
 *       {@link #auth(Context)} en lugar de {@code AuthRepository}.</li>
 *   <li>Crear {@code FirestoreDoctorRepository implements IDoctorRepository} y
 *       devolverlo en {@link #doctors()}, o devolver un repositorio que combine
 *       remoto + respaldo local.</li>
 *   <li>Para citas, <b>no se sustituye</b> {@link #appointments(Context)}: Room
 *       sigue siendo la fuente de verdad de la UI y Firestore se añade como
 *       sincronizador en segundo plano.</li>
 * </ol>
 * Ni las pantallas, ni los {@code ViewModel}, ni los casos de uso necesitan cambios:
 * todos dependen de las interfaces de {@code domain.repository}.
 * <p>
 * <b>Nota sobre singletons.</b> Los repositorios de catálogo no guardan estado
 * (solo envuelven el {@code MedicalApiService}, que ya es un singleton), así que se
 * crean bajo demanda. El de citas sí recibe el {@code Context} de aplicación porque
 * necesita la base de datos Room.
 */
public final class RepositoryProvider {

    private RepositoryProvider() {
        // Clase de utilidades: no se instancia.
    }

    /** Autenticación. Actualmente simulada en el dispositivo. */
    @NonNull
    public static IAuthRepository auth(@NonNull Context context) {
        return new AuthRepository();
    }

    /** Catálogo médico (especialidades, doctores y horarios). */
    @NonNull
    public static IDoctorRepository doctors() {
        return new DoctorRepository();
    }

    /** Pacientes y su expediente. */
    @NonNull
    public static IPatientRepository patients() {
        return new PatientRepository();
    }

    /**
     * Citas médicas.
     * <p>
     * Se pasa el {@code Context} de aplicación para que Room use su propio ciclo de
     * vida y no retenga una {@code Activity}.
     */
    @NonNull
    public static IAppointmentRepository appointments(@NonNull Context context) {
        return new AppointmentRepository(context.getApplicationContext());
    }
}
