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
                        if (response.isSuccessful() && response.body() != null) {
                            campos = response.body().fields;
                        } else if (response.code() != 404) {
                            // 403/500: las reglas no dejan leer ni el propio documento.
                            // Se intenta igualmente el vínculo por ficha de médico.
                            android.util.Log.w(TAG, "No se pudo leer /users/" + uid
                                    + " (HTTP " + response.code() + ")");
                        }
                        resolverDoctorOHeredar(uid, email, campos, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        // Sin red: no se puede saber el rol, pero la app debe arrancar.
                        callback.onFallback("Sin conexión al leer el rol: " + t.getMessage());
                    }
                });
    }

    /** Segundo paso: comprueba si el rol guardado ya es admin o busca su ficha médica. */
    private void resolverDoctorOHeredar(@NonNull final String uid, @Nullable final String email,
                                        @Nullable Map<String, FirestoreApi.Value> campos,
                                        @NonNull final RoleCallback callback) {
        UserRole guardado = campos != null
                ? UserRole.fromValue(leerTexto(campos, "role")) : UserRole.PATIENT;

        if (guardado.isAdmin()) {
            // El rol más alto no necesita ficha de médico.
            callback.onRole(UserRole.ADMIN, leerTexto(campos, "doctorId"));
            return;
        }

        api.listDocuments(FirebaseConfig.COLLECTION_DOCTORS)
                .enqueue(new retrofit2.Callback<FirestoreApi.DocumentList>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.DocumentList> call,
                            @NonNull Response<FirestoreApi.DocumentList> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onRole(guardado, leerTexto(campos, "doctorId"));
                            return;
                        }
                        List<Doctor> doctores = FirestoreMapper.toDoctors(response.body().documents);
                        Doctor mio = buscarFichaDeLaCuenta(doctores, uid, email);
                        if (mio == null) {
                            // Ni admin ni médico: paciente con el rol ya guardado.
                            callback.onRole(guardado, leerTexto(campos, "doctorId"));
                            return;
                        }
                        vincularSiHaciaFalta(mio, uid);
                        callback.onRole(UserRole.DOCTOR, mio.getId());
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.DocumentList> call,
                                          @NonNull Throwable t) {
                        callback.onRole(guardado, leerTexto(campos, "doctorId"));
                    }
                });
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
     * Crea el documento de rol al registrarse.
     * <p>
     * Siempre con {@code role = "patient"}: <b>nadie puede autoproclamarse</b> médico o
     * administrador desde la app. El alta de médicos la hace un administrador.
     */
    public void crearRolPaciente(@NonNull String uid, @NonNull String email,
                                 @NonNull String fullName,
                                 @NonNull final RoleCallback callback) {
        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("role", FirestoreApi.ofString(UserRole.PATIENT.getValue()));
        f.put("email", FirestoreApi.ofString(email));
        f.put("fullName", FirestoreApi.ofString(fullName));
        f.put("createdAt", FirestoreApi.ofInteger(System.currentTimeMillis()));

        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = f;

        // PATCH con máscara: crea el documento si no existe y no pisa un rol ya
        // asignado en los campos que no se envían.
        api.updateDocument(COLLECTION_USERS, uid,
                        new java.util.ArrayList<>(f.keySet()), doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            callback.onRole(UserRole.PATIENT, null);
                        } else {
                            callback.onFallback("No se pudo crear el rol: HTTP "
                                    + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onFallback("Sin conexión al crear el rol: " + t.getMessage());
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
