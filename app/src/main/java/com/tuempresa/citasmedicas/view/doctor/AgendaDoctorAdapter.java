package com.tuempresa.citasmedicas.view.doctor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adaptador de la <b>agenda del médico</b>.
 * <p>
 * Muestra lo que el médico necesita en consulta: <b>hora, paciente, edad, motivo y
 * estado</b>. El adaptador del paciente ({@code AppointmentAdapter}) muestra el
 * nombre del doctor porque es lo que le interesa a quien reserva; reutilizarlo aquí
 * daría una tarjeta con el propio nombre del médico repetido en todas las filas.
 */
public class AgendaDoctorAdapter
        extends ListAdapter<Appointment, AgendaDoctorAdapter.CitaViewHolder> {

    private static final String[] MESES = {
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"
    };

    public AgendaDoctorAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Appointment> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Appointment>() {
                @Override
                public boolean areItemsTheSame(@NonNull Appointment a, @NonNull Appointment b) {
                    return igual(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Appointment a, @NonNull Appointment b) {
                    return igual(a.getId(), b.getId())
                            && igual(a.getTime(), b.getTime())
                            && igual(a.getPatientName(), b.getPatientName())
                            && igual(a.getReason(), b.getReason())
                            // Se usa equals() y no == para que la comprobación sea
                            // explícita: con dos enums == ya compara identidad, pero
                            // lint marca el operador como sospechoso dentro de DiffUtil
                            // porque el mismo patrón con objetos daría un fallo sutil.
                            && java.util.Objects.equals(a.getStatus(), b.getStatus());
                }
            };

    private static boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public CitaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agenda_cita, parent, false);
        return new CitaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CitaViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class CitaViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTime, tvPatient, tvAge, tvStatus, tvReason, tvSpecialty;

        CitaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvPatient = itemView.findViewById(R.id.tvPatient);
            tvAge = itemView.findViewById(R.id.tvPatientAge);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvSpecialty = itemView.findViewById(R.id.tvSpecialty);
        }

        void bind(@NonNull Appointment item) {
            Context ctx = itemView.getContext();

            tvTime.setText(texto(item.getTime()));
            tvPatient.setText(texto(item.getPatientName()));
            tvSpecialty.setText(texto(item.getSpecialtyName()));

            int edad = item.getPatientAge();
            if (edad > 0) {
                tvAge.setText(ctx.getResources().getQuantityString(R.plurals.years, edad, edad));
                tvAge.setVisibility(View.VISIBLE);
            } else {
                tvAge.setVisibility(View.GONE);
            }

            String motivo = item.getReason();
            if (motivo != null && !motivo.trim().isEmpty()) {
                tvReason.setText(ctx.getString(R.string.fmt_reason, motivo.trim()));
                tvReason.setVisibility(View.VISIBLE);
            } else {
                tvReason.setVisibility(View.GONE);
            }

            pintarEstado(ctx, item.getStatus() != null
                    ? item.getStatus() : AppointmentStatus.CONFIRMED);
        }

        private void pintarEstado(Context ctx, AppointmentStatus status) {
            switch (status) {
                case CANCELLED:
                    tvStatus.setText(R.string.status_cancelled);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_cancelled));
                    break;
                case PENDING:
                    tvStatus.setText(R.string.status_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending));
                    break;
                case CONFIRMED:
                default:
                    tvStatus.setText(R.string.status_confirmed);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_confirmed));
                    break;
            }
        }

        private String texto(String valor) {
            return valor != null ? valor : "";
        }
    }

    /**
     * Formatea una fecha {@code yyyy-MM-dd} como {@code "12 mar"}.
     * <p>
     * Es estático y público para poder probarlo sin inflar la vista.
     */
    @NonNull
    public static String formatearFecha(@NonNull String fecha) {
        if (fecha == null || fecha.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat entrada = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = entrada.parse(fecha);
            if (d == null) {
                return fecha;
            }
            SimpleDateFormat dia = new SimpleDateFormat("dd", Locale.US);
            SimpleDateFormat mes = new SimpleDateFormat("MM", Locale.US);
            int m = Integer.parseInt(mes.format(d)) - 1;
            String nombreMes = (m >= 0 && m < MESES.length) ? MESES[m] : "";
            return dia.format(d) + " " + nombreMes;
        } catch (ParseException e) {
            return fecha;
        }
    }
}
