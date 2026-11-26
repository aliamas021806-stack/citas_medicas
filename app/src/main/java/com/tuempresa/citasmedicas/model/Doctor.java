package com.tuempresa.citasmedicas.model;

import java.io.Serializable;

import androidx.annotation.Nullable;

/**
 * Doctor / profesional de la salud.
 * Incluye la EDAD del doctor (campo expuesto por la API mock).
 */
public class Doctor implements Serializable {

    private String id;
    private String fullName;
    private String specialtyId;
    private String specialtyName;
    private String bio;
    private double rating;
    private int yearsExperience;
    private int age;
    private double consultationFee;
    private String hospital;
    private String photoUrl;

    /**
     * Correo profesional del médico.
     * <p>
     * Es la <b>invitación</b>: cuando un administrador da de alta al médico desde la
     * app indica su correo, y la primera cuenta que inicie sesión con ese correo queda
     * vinculada a esta ficha (recibe el rol de médico y su agenda).
     */
    private String email;

    /**
     * uid de Firebase del médico ya vinculado.
     * <p>
     * Si está relleno, la vinculación es <b>fuerte</b>: sólo esa cuenta es el médico.
     * Si está vacío, la ficha está "invitada pero sin reclamar" y se vincula por email.
     */
    private String userId;

    public Doctor() {
    }

    public Doctor(String id, String fullName, String specialtyId, String specialtyName,
                  String bio, double rating, int yearsExperience, int age,
                  double consultationFee, String hospital, String photoUrl) {
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getSpecialtyId() { return specialtyId; }
    public void setSpecialtyId(String specialtyId) { this.specialtyId = specialtyId; }
    public String getSpecialtyName() { return specialtyName; }
    public void setSpecialtyName(String specialtyName) { this.specialtyName = specialtyName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(int yearsExperience) { this.yearsExperience = yearsExperience; }

    /** Edad del doctor en años. */
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public double getConsultationFee() { return consultationFee; }
    public void setConsultationFee(double consultationFee) { this.consultationFee = consultationFee; }
    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    /** Correo del médico: la invitación que usa el alta desde la app. */
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /** uid de Firebase una vez vinculado (vacío si la invitación sigue pendiente). */
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    /** ¿La ficha ya está vinculada a una cuenta concreta? */
    public boolean isLinked() {
        return userId != null && !userId.trim().isEmpty();
    }

    /**
     * ¿Esta cuenta es la del médico?
     * <p>
     * Se comprueba primero el {@code uid} (vinculación fuerte) y después el correo
     * (invitación pendiente). El correo se compara sin distinguir mayúsculas ni
     * espacios: es lo que teclea el usuario, no un identificador técnico.
     */
    public boolean matchesAccount(@Nullable String uid, @Nullable String accountEmail) {
        if (isLinked()) {
            return userId.trim().equals(uid != null ? uid.trim() : "");
        }
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        if (accountEmail == null || accountEmail.trim().isEmpty()) {
            return false;
        }
        return email.trim().equalsIgnoreCase(accountEmail.trim());
    }

    /** Prefijo del título profesional (Dr./Dra.) según el nombre. */
    public String getDisplayName() {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "Doctor";
        }
        String firstName = fullName.trim().split("\\s+")[0];
        boolean female = firstName.toLowerCase(java.util.Locale.getDefault()).endsWith("a");
        return (female ? "Dra. " : "Dr. ") + fullName;
    }

    /** Iniciales para avatar. */
    public String getInitials() {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            sb.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return sb.toString();
    }
}
