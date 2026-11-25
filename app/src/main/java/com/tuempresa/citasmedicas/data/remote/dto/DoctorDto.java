package com.tuempresa.citasmedicas.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de doctor: contrato JSON de {@code GET /api/doctors},
 * {@code GET /api/doctors/{id}} y {@code GET /api/doctors/specialty/{id}}.
 * <p>
 * Los tipos numéricos son envoltorios ({@link Double}/{@link Integer}) para poder
 * detectar campos ausentes: Gson asigna {@code null} cuando la clave no viene en
 * el JSON, y el mapper aplica entonces un valor por defecto explícito.
 */
public class DoctorDto {

    @SerializedName("id")
    private String id;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("specialtyId")
    private String specialtyId;

    @SerializedName("specialtyName")
    private String specialtyName;

    @SerializedName("bio")
    private String bio;

    @SerializedName("rating")
    private Double rating;

    @SerializedName("yearsExperience")
    private Integer yearsExperience;

    @SerializedName("age")
    private Integer age;

    @SerializedName("consultationFee")
    private Double consultationFee;

    @SerializedName("hospital")
    private String hospital;

    @SerializedName("photoUrl")
    private String photoUrl;

    public DoctorDto() {
    }

    public DoctorDto(String id, String fullName, String specialtyId, String specialtyName,
                     String bio, Double rating, Integer yearsExperience, Integer age,
                     Double consultationFee, String hospital, String photoUrl) {
        this.id = id;
        this.fullName = fullName;
        this.specialtyId = specialtyId;
        this.specialtyName = specialtyName;
        this.bio = bio;
        this.rating = rating;
        this.yearsExperience = yearsExperience;
        this.age = age;
        this.consultationFee = consultationFee;
        this.hospital = hospital;
        this.photoUrl = photoUrl;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public String getSpecialtyName() {
        return specialtyName;
    }

    public String getBio() {
        return bio;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getYearsExperience() {
        return yearsExperience;
    }

    public Integer getAge() {
        return age;
    }

    public Double getConsultationFee() {
        return consultationFee;
    }

    public String getHospital() {
        return hospital;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
