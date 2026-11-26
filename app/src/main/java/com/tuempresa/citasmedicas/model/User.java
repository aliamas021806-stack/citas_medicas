package com.tuempresa.citasmedicas.model;

import java.util.Objects;

/**
 * Usuario (paciente) de la aplicación.
 * <p>
 * Además de las credenciales básicas, guarda los datos del <b>expediente</b> que el
 * paciente introduce al registrarse: teléfono, grupo sanguíneo, aseguradora y
 * alergias. Antes estos valores se inventaban con datos aleatorios del mock, así que
 * el perfil mostraba información que no era del usuario.
 */
public class User {

    private String id;
    private String fullName;
    private String lastName;
    private String email;
    private String phone;
    private int age;
    private String bloodType;
    private String insurance;
    private String allergies;

    /**
     * Rol del usuario: paciente, médico o administrador.
     * <p>
     * Nunca es {@code null}: por defecto {@link UserRole#PATIENT}, que es el permiso
     * mínimo.
     */
    private UserRole role = UserRole.PATIENT;

    /**
     * Si el rol es {@link UserRole#DOCTOR}, id del documento en {@code doctors}
     * (ej. {@code doc_01}) que identifica su agenda.
     */
    private String doctorId;

    public User() {
    }

    public User(String id, String fullName, String email, String phone) {
        this(id, fullName, email, phone, 0);
    }

    public User(String id, String fullName, String email, String phone, int age) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.age = age;
    }

    /**
     * Constructor completo con el expediente del paciente.
     *
     * @param fullName  nombre de pila
     * @param lastName  apellidos (puede ir vacío)
     */
    public User(String id, String fullName, String lastName, String email, String phone,
                int age, String bloodType, String insurance, String allergies) {
        this.id = id;
        this.fullName = fullName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.age = age;
        this.bloodType = bloodType;
        this.insurance = insurance;
        this.allergies = allergies;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    /** Nombre de pila. */
    public String getFirstName() { return fullName; }
    public void setFirstName(String firstName) { this.fullName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    /**
     * Nombre completo para mostrar: nombre + apellidos.
     * <p>
     * Si no hay apellidos devuelve sólo el nombre, así que el código anterior que
     * guardaba el nombre completo en este campo sigue funcionando igual.
     */
    public String getFullName() {
        String nombre = fullName != null ? fullName.trim() : "";
        String apellidos = lastName != null ? lastName.trim() : "";
        if (nombre.isEmpty()) {
            return apellidos;
        }
        if (apellidos.isEmpty()) {
            return nombre;
        }
        return nombre + " " + apellidos;
    }

    /** Se conserva por compatibilidad: asigna el nombre de pila. */
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    /** Edad del paciente en años. */
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public String getInsurance() { return insurance; }
    public void setInsurance(String insurance) { this.insurance = insurance; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    /** Rol del usuario. Nunca devuelve {@code null}. */
    public UserRole getRole() {
        return role != null ? role : UserRole.PATIENT;
    }

    public void setRole(UserRole role) {
        this.role = role != null ? role : UserRole.PATIENT;
    }

    /** Id del documento en {@code doctors} cuando el rol es médico. */
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }

    /** Atajo: ¿es un médico? */
    public boolean isDoctor() { return getRole() == UserRole.DOCTOR; }

    /** Atajo: ¿es administrador? */
    public boolean isAdmin() { return getRole() == UserRole.ADMIN; }

    public String getInitials() {
        String completo = getFullName();
        if (completo == null || completo.trim().isEmpty()) {
            return "?";
        }
        String[] parts = completo.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            sb.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}
