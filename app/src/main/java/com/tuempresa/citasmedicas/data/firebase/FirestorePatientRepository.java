package com.tuempresa.citasmedicas.data.firebase;

import androidx.annotation.NonNull;

import com.tuempresa.citasmedicas.domain.repository.IPatientRepository;
import com.tuempresa.citasmedicas.domain.repository.RepositoryCallback;
import com.tuempresa.citasmedicas.model.Patient;

import java.util.List;

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
                            callback.onError(mensajeDeError(response.code()));
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
                            callback.onError(mensajeDeError(response.code()));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<FirestoreApi.FirestoreDocument> call,
                                          @NonNull Throwable t) {
                        callback.onError(mensajeDeRed(t));
                    }
                });
    }

    @NonNull
    private String mensajeDeError(int httpCode) {
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
