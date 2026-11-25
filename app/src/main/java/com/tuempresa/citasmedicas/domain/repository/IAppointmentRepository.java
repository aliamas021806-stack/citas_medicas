package com.tuempresa.citasmedicas.domain.repository;

import androidx.lifecycle.LiveData;

import com.tuempresa.citasmedicas.model.Appointment;

import java.util.List;

/**
 * Contrato del repositorio de citas médicas.
 * <p>
 * <b>Decisiones de diseño relevantes para la futura conexión con Firestore:</b>
 * <ul>
 *   <li><b>Habla en modelos, no en entidades.</b> Devuelve {@link Appointment}
 *       (modelo de dominio) y nunca {@code AppointmentEntity} (tabla de Room). Así,
 *       cuando Firestore sea la fuente de datos, la UI no se entera: no tiene que
 *       cambiar ni un {@code import} aunque la cita venga de la nube.</li>
 *   <li><b>Expone {@code LiveData}.</b> Room lo produce de forma nativa, así que la
 *       implementación local es una simple delegación. Una implementación remota
 *       puede alimentar el mismo {@code LiveData} desde un {@code MutableLiveData},
 *       o mantener Room como caché y observar la tabla.</li>
 *   <li><b>Identificador como {@code String}.</b> Firestore identifica los
 *       documentos con cadenas, así que el dominio ya usa el tipo correcto y no
 *       habrá conversiones al sincronizar.</li>
 * </ul>
 * El identificador local ({@code long} de SQLite) se conserva en
 * {@code Appointment.id} como texto; cuando se sincronice con Firestore se podrá
 * guardar además el id remoto sin tocar esta interfaz.
 */
public interface IAppointmentRepository {

    /**
     * Flujo observable de todas las citas, ordenadas por fecha y hora.
     * <p>
     * Es {@code LiveData} (y no un callback puntual) porque la UI necesita
     * actualizarse sola al insertar o cancelar: es la ventaja del patrón
     * <i>offline first</i>.
     */
    LiveData<List<Appointment>> observeAll();

    /**
     * Agenda una cita.
     *
     * @param appointment cita a guardar (sin id, se asigna al insertar)
     * @param callback    recibe el id asignado por el almacenamiento
     */
    void create(Appointment appointment, RepositoryCallback<String> callback);

    /** Cancela una cita existente por su identificador. */
    void cancel(String appointmentId, RepositoryCallback<Void> callback);

    /** Elimina definitivamente una cita. */
    void delete(Appointment appointment, RepositoryCallback<Void> callback);
}
