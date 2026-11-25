package com.tuempresa.citasmedicas.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de paciente: contrato JSON de {@code GET /api/patients} y
 * {@code GET /api/patients/{id}}.
 * <p>
 * Incluye la EDAD ({@code age}) como campo propio de la información del paciente.
 * Los datos del backend son sintéticos (generados por IA), pero el contrato es el
 * de un expediente clínico real.
 */
public class PatientDto {

    @SerializedName("id")
    private String id;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("email")
    private String email;

    @SerializedName("phone")
    private String phone;

    @SerializedName("age")
    private Integer age;

    @SerializedName("bloodType")
    private String bloodType;

    @SerializedName("gender")
    private String gender;

    @SerializedName("address")
    private String address;

    @SerializedName("insurance")
    private String insurance;

    @SerializedName("allergies")
    private String allergies;

    @SerializedName("chronicConditions")
    private String chronicConditions;

    @SerializedName("heightCm")
    private Double heightCm;

    @SerializedName("weightKg")
    private Double weightKg;

    @SerializedName("emergencyContact")
    private String emergencyContact;

    @SerializedName("createdAt")
    private Long createdAt;

    public PatientDto() {
    }

    public PatientDto(String id, String fullName, String email, String phone, Integer age,
                      String bloodType, String gender, String address, String insurance,
                      String allergies, String chronicConditions, Double heightCm,
                      Double weightKg, String emergencyContact, Long createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.age = age;
        this.bloodType = bloodType;
        this.gender = gender;
        this.address = address;
        this.insurance = insurance;
        this.allergies = allergies;
        this.chronicConditions = chronicConditions;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.emergencyContact = emergencyContact;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    /** EDAD del paciente; {@code null} si el servidor no la informa. */
    public Integer getAge() {
        return age;
    }

    public String getBloodType() {
        return bloodType;
    }

    public String getGender() {
        return gender;
    }

    public String getAddress() {
        return address;
    }

    public String getInsurance() {
        return insurance;
    }

    public String getAllergies() {
        return allergies;
    }

    public String getChronicConditions() {
        return chronicConditions;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public Long getCreatedAt() {
        return createdAt;
    }
}
