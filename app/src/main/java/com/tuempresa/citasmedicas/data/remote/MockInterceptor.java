package com.tuempresa.citasmedicas.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.tuempresa.citasmedicas.data.mock.MockData;
import com.tuempresa.citasmedicas.data.remote.dto.DoctorDto;
import com.tuempresa.citasmedicas.data.remote.dto.PatientDto;
import com.tuempresa.citasmedicas.data.remote.dto.SpecialtyDto;
import com.tuempresa.citasmedicas.data.remote.dto.TimeSlotDto;
import com.tuempresa.citasmedicas.model.Doctor;
import com.tuempresa.citasmedicas.model.Patient;
import com.tuempresa.citasmedicas.model.Specialty;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Interceptor OkHttp que simula un servidor REST real.
 * <p>
 * Inspecciona la ruta solicitada por {@link MedicalApiService} y devuelve el JSON
 * correspondiente. Para que la simulación sea fiel, el JSON se genera a partir de
 * los <b>DTO</b> (el mismo contrato que expondría un backend real), no de los
 * modelos de dominio: así los {@code @SerializedName} y los tipos envoltorio se
 * ejercitan de verdad en cada petición.
 */
public class MockInterceptor implements Interceptor {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final Gson gson = new Gson();

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String path = request.url().encodedPath();
        String json = route(path);
        return buildResponse(request, json);
    }

    private String route(String path) {
        // /specialties
        if (path.endsWith("specialties")) {
            return gson.toJson(toSpecialtyDtos(MockData.getSpecialties()));
        }

        // /patients/{id}
        if (path.contains("patients/") && !path.endsWith("patients")) {
            String id = path.substring(path.lastIndexOf("patients/") + "patients/".length());
            Patient patient = MockData.getPatientById(id);
            return patient == null ? "null" : gson.toJson(patientToDto(patient));
        }

        // /patients
        if (path.endsWith("patients")) {
            return gson.toJson(toPatientDtos(MockData.getPatients()));
        }

        // /doctors/specialty/{id}
        if (path.contains("doctors/specialty/")) {
            String id = path.substring(path.indexOf("doctors/specialty/") + "doctors/specialty/".length());
            return gson.toJson(toDoctorDtos(MockData.getDoctorsBySpecialty(id)));
        }

        // /doctors/{id}
        if (path.contains("doctors/") && !path.endsWith("doctors")) {
            String id = path.substring(path.lastIndexOf("doctors/") + "doctors/".length());
            Doctor doctor = MockData.getDoctorById(id);
            return doctor == null ? "null" : gson.toJson(doctorToDto(doctor));
        }

        // /slots/{doctorId}
        if (path.contains("slots/")) {
            String id = path.substring(path.lastIndexOf("slots/") + "slots/".length());
            return gson.toJson(toTimeSlotDtos(MockData.getAvailableSlots(id)));
        }

        // /doctors
        if (path.endsWith("doctors")) {
            return gson.toJson(toDoctorDtos(MockData.getDoctors()));
        }

        // Fallback
        return "[]";
    }

    // ------------------------------------------------- modelo -> DTO (payload)

    @Nullable
    public static SpecialtyDto specialtyToDto(@Nullable Specialty model) {
        if (model == null) {
            return null;
        }
        return new SpecialtyDto(model.getId(), model.getName(), model.getIconEmoji(),
                model.getDescription(), model.getDoctorCount());
    }

    @Nullable
    public static DoctorDto doctorToDto(@Nullable Doctor model) {
        if (model == null) {
            return null;
        }
        return new DoctorDto(model.getId(), model.getFullName(), model.getSpecialtyId(),
                model.getSpecialtyName(), model.getBio(), model.getRating(),
                model.getYearsExperience(), model.getAge(), model.getConsultationFee(),
                model.getHospital(), model.getPhotoUrl());
    }

    @Nullable
    public static PatientDto patientToDto(@Nullable Patient model) {
        if (model == null) {
            return null;
        }
        return new PatientDto(model.getId(), model.getFullName(), model.getEmail(),
                model.getPhone(), model.getAge(), model.getBloodType(), model.getGender(),
                model.getAddress(), model.getInsurance(), model.getAllergies(),
                model.getChronicConditions(), model.getHeightCm(), model.getWeightKg(),
                model.getEmergencyContact(), model.getCreatedAt());
    }

    @Nullable
    public static TimeSlotDto slotToDto(@Nullable TimeSlot model) {
        if (model == null) {
            return null;
        }
        return new TimeSlotDto(model.getId(), model.getDoctorId(), model.getDate(),
                model.getTime(), model.isAvailable());
    }

    private static List<SpecialtyDto> toSpecialtyDtos(@Nullable List<Specialty> models) {
        List<SpecialtyDto> out = new ArrayList<>();
        if (models != null) {
            for (Specialty s : models) {
                SpecialtyDto dto = specialtyToDto(s);
                if (dto != null) {
                    out.add(dto);
                }
            }
        }
        return out;
    }

    private static List<DoctorDto> toDoctorDtos(@Nullable List<Doctor> models) {
        List<DoctorDto> out = new ArrayList<>();
        if (models != null) {
            for (Doctor d : models) {
                DoctorDto dto = doctorToDto(d);
                if (dto != null) {
                    out.add(dto);
                }
            }
        }
        return out;
    }

    private static List<PatientDto> toPatientDtos(@Nullable List<Patient> models) {
        List<PatientDto> out = new ArrayList<>();
        if (models != null) {
            for (Patient p : models) {
                PatientDto dto = patientToDto(p);
                if (dto != null) {
                    out.add(dto);
                }
            }
        }
        return out;
    }

    private static List<TimeSlotDto> toTimeSlotDtos(@Nullable List<TimeSlot> models) {
        List<TimeSlotDto> out = new ArrayList<>();
        if (models != null) {
            for (TimeSlot t : models) {
                TimeSlotDto dto = slotToDto(t);
                if (dto != null) {
                    out.add(dto);
                }
            }
        }
        return out;
    }

    private Response buildResponse(Request request, String json) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (mock)")
                .body(ResponseBody.create(json, JSON))
                .addHeader("content-type", "application/json")
                .build();
    }
}
