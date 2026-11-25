package com.tuempresa.citasmedicas.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

/**
 * DTO de especialidad médica: representa EXACTAMENTE el JSON que devuelve el
 * servidor en {@code GET /api/specialties}.
 * <p>
 * ¿Por qué separarlo del modelo de dominio {@code Specialty}?
 * <ul>
 *   <li>El contrato de red puede cambiar (renombrar un campo, anidar datos) sin
 *       obligar a tocar la UI ni la lógica de negocio.</li>
 *   <li>Gson solo conoce esta clase, así que el modelo de dominio queda libre de
 *       anotaciones y de dependencias de Retrofit.</li>
 *   <li>{@code @SerializedName} documenta el nombre real del campo en el JSON,
 *       de modo que un cambio en la API se detecta en un solo sitio.</li>
 * </ul>
 * La conversión a modelo se hace en {@link DtoMapper}.
 */
public class SpecialtyDto {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("iconEmoji")
    private String iconEmoji;

    @SerializedName("description")
    private String description;

    @SerializedName("doctorCount")
    private Integer doctorCount;

    public SpecialtyDto() {
    }

    public SpecialtyDto(String id, String name, String iconEmoji,
                        String description, Integer doctorCount) {
        this.id = id;
        this.name = name;
        this.iconEmoji = iconEmoji;
        this.description = description;
        this.doctorCount = doctorCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Número de doctores de la especialidad.
     * <p>
     * Es {@link Integer} (y no {@code int}) a propósito: si el servidor omite el
     * campo, Gson deja {@code null} en lugar de {@code 0}, y así el mapper puede
     * distinguir "no informado" de "cero doctores".
     */
    public Integer getDoctorCount() {
        return doctorCount;
    }

    /** Lista vacía en lugar de {@code null}, para no propagar nulos. */
    public static java.util.List<SpecialtyDto> emptyList() {
        return new ArrayList<>();
    }
}
