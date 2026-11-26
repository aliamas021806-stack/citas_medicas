package com.tuempresa.citasmedicas.model;

/**
 * Credenciales y datos de registro del paciente.
 * <p>
 * Incluye el <b>expediente</b> que el usuario escribe en el formulario: teléfono,
 * grupo sanguíneo, aseguradora y alergias. Antes estos datos se generaban
 * aleatoriamente en el mock, de modo que el perfil mostraba información inventada
 * en lugar de la del paciente.
 */
public class AuthRequest {

    private String fullName;
    private String lastName;
    private String email;
    private String password;
    private int age;
    private String phone;
    private String bloodType;
    private String insurance;
    private String allergies;

    public AuthRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public AuthRequest(String fullName, String email, String password) {
        this(fullName, email, password, 0);
    }

    public AuthRequest(String fullName, String email, String password, int age) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.age = age;
    }

    /**
     * Constructor completo del registro.
     *
     * @param lastName apellidos (puede ir vacío)
     */
    public AuthRequest(String fullName, String lastName, String email, String password,
                       int age, String phone, String bloodType, String insurance,
                       String allergies) {
        this.fullName = fullName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.age = age;
        this.phone = phone;
        this.bloodType = bloodType;
        this.insurance = insurance;
        this.allergies = allergies;
    }

    public String getFullName() { return fullName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    /** Edad indicada en el registro (0 si no se especifica). */
    public int getAge() { return age; }

    public String getPhone() { return phone; }
    public String getBloodType() { return bloodType; }
    public String getInsurance() { return insurance; }
    public String getAllergies() { return allergies; }
}
