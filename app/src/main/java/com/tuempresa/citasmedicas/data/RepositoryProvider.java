package com.tuempresa.citasmedicas.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.data.firebase.FirebaseAuthFallback;
import com.tuempresa.citasmedicas.data.firebase.FirebaseAuthRepository;
import com.tuempresa.citasmedicas.data.firebase.FirebaseConfig;
import com.tuempresa.citasmedicas.data.firebase.FirestoreAppointmentRepository;
import com.tuempresa.citasmedicas.data.firebase.FirestoreDoctorRepository;
import com.tuempresa.citasmedicas.data.firebase.FirestoreFallback;
import com.tuempresa.citasmedicas.data.firebase.FirestorePatientRepository;
import com.tuempresa.citasmedicas.data.repository.AppointmentRepository;
import com.tuempresa.citasmedicas.data.repository.AuthRepository;
import com.tuempresa.citasmedicas.data.repository.DoctorRepository;
import com.tuempresa.citasmedicas.data.repository.PatientRepository;
import com.tuempresa.citasmedicas.domain.repository.IAppointmentRepository;
import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.IDoctorRepository;
import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;

/**
 * Raíz de composición: <b>el único sitio donde se decide qué implementación usa cada
 * repositorio</b>.
 * <p>
 * <b>Cómo funciona el interruptor.</b> {@link FirebaseConfig#isEnabled()} devuelve
 * {@code true} sólo si Gradle inyectó {@code FIREBASE_PROJECT_ID} y
 * {@code FIREBASE_API_KEY} (desde {@code local.properties} o variables de entorno).
 * Si falta cualquiera de los dos, todo cae a las implementaciones simuladas y la app
 * se comporta exactamente igual que antes de existir Firebase.
 * <p>
 * Eso tiene tres consecuencias prácticas:
 * <ul>
 *   <li><b>GitHub Actions y el APK del profesor</b> siguen funcionando sin
 *       configurar nada: compilan en modo mock.</li>
 *   <li><b>Nunca se suben claves</b> al repositorio: {@code local.properties} está
 *       en {@code .gitignore}.</li>
 *   <li><b>Se puede volver atrás</b> borrando una línea del archivo de propiedades,
 *       sin tocar código.</li>
 * </ul>
 * <p>
 * <b>Room no se sustituye nunca.</b> {@link #appointments(Context)} sigue devolviendo
 * el repositorio local: Room es la fuente de verdad de la UI (reactividad y offline).
 * Firestore se añadirá como sincronizador en segundo plano, no como reemplazo.
 */
public final class RepositoryProvider {

    private RepositoryProvider() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * ¿Está Firebase configurado y activo?
     * <p>
     * Útil para que la UI pueda indicar el modo activo (por ejemplo, en Perfil).
     */
    public static boolean isUsingFirebase() {
        return FirebaseConfig.isEnabled();
    }

    /** Descripción del origen de datos activo: {@code "Mock"} o {@code "Firebase (...)"}. */
    @NonNull
    public static String describeDataSource() {
        return FirebaseConfig.describeMode();
    }

    /**
     * Autenticación.
     * <p>
     * Con Firebase configurado se usa {@link FirebaseAuthRepository} (REST de
     * Identity Toolkit). Si no, la validación local simulada de siempre.
     * <p>
     * <b>Degradación segura.</b> El remoto va envuelto en
     * {@link FirebaseAuthFallback}, que sólo recurre a la validación local si Firebase
     * indica un <i>problema de configuración</i> (proveedor de email sin activar, clave
     * de otro proyecto). Ante una contraseña incorrecta o un fallo de red propaga el
     * error: nunca se regala una sesión.
     */
    @NonNull
    public static IAuthRepository auth(@NonNull Context context) {
        if (FirebaseConfig.isEnabled()) {
            return FirebaseAuthFallback.wrap(new FirebaseAuthRepository(context),
                    new AuthRepository());
        }
        return new AuthRepository();
    }

    /**
     * Catálogo médico.
     * <p>
     * Con Firebase se leen las colecciones {@code doctors} y {@code specialties}.
     * Los <b>horarios disponibles</b> se siguen resolviendo localmente (no se migran
     * en esta fase), por eso el repositorio remoto recibe el local como respaldo.
     * <p>
     * <b>Respaldo automático.</b> El remoto va envuelto en {@link FirestoreFallback}:
     * si Firestore responde con error (base sin crear, reglas, sin red, cuota) o
     * devuelve una colección vacía, se sirven los datos locales. Así una incidencia
     * en la nube no deja las pantallas en blanco: se nota en el log y en el chip de
     * Perfil, pero la app sigue siendo demostrable.
     */
    @NonNull
    public static IDoctorRepository doctors(@NonNull Context context) {
        if (FirebaseConfig.isEnabled()) {
            Context app = context.getApplicationContext();
            return FirestoreFallback.doctors(
                    new FirestoreDoctorRepository(new DoctorRepository(), app),
                    new DoctorRepository());
        }
        return new DoctorRepository();
    }

    /**
     * Pacientes y su expediente.
     * <p>
     * Igual que el catálogo: Firestore con respaldo local automático.
     */
    @NonNull
    public static IPatientRepository patients(@NonNull Context context) {
        if (FirebaseConfig.isEnabled()) {
            return FirestoreFallback.patients(
                    new FirestorePatientRepository(context.getApplicationContext()),
                    new PatientRepository());
        }
        return new PatientRepository();
    }

    /**
     * Citas médicas.
     * <p>
     * <b>Room es la fuente de verdad, Firestore se añade encima.</b> Con Firebase
     * configurado se devuelve {@link FirestoreAppointmentRepository}, que guarda en
     * Room primero (reactividad inmediata y funcionamiento sin internet) y <i>además</i>
     * empuja la cita a la colección {@code appointments} de Firestore. Así las citas
     * que se crean en la app aparecen en la consola de Firebase, que es lo que se
     * espera de una app "conectada a la base de datos".
     * <p>
     * Sin credenciales se devuelve el repositorio puramente local: idéntico al
     * comportamiento anterior. La UI no cambia en ninguno de los dos casos, porque
     * ambos implementan {@link IAppointmentRepository}.
     */
    @NonNull
    public static IAppointmentRepository appointments(@NonNull Context context) {
        if (FirebaseConfig.isEnabled()) {
            return new FirestoreAppointmentRepository(context.getApplicationContext());
        }
        return new AppointmentRepository(context.getApplicationContext());
    }
}
