package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.model.Doctor;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Alta de médicos desde la app (sólo administrador).
 * <p>
 * <b>El problema que resuelve.</b> Añadir un médico obligaba a abrir la consola de
 * Firebase y crear el documento a mano, campo por campo. Eso es lento y produce
 * fichas inconsistentes (falta {@code specialtyId}, el nombre con otro formato, la
 * tarifa como texto...). Desde aquí se crea con los mismos campos que ya lee el
 * catálogo, así que el médico aparece en la lista de los pacientes de inmediato.
 * <p>
 * <b>Cómo entra el médico a la app.</b> No se crea una contraseña por él (sería un
 * riesgo y una fuga de datos). Se guarda su <b>correo</b> en la ficha
 * ({@code email}). Cuando el médico se registra en la app con ese mismo correo,
 * {@link FirestoreUserRepository} detecta la coincidencia, le da el rol de médico y
 * le escribe su {@code uid} en la ficha. A partir de ahí la vinculación es por
 * {@code uid}.
 * <p>
 * <b>Quién puede crear.</b> Firestore lo decide: la regla de la colección
 * {@code doctors} exige que el llamante sea administrador (leído de
 * {@code /users/{uid}}). Aunque alguien manipulase la app, el servidor rechazaría la
 * escritura.
 */
public class FirestoreAdminRepository {

    private static final String TAG = "AltaMedicos";

    private final FirestoreApi api;

    public FirestoreAdminRepository(@NonNull android.content.Context context) {
        this.api = FirestoreClient.getApi(context.getApplicationContext());
    }

    /** Resultado del alta. */
    public interface AltaCallback {
        /** @param doctorId id del documento creado en {@code doctors} */
        void onCreado(@NonNull String doctorId);

        void onError(@NonNull String mensaje);
    }

    /**
     * Crea la ficha del médico.
     * <p>
     * El id se genera a partir del correo ({@code doc_<correo normalizado>}) para que
     * sea legible en la consola y para que un doble toque en "Registrar" no cree dos
     * fichas de la misma persona: el segundo intento sobrescribe la misma ruta.
     */
    public void crearMedico(@NonNull Doctor doctor, @NonNull String email,
                            @NonNull final AltaCallback callback) {
        if (doctor.getFullName() == null || doctor.getFullName().trim().isEmpty()) {
            callback.onError("Escribe el nombre del médico");
            return;
        }
        if (email.trim().isEmpty() || !email.contains("@")) {
            callback.onError("Escribe un correo válido");
            return;
        }

        final String doctorId = idDesdeEmail(email);
        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = aCampos(doctor, email);

        api.updateDocument(FirebaseConfig.COLLECTION_DOCTORS, doctorId,
                        new java.util.ArrayList<>(doc.fields.keySet()), doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.i(TAG, "Médico '" + doctorId + "' registrado");
                            callback.onCreado(doctorId);
                        } else if (response.code() == 404) {
                            // La colección aún no tiene ningún documento: se crea con
                            // ruta explícita, que es lo que admite la API REST.
                            crearConRuta(doctorId, doc, callback);
                        } else {
                            callback.onError(mensajeDeError(response.code(),
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    /** Crea el documento cuando el PATCH devuelve 404 (no existía y no se pudo crear). */
    private void crearConRuta(@NonNull final String doctorId,
                              @NonNull FirestoreApi.FirestoreDocument doc,
                              @NonNull final AltaCallback callback) {
        api.createDocument(FirebaseConfig.COLLECTION_DOCTORS + "/" + doctorId, doc)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            callback.onCreado(doctorId);
                        } else {
                            callback.onError(mensajeDeError(response.code(),
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    // ───────────────────────── Conversión ─────────────────────────

    /**
     * Campos del documento tal y como los lee {@code FirestoreMapper.toDoctor}.
     * <p>
     * Todos los campos son obligatorios para el catálogo menos
     * {@code specialtyId}/{@code hospital}/{@code bio}, que se envían vacíos si no se
     * rellenan: así la ficha siempre tiene la misma forma y el listado del paciente no
     * muestra huecos.
     */
    @NonNull
    static Map<String, FirestoreApi.Value> aCampos(@NonNull Doctor d, @NonNull String email) {
        Map<String, FirestoreApi.Value> f = new HashMap<>();
        f.put("fullName", FirestoreApi.ofString(d.getFullName()));
        f.put("specialtyId", FirestoreApi.ofString(d.getSpecialtyId()));
        f.put("specialtyName", FirestoreApi.ofString(d.getSpecialtyName()));
        f.put("bio", FirestoreApi.ofString(d.getBio()));
        f.put("rating", FirestoreApi.ofDouble(d.getRating()));
        f.put("yearsExperience", FirestoreApi.ofInteger(d.getYearsExperience()));
        f.put("age", FirestoreApi.ofInteger(d.getAge()));
        f.put("consultationFee", FirestoreApi.ofDouble(d.getConsultationFee()));
        f.put("hospital", FirestoreApi.ofString(d.getHospital()));
        f.put("photoUrl", FirestoreApi.ofString(d.getPhotoUrl()));
        // La invitación: sin este campo el médico no podría reclamar su ficha.
        f.put("email", FirestoreApi.ofString(email.trim().toLowerCase(java.util.Locale.ROOT)));
        // Vacío a propósito: se rellena solo cuando el médico entra por primera vez.
        f.put("userId", FirestoreApi.ofString(""));
        f.put("createdAt", FirestoreApi.ofInteger(System.currentTimeMillis()));
        return f;
    }

    /**
     * Id de documento derivado del correo.
     * <p>
     * Se normaliza a minúsculas y se sustituye todo lo que no sea letra o número por
     * {@code _} porque el id de un documento de Firestore no puede contener {@code /}.
     * Mismo criterio que {@code AuthRepository.idDe(email)} para que ambos lados de la
     * app nombren igual a la misma persona.
     */
    @NonNull
    static String idDesdeEmail(@NonNull String email) {
        String limpio = email.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        if (limpio.isEmpty()) {
            limpio = "medico";
        }
        return "doc_" + limpio;
    }

    // ───────────────────────── Errores ─────────────────────────

    @NonNull
    private String mensajeDeError(int httpCode, @Nullable String jsonError) {
        String detalle = FirebaseErrors.describirErrorFirestore(httpCode, jsonError);
        if (detalle != null && !detalle.isEmpty()
                && !detalle.equals(jsonError == null ? "" : jsonError.trim())) {
            return detalle;
        }
        if (httpCode == 403) {
            return "Firestore rechazó el alta (403). Sólo un administrador puede añadir "
                    + "médicos: revisa el rol de tu cuenta en /users";
        }
        return "No se pudo registrar el médico: HTTP " + httpCode;
    }

    @NonNull
    private String mensajeDeRed(@NonNull Throwable t) {
        String causa = t.getMessage() != null ? t.getMessage() : "";
        if (causa.contains("Unable to resolve host") || causa.contains("UnknownHost")) {
            return "Sin conexión a internet: no se pudo registrar al médico";
        }
        return "Error de conexión con Firestore: " + causa;
    }
}
