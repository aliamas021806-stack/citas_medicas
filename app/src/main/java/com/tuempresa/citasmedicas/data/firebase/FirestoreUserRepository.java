package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.UserRole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Decide <b>qué es</b> la cuenta que acaba de iniciar sesión: paciente, médico o
 * administrador.
 * <p>
 * <b>El problema que resuelve.</b> Firebase Authentication sólo sabe el correo y el
 * {@code uid}; no distingue un paciente de un médico. Sin esta pieza, un médico que
 * se registrara con su correo vería la app de paciente y su agenda estaría vacía,
 * porque las citas se buscan por {@code doctorId} y su cuenta no estaría vinculada a
 * ninguna ficha de la colección {@code doctors}.
 * <p>
 * <b>Cómo se vincula sin tocar la base de datos a mano.</b> Un administrador da de
 * alta al médico desde la propia app escribiendo su correo. Esa ficha queda
 * <i>invitada</i> ({@code userId} vacío). Cuando el médico crea su cuenta (o inicia
 * sesión) con ese correo, esta clase lo detecta, escribe su {@code uid} en la ficha
 * y le concede el rol de médico. A partir de ese momento la vinculación es fuerte:
 * el {@code uid} manda sobre el correo.
 * <p>
 * <b>Precedencia de roles.</b> Administrador &gt; Médico &gt; Paciente. El rol de
 * administrador sólo puede venir del documento {@code /users/{uid}}, que la app nunca
 * escribe con ese valor (al registrarse siempre pone {@code patient}): no hay forma de
 * autoascenderse desde el cliente.
 */
public class FirestoreUserRepository {

    private static final String TAG = "RolesCitas";

    /** Colección de los perfiles con rol. */
    public static final String COLLECTION_USERS = "users";

    private final FirestoreApi api;

    public FirestoreUserRepository(@NonNull android.content.Context context) {
        this.api = FirestoreClient.getApi(context.getApplicationContext());
    }

    /** Puerto de salida del rol: lo que necesita el resto de la app. */
    public interface RoleCallback {
        /**
         * @param role     rol efectivo de la cuenta
         * @param doctorId id de la ficha en {@code doctors} (vacío si no es médico)
         */
        void onRole(@NonNull UserRole role, @Nullable String doctorId);

        /**
         * Igual que {@link #onRole} pero con la <b>ficha completa</b> del médico, que
         * es de donde salen el nombre real, la edad y el teléfono.
         * <p>
         * <b>Por qué hacía falta.</b> Al iniciar sesión, Firebase Authentication sólo
         * devuelve el correo: el nombre se derivaba del correo ("medico.demo01" →
         * "Medico Demo01"), la edad se quedaba en 0 y el teléfono aparecía vacío,
         * aunque la ficha tuviera "Ana Suárez", 35 años y un consultorio asignado. Con
         * la ficha a mano, la sesión se rellena con los datos de verdad.
         *
         * @param ficha ficha del médico (nunca {@code null} si {@code role} es médico)
         */
        default void onRolConFicha(@NonNull UserRole role, @Nullable String doctorId,
                                   @Nullable Doctor ficha) {
            onRole(role, doctorId);
        }

        /** No se pudo consultar: se asume paciente. */
        void onFallback(@NonNull String motivo);
    }

    /**
     * Resuelve el rol de la cuenta y, si es un médico invitado, lo vincula.
     * <p>
     * Pasos:
     * <ol>
     *   <li>Lee {@code /users/{uid}}. Si dice {@code admin}, se acabó: es el rol más
     *       alto y no hace falta mirar nada más.</li>
     *   <li>Lee la colección {@code doctors} y busca una ficha cuyo {@code userId} sea
     *       este {@code uid} (ya vinculada) o cuyo {@code email} coincida con el de la
     *       cuenta (invitación pendiente). La segunda búsqueda se hace en memoria: la
     *       colección tiene decenas de documentos y así no se depende de un índice.</li>
     *   <li>Si encuentra una ficha invitada, escribe el {@code uid} en ella para no
     *       volver a depender del correo.</li>
     * </ol>
     */
    public void cargarRol(@NonNull final String uid, @Nullable final String email,
                          @NonNull final RoleCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onFallback("No hay identificador de usuario");
            return;
        }

        api.getDocument(COLLECTION_USERS, uid)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        Map<String, FirestoreApi.Value> campos = null;
                        // ── 404 sí, 403 no ──
                        // Antes cualquier respuesta que no fuese 2xx dejaba `campos` en
                        // null, así que un error de *permisos* se interpretaba como
                        // «esta cuenta no tiene perfil». Con esa premisa se acababa
                        // escribiendo `role: "patient"` encima del documento de un
                        // administrador y la app lo degradaba a paciente en cada inicio
                        // de sesión. Ahora «no hay perfil» significa exactamente 404.
                        boolean hayPerfil = response.isSuccessful() && response.body() != null;
                        if (hayPerfil) {
                            campos = response.body().fields;
                        } else if (response.code() != 404) {
                            android.util.Log.w(TAG, "No se pudo leer /users/" + uid
                                    + " (HTTP " + response.code() + ")");
                            // No se sabe el rol y no se puede deducir: se entra con el
                            // rol que ya tenga la sesión, sin escribir nada en Firestore.
                            callback.onFallback("No se pudo leer el perfil: HTTP "
                                    + response.code());
                            return;
                        }
                        resolverDoctorOHeredar(uid, email, campos, hayPerfil, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        // Sin red: no se puede saber el rol, pero la app debe arrancar.
                        callback.onFallback("Sin conexión al leer el rol: " + t.getMessage());
                    }
                });
    }

    /**
     * Segundo paso: decide el rol y, si hay ficha de médico, la vincula.
     * <p>
     * <b>Regla de oro: al iniciar sesión no se escribe el rol.</b> Sólo se escribe la
     * {@code doctorId} que falta, y siempre con la ficha delante. Así una cuenta
     * <i>nunca</i> puede perder su rol por entrar (que es lo que le pasaba al
     * administrador: cada login lo devolvía a paciente).
     *
     * @param hayPerfil {@code true} si {@code /users/{uid}} existe de verdad (no es un
     *                  error de permisos ni de red)
     */
    private void resolverDoctorOHeredar(@NonNull final String uid, @Nullable final String email,
                                        @Nullable Map<String, FirestoreApi.Value> campos,
                                        boolean hayPerfil,
                                        @NonNull final RoleCallback callback) {
        UserRole guardado = hayPerfil
                ? UserRole.fromValue(leerTexto(campos, "role")) : UserRole.PATIENT;

        if (guardado.isAdmin()) {
            // El rol más alto no necesita ficha de médico y no se toca su documento:
            // el rol admin sólo puede venir de la consola.
            callback.onRolConFicha(UserRole.ADMIN, leerTexto(campos, "doctorId"), null);
            return;
        }

        final String doctorIdGuardado = leerTexto(campos, "doctorId");

        api.listDocuments(FirebaseConfig.COLLECTION_DOCTORS)
                .enqueue(new retrofit2.Callback<FirestoreApi.DocumentList>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.DocumentList> call,
                            @NonNull Response<FirestoreApi.DocumentList> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            // No se pudo comprobar si es médico. Si la sesión ya decía
                            // que lo era, se mantiene: degradar aquí sería el mismo
                            // error que se está corrigiendo, sólo que por otra puerta.
                            callback.onRole(guardado, doctorIdGuardado);
                            return;
                        }
                        List<Doctor> doctores = FirestoreMapper.toDoctors(response.body().documents);
                        Doctor mio = buscarFichaDeLaCuenta(doctores, uid, email);
                        if (mio == null) {
                            // Ni admin ni médico: es un paciente. Y si todavía no tiene
                            // documento de rol se crea AHORA, porque sin él las reglas
                            // no pueden identificarlo (`miRol()` devolvería ''). El rol
                            // escrito es el que ya tenía la sesión, nunca otro.
                            cerrarCreandoPerfilSiFalta(uid, email, !hayPerfil, guardado,
                                    doctorIdGuardado, callback);
                            return;
                        }

                        // Hay ficha: esta cuenta ES del médico. Se reclama si estaba
                        // invitada (esa escritura va firmada con su propio correo).
                        vincularSiHaciaFalta(mio, uid);

                        boolean faltaDoctorId = doctorIdGuardado == null
                                || doctorIdGuardado.trim().isEmpty()
                                || !doctorIdGuardado.trim().equals(mio.getId());

                        if (!hayPerfil || faltaDoctorId) {
                            // Se completa SÓLO el perfil del médico: `role: "doctor"` y su
                            // `doctorId`. Nunca se pasa por aquí una cuenta de admin,
                            // porque el admin ya salió arriba.
                            guardarPerfilDeMedico(uid, email, mio, callback);
                            return;
                        }
                        // Ya estaba todo: no se escribe nada (ni una petición de más).
                        callback.onRolConFicha(UserRole.DOCTOR, mio.getId(), mio);
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.DocumentList> call,
                                          @NonNull Throwable t) {
                        // Sin red no se puede crear ni vincular nada: se entra con lo
                        // que diga la sesión, sin sobrescribir el servidor.
                        callback.onRole(guardado, doctorIdGuardado);
                    }
                });
    }

    /**
     * Termina la resolución del rol y, si la cuenta no tenía perfil, lo crea.
     * <p>
     * Sólo se crea cuando la lectura de {@code /users/{uid}} devolvió 404. Es importante:
     * si se creara siempre, un administrador que escribe su documento a mano vería cómo
     * su {@code role: "admin"} se degrada a {@code patient} en el siguiente inicio de
     * sesión, que es exactamente el síntoma «entro como admin y me trata como paciente».
     */
    private void cerrarCreandoPerfilSiFalta(@NonNull String uid, @Nullable String email,
                                            boolean sinPerfil, @NonNull UserRole rol,
                                            @Nullable String doctorId,
                                            @NonNull final RoleCallback callback) {
        if (!sinPerfil) {
            callback.onRole(rol, doctorId);
            return;
        }
        asegurarPerfil(uid, email != null ? email : "", null, rol, doctorId, callback);
    }

    /**
     * Busca la ficha del médico que corresponde a esta cuenta.
     * <p>
     * Primero por {@code uid} (vinculación fuerte) y después por correo (invitación
     * pendiente). Se hace en memoria a propósito: filtrar por correo en Firestore
     * exigiría un índice y la colección es pequeña.
     */
    @Nullable
    static Doctor buscarFichaDeLaCuenta(@Nullable List<Doctor> doctores,
                                        @NonNull String uid, @Nullable String email) {
        if (doctores == null) {
            return null;
        }
        // 1) Ya vinculada a este uid.
        for (Doctor d : doctores) {
            if (d != null && d.isLinked() && uid.trim().equals(d.getUserId().trim())) {
                return d;
            }
        }
        // 2) Invitación pendiente por correo (sólo fichas sin uid).
        for (Doctor d : doctores) {
            if (d == null || d.isLinked()) {
                continue;
            }
            if (d.matchesAccount(uid, email)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Escribe el {@code uid} en la ficha invitada y, en el mismo paso, guarda el
     * {@code doctorId} en {@code /users/{uid}}.
     * <p>
     * <b>Las dos escrituras son necesarias y las dos están verificadas por las reglas.</b>
     * <ul>
     *   <li>En {@code doctors} sólo se permite escribir el campo {@code userId}, y sólo
     *       si el {@code email} de la ficha coincide con el correo del token y nadie la
     *       había reclamado antes. Así un paciente no puede apropiarse de la ficha de
     *       otro médico.</li>
     *   <li>En {@code users} sólo se permite escribir {@code doctorId} si esa ficha
     *       existe, tiene su correo y está libre. Es lo que convierte la cuenta en
     *       «médico» sin que nadie pueda autoascenderse.</li>
     * </ul>
     * Ambas son <i>best-effort</i>: la vinculación por correo sigue funcionando aunque
     * fallen, así que no se bloquea la entrada al médico.
     */
    private void vincularSiHaciaFalta(@NonNull Doctor doctor, @NonNull String uid) {
        if (doctor.isLinked() || doctor.getId() == null || doctor.getId().trim().isEmpty()) {
            return;
        }

        Map<String, FirestoreApi.Value> camposFicha = new HashMap<>();
        camposFicha.put("userId", FirestoreApi.ofString(uid));

        FirestoreApi.FirestoreDocument ficha = new FirestoreApi.FirestoreDocument();
        ficha.fields = camposFicha;

        api.updateDocument(FirebaseConfig.COLLECTION_DOCTORS, doctor.getId(),
                        new java.util.ArrayList<>(camposFicha.keySet()), ficha)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.i(TAG, "Ficha médica vinculada a la cuenta " + uid);
                        } else {
                            android.util.Log.w(TAG, "No se pudo escribir userId en la ficha "
                                    + "(HTTP " + response.code()
                                    + "); se seguirá usando el correo");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión al vincular la ficha médica");
                    }
                });

        // Segundo paso: dejar el doctorId en el perfil. Es lo que hace que las
        // reglas de `appointments` devuelvan sólo las citas de este médico.
        Map<String, FirestoreApi.Value> camposPerfil = new HashMap<>();
        camposPerfil.put("doctorId", FirestoreApi.ofString(doctor.getId()));

        FirestoreApi.FirestoreDocument perfil = new FirestoreApi.FirestoreDocument();
        perfil.fields = camposPerfil;

        api.updateDocument(COLLECTION_USERS, uid,
                        new java.util.ArrayList<>(camposPerfil.keySet()), perfil)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.i(TAG, "doctorId guardado en el perfil de " + uid);
                        } else {
                            android.util.Log.w(TAG, "No se pudo guardar doctorId en el perfil "
                                    + "(HTTP " + response.code() + ")");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión al guardar el doctorId");
                    }
                });
    }

    // ───────────────────────── Escritura del rol ─────────────────────────

    /**
     * Deja el perfil del médico con su rol y su {@code doctorId}, y devuelve la ficha.
     * <p>
     * <b>Se escribe el rol porque aquí sí se sabe.</b> La ficha existe y su correo es
     * el del token: la regla de {@code users} acepta {@code doctorId} con esa
     * comprobación, y {@code role: "doctor"} lo único que hace es reflejar en el
     * documento lo que ya es cierto. Lo que <b>nunca</b> ocurre es escribir
     * {@code role} sobre una cuenta de administrador: esas salen antes de llegar aquí.
     */
    private void guardarPerfilDeMedico(@NonNull String uid, @Nullable String email,
                                       @NonNull final Doctor ficha,
                                       @NonNull final RoleCallback callback) {
        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("role", FirestoreApi.ofString(UserRole.DOCTOR.getValue()));
        f.put("email", FirestoreApi.ofString(email != null ? email : ""));
        f.put("doctorId", FirestoreApi.ofString(ficha.getId()));

        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = f;

        api.updateDocument(COLLECTION_USERS, uid,
                        new java.util.ArrayList<>(f.keySet()), doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        // Aunque falle la escritura, la ficha ya identifica al médico:
                        // se entra igual y la próxima vez se reintenta.
                        if (!response.isSuccessful() && response.code() != 403) {
                            android.util.Log.w(TAG, "No se pudo completar el perfil del "
                                    + "médico (HTTP " + response.code() + ")");
                        }
                        callback.onRolConFicha(UserRole.DOCTOR, ficha.getId(), ficha);
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        android.util.Log.w(TAG, "Sin conexión al completar el perfil del médico");
                        callback.onRolConFicha(UserRole.DOCTOR, ficha.getId(), ficha);
                    }
                });
    }

    /**
     * Deja constancia del perfil en {@code /users/{uid}} si aún no existe.
     * <p>
     * <b>Por qué hacía falta.</b> El documento de rol no lo escribía nadie: la ruta
     * {@code administrators} sólo existía en el camino del administrador. Y sin ese
     * documento pasan dos cosas graves:
     * <ul>
     *   <li>{@code miRol()} devuelve {@code ''}, así que un administrador que ya tiene
     *       su documento con {@code role: "admin"} nunca era reconocido… pero peor: las
     *       cuentas nuevas se quedaban sin perfil y dependían del respaldo local.</li>
     *   <li>Las reglas de {@code appointments} necesitan {@code miDoctorId()} del
     *       perfil: sin documento, la consulta de la agenda recibe <b>403</b> y el
     *       médico ve «sin conexión».</li>
     * </ul>
     * <p>
     * <b>Cómo se escribe sin pisar nada.</b> Se usa {@code PATCH} con
     * {@code updateMask} de sólo {@code role}/{@code email}/{@code fullName}/
     * {@code createdAt}. Si el documento ya existe con {@code role: "admin"} o
     * {@code "doctor"}, el {@code role} enviado es {@code "patient"}… por eso sólo se
     * llama cuando se sabe que no hay perfil (ver {@link #cargarRol}), o con el rol
     * real ya resuelto.
     *
     * @param rol rol real ya resuelto (no se inventa: lo decide la ficha o el admin)
     */
    public void asegurarPerfil(@NonNull String uid, @NonNull String email,
                               @Nullable String fullName, @NonNull UserRole rol,
                               @Nullable String doctorId,
                               @NonNull final RoleCallback callback) {
        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("role", FirestoreApi.ofString(rol.getValue()));
        f.put("email", FirestoreApi.ofString(email));
        f.put("fullName", FirestoreApi.ofString(fullName != null ? fullName : ""));
        f.put("createdAt", FirestoreApi.ofInteger(System.currentTimeMillis()));
        if (doctorId != null && !doctorId.trim().isEmpty()) {
            f.put("doctorId", FirestoreApi.ofString(doctorId));
        }

        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = f;

        // PATCH con máscara: crea el documento si no existe y no pisa campos ajenos.
        api.updateDocument(COLLECTION_USERS, uid,
                        new java.util.ArrayList<>(f.keySet()), doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful() || response.code() == 403) {
                            // 403 aquí no invalida nada: el rol ya se resolvió leyendo
                            // la ficha, y esa lectura es la que autoriza la agenda.
                            callback.onRole(rol, doctorId);
                        } else {
                            callback.onFallback("No se pudo guardar el perfil: HTTP "
                                    + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onFallback("Sin conexión al guardar el perfil: "
                                + t.getMessage());
                    }
                });
    }

    /** Cambia el rol de un usuario. Sólo un administrador puede hacerlo (regla). */
    public void asignarRol(@NonNull String uid, @NonNull UserRole rol,
                           @Nullable String doctorId,
                           @NonNull final RoleCallback callback) {
        final String doctorIdAsignado = doctorId != null ? doctorId : "";

        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("role", FirestoreApi.ofString(rol.getValue()));
        f.put("doctorId", FirestoreApi.ofString(doctorIdAsignado));

        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = f;

        api.updateDocument(COLLECTION_USERS, uid,
                        new java.util.ArrayList<>(f.keySet()), doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            callback.onRole(rol, doctorIdAsignado);
                        } else {
                            callback.onFallback("No se pudo asignar el rol: HTTP "
                                    + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onFallback("Sin conexión al asignar el rol");
                    }
                });
    }

    // ───────────────────────── Utilidades ─────────────────────────

    @Nullable
    private String leerTexto(@Nullable Map<String, FirestoreApi.Value> campos,
                             @NonNull String clave) {
        if (campos == null) {
            return null;
        }
        FirestoreApi.Value v = campos.get(clave);
        return v != null ? v.stringValue : null;
    }
}
