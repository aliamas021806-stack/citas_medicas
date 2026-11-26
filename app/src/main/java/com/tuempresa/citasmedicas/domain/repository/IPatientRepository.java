package com.tuempresa.citasmedicas.domain.repository;

import com.tuempresa.citasmedicas.model.Patient;

import java.util.List;

/**
 * Contrato del repositorio de pacientes (expediente, incluida la EDAD).
 * <p>
 * Igual que {@link IDoctorRepository}, permite sustituir la fuente simulada por
 * Firestore sin cambiar dominio ni presentación.
 */
public interface IPatientRepository {

    /** Listado completo de pacientes. */
    void getPatients(RepositoryCallback<List<Patient>> callback);

    /** Expediente de un paciente concreto. */
    void getPatientById(String patientId, RepositoryCallback<Patient> callback);

    /**
     * Guarda o actualiza el expediente de un paciente.
     * <p>
     * Servicio opcional: las implementaciones que no tengan backend lo dejan pasar sin
     * hacer nada (el perfil se nutre de la sesión).
     */
    void savePatient(Patient patient, RepositoryCallback<Void> callback);
}
