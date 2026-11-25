package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.domain.repository.IAuthRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.AuthRequest;
import com.tuempresa.citasmedicas.model.User;

/**
 * Autenticación con <b>degradación controlada</b> cuando Firebase no está configurado.
 * <p>
 * <b>El problema que resuelve.</b> Si el proyecto de Firebase existe pero el proveedor
 * de <i>Correo electrónico/contraseña</i> todavía no se ha activado en la consola,
 * Firebase responde {@code CONFIGURATION_NOT_FOUND} y <b>nadie puede iniciar sesión</b>.
 * La app quedaría inutilizable por un trámite de configuración, no por un error de
 * código.
 * <p>
 * <b>Qué hace y qué NO hace.</b> Es deliberadamente restrictivo:
 * <ul>
 *   <li><b>Sí</b> recurre a la validación local cuando Firebase dice que <i>no está
 *       configurado</i> ({@code CONFIGURATION_NOT_FOUND}, {@code OPERATION_NOT_ALLOWED},
 *       {@code ADMIN_ONLY_OPERATION}) o que la <i>clave no sirve</i> para este proyecto.</li>
 *   <li><b>Nunca</b> recurre al respaldo si Firebase respondió que las credenciales son
 *       incorrectas ({@code EMAIL_NOT_FOUND}, {@code INVALID_PASSWORD},
 *       {@code EMAIL_EXISTS}...). Eso sería un agujero de seguridad: permitiría entrar
 *       con cualquier contraseña.</li>
 *   <li><b>Nunca</b> recurre ante un fallo de red: sin conexión, un login "fantasma"
 *       daría una sesión que no existe en el servidor.</li>
 * </ul>
 * Cada degradación se registra con {@code Log.w} indicando el motivo exacto.
 * <p>
 * <b>Java puro:</b> composición de interfaces, sin Kotlin.
 */
public final class FirebaseAuthFallback {

    private static final String TAG = "AuthFallback";

    private FirebaseAuthFallback() {
        // Clase de utilidades.
    }

    /**
     * Envuelve el repositorio remoto con el local como red de seguridad.
     *
     * @param remoto autenticación real contra Firebase
     * @param local  validación simulada (siempre disponible, sin red)
     */
    @NonNull
    public static IAuthRepository wrap(@NonNull final IAuthRepository remoto,
                                       @NonNull final IAuthRepository local) {
        return new IAuthRepository() {

            @Override
            public void login(@NonNull AuthRequest request,
                              @NonNull final RepositoryCallback<User> callback) {
                remoto.login(request, new Traductor(callback, "login") {
                    @Override
                    void degradar() {
                        local.login(request, callback);
                    }
                });
            }

            @Override
            public void register(@NonNull AuthRequest request,
                                 @NonNull final RepositoryCallback<User> callback) {
                remoto.register(request, new Traductor(callback, "registro") {
                    @Override
                    void degradar() {
                        local.register(request, callback);
                    }
                });
            }
        };
    }

    /**
     * ¿El mensaje indica un problema de <i>configuración</i> y no de credenciales?
     * <p>
     * Se compara sobre el texto ya traducido al español que produce
     * {@code FirebaseAuthRepository}, para no acoplar esta clase a las etiquetas
     * internas de Google.
     */
    public static boolean esProblemaDeConfiguracion(@Nullable String mensaje) {
        if (mensaje == null) {
            return false;
        }
        String m = mensaje.toLowerCase(java.util.Locale.ROOT);
        // Configuración ausente o deshabilitada.
        if (m.contains("no está configurado")
                || m.contains("deshabilitado")
                || m.contains("deshabilitada")
                || m.contains("no está permitida con la configuración")) {
            return true;
        }
        // La clave no pertenece a este proyecto: tampoco es culpa del usuario.
        return m.contains("no es válida para este proyecto");
    }

    /**
     * Callback intermedio: deja pasar los éxitos y los errores de credenciales, y
     * sólo activa el respaldo ante problemas de configuración.
     */
    private abstract static class Traductor implements RepositoryCallback<User> {

        private final RepositoryCallback<User> destino;
        private final String operacion;

        Traductor(RepositoryCallback<User> destino, String operacion) {
            this.destino = destino;
            this.operacion = operacion;
        }

        /** Ejecuta la validación local. */
        abstract void degradar();

        @Override
        public final void onSuccess(User data) {
            destino.onSuccess(data);
        }

        @Override
        public final void onError(String message) {
            if (esProblemaDeConfiguracion(message)) {
                android.util.Log.w(TAG, "Firebase Authentication no está listo (" + message
                        + "). Se usa la validación local para el " + operacion);
                degradar();
                return;
            }
            destino.onError(message);
        }
    }
}
