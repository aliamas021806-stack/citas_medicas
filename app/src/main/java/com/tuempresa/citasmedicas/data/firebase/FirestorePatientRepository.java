package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Patient;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Repositorio de pacientes contra <b>Cloud Firestore</b> (REST).
 * <p>
 * Implementa {@link IPatientRepository}. {@code PatientViewModel} y la pantalla de
 * perfil no cambian.
 */
public class FirestorePatientRepository implements IPatientRepository {

    private final FirestoreApi api;

    /**
     * @param context contexto de aplicación, necesario para enviar el {@code idToken}
     *                del usuario en las peticiones (ver {@code AuthHeaderInterceptor}).
     */
    public FirestorePatientRepository(@NonNull android.content.Context context) {
        this.api = FirestoreClient.getApi(context.getApplicationContext());
    }

    /**
     * Constructor sin contexto.
     * <p>
     * No puede leer el token, así que con reglas endurecidas las peticiones serán
     * rechazadas. Se conserva por compatibilidad.
     *
     * @deprecated usar {@link #FirestorePatientRepository(android.content.Context)}
     */
    @Deprecated
    public FirestorePatientRepository() {
        this.api = FirestoreClient.getApi();
    }

    @Override
    public void getPatients(@NonNull final RepositoryCallback<List<Patient>> callback) {
        api.listDocuments(FirebaseConfig.COLLECTION_PATIENTS)
                .enqueue(new retrofit2.Callback<FirestoreApi.DocumentList>() {
                    @Override
                    public void onResponse(@NonNull Call<FirestoreApi.DocumentList> call,
                                           @NonNull Response<FirestoreApi.DocumentList> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Patient> lista =
                                    FirestoreMapper.toPatients(response.body().documents);
                            if (lista.isEmpty()) {
                                callback.onError("La colección 'patients' está vacía en Firestore");
                                return;
                            }
                            callback.onSuccess(lista);
                        } else {
                            callback.onError(mensajeDeError(response.code(),
                                    FirebaseErrors.cuerpoDeError(response)));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.DocumentList> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    @Override
    public void getPatientById(@NonNull String patientId,
                               @NonNull final RepositoryCallback<Patient> callback) {
        api.getDocument(FirebaseConfig.COLLECTION_PATIENTS, patientId)
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Patient p = FirestoreMapper.toPatient(response.body());
                            if (p == null) {
                                callback.onError("No se encontró el paciente solicitado");
                                return;
                            }
                            callback.onSuccess(p);
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

    /**
     * Guarda el expediente en Firestore.
     * <p>
     * Usa {@code PATCH} con {@code updateMask.fieldPaths} para no perder campos: sin
     * la máscara, Firestore <b>reemplaza</b> el documento completo y borraría los
     * campos que no se envían.
     * <p>
     * Estrategia: primero se intenta actualizar (el caso normal, el paciente ya
     * existe); si responde 404 se crea.
     */
    @Override
    public void savePatient(@NonNull final Patient patient,
                            @NonNull final RepositoryCallback<Void> callback) {
        if (patient == null || patient.getId() == null || patient.getId().trim().isEmpty()) {
            callback.onError("El expediente no tiene identificador de paciente");
            return;
        }
        final String id = patient.getId().trim();
        final Map<String, FirestoreApi.Value> campos = aCampos(patient);

        api.updateDocument(FirebaseConfig.COLLECTION_PATIENTS, id,
                        new java.util.ArrayList<>(campos.keySet()), documentoCon(campos))
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else if (response.code() == 404) {
                            crear(id, campos, callback);
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

    /** Crea el documento del expediente cuando aún no existía. */
    private void crear(@NonNull String id,
                       @NonNull Map<String, FirestoreApi.Value> campos,
                       @NonNull final RepositoryCallback<Void> callback) {
        api.createDocument(FirebaseConfig.COLLECTION_PATIENTS + "/" + id,
                        documentoCon(campos))
                .enqueue(new retrofit2.Callback<FirestoreApi.FirestoreDocument>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<FirestoreApi.FirestoreDocument> call,
                            @NonNull Response<FirestoreApi.FirestoreDocument> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
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

    /** Envuelve los campos en el documento que espera la API REST. */
    @NonNull
    private FirestoreApi.FirestoreDocument documentoCon(
            @NonNull Map<String, FirestoreApi.Value> campos) {
        FirestoreApi.FirestoreDocument doc = new FirestoreApi.FirestoreDocument();
        doc.fields = campos;
        return doc;
    }

    /**
     * Convierte el expediente al formato de campos de Firestore.
     * <p>
     * Se usa el mismo nombre de campo que ya existe en la colección {@code patients}
     * para no romper los documentos anteriores.
     */
    @NonNull
    public static Map<String, FirestoreApi.Value> aCampos(@NonNull Patient p) {
        Map<String, FirestoreApi.Value> f = new java.util.HashMap<>();
        f.put("fullName", FirestoreApi.ofString(p.getFullName()));
        f.put("email", FirestoreApi.ofString(p.getEmail()));
        f.put("phone", FirestoreApi.ofString(p.getPhone()));
        f.put("age", FirestoreApi.ofInteger(p.getAge()));
        f.put("bloodType", FirestoreApi.ofString(p.getBloodType()));
        f.put("insurance", FirestoreApi.ofString(p.getInsurance()));
        f.put("allergies", FirestoreApi.ofString(p.getAllergies()));
        return f;
    }

    @NonNull
    private String mensajeDeError(int httpCode, @Nullable String jsonError) {
        String detalle = FirebaseErrors.describirErrorFirestore(httpCode, jsonError);
        if (!detalle.isEmpty() && !detalle.equals(jsonError == null ? "" : jsonError.trim())) {
            return detalle;
        }
        switch (httpCode) {
            case 401:
            case 403:
                return "Firestore ha rechazado la lectura (" + httpCode
                        + "). Revisa las Reglas de seguridad de 'patients'";
            case 404:
                return "No se encontró la colección 'patients' en Firestore (404)";
            case 429:
                return "Demasiadas peticiones a Firestore (429). Espera unos segundos";
            default:
                return "Firestore respondió con HTTP " + httpCode + " al leer pacientes";
        }
    }

    @NonNull
    private String mensajeDeRed(@NonNull Throwable t) {
        String causa = t.getMessage() != null ? t.getMessage() : "";
        if (causa.contains("Unable to resolve host") || causa.contains("UnknownHost")) {
            return "Sin conexión a internet: no se pudo contactar con Firestore";
        }
        return "Error de conexión con Firestore: " + causa;
    }
}
