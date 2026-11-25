package com.tuempresa.citasmedicas.domain.repository;

import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.List;

/**
 * Contrato del repositorio de catálogo médico (especialidades, doctores y horarios).
 * <p>
 * <b>Por qué existe esta interfaz.</b> Antes los casos de uso dependían de la clase
 * concreta {@code DoctorRepository}, que construía un {@code ApiClient} con el
 * interceptor simulado. Con esta interfaz, el dominio declara <i>qué</i> necesita y
 * la capa de datos decide <i>cómo</i> lo obtiene. Eso permite, sin tocar ni una línea
 * de dominio ni de UI:
 * <ul>
 *   <li>Inyectar una implementación de prueba (datos fijos, sin red).</li>
 *   <li>Añadir una implementación con Firestore en paralelo a la actual.</li>
 *   <li>Combinar ambas (por ejemplo, Firestore con reserva a datos locales).</li>
 * </ul>
 * Esto es la <i>inversión de dependencias</i> del último principio SOLID: el dominio
 * no depende de la infraestructura, la infraestructura depende del dominio.
 */
public interface IDoctorRepository {

    /** Catálogo completo de especialidades médicas. */
    void getSpecialties(RepositoryCallback<List<Specialty>> callback);

    /** Catálogo completo de doctores. */
    void getDoctors(RepositoryCallback<List<Doctor>> callback);

    /** Doctores filtrados por especialidad. */
    void getDoctorsBySpecialty(String specialtyId, RepositoryCallback<List<Doctor>> callback);

    /** Un doctor concreto por su identificador. */
    void getDoctorById(String doctorId, RepositoryCallback<Doctor> callback);

    /** Franjas horarias disponibles de un doctor. */
    void getAvailableSlots(String doctorId, RepositoryCallback<List<TimeSlot>> callback);
}
