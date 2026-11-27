package com.tuempresa.citasmedicas.view.doctor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.Appointment;
import com.tuempresa.citasmedicas.model.AppointmentStatus;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador de la <b>agenda del médico</b> con dos secciones.
 * <p>
 * La pantalla no es una lista plana a propósito: lo que <b>falta por atender</b> va
 * arriba, porque es lo que el médico mira al recibir al siguiente paciente, y lo
 * <b>ya atendido</b> queda abajo como histórico del día. Para conseguirlo con un solo
 * {@link RecyclerView} se insertan filas de cabecera ({@link Tipo#CABECERA}) entre los
 * dos grupos de citas ({@link Tipo#CITA}); así no hacen falta dos listas con scroll
 * independiente, que en un teléfono se pelearían por el espacio.
 */
public class AgendaDoctorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Qué representa una fila de la lista. */
    private enum Tipo {
        CABECERA,
        CITA
    }

    /** Fila homogénea: o una cabecera de sección, o una cita. */
    private static final class Fila {
        final Tipo tipo;
        /** Texto de la cabecera (sólo si {@code tipo == CABECERA}). */
        final String titulo;
        /** Cita (sólo si {@code tipo == CITA}). */
        final Appointment cita;

        private Fila(Tipo tipo, String titulo, Appointment cita) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.cita = cita;
        }

        static Fila cabecera(String titulo) {
            return new Fila(Tipo.CABECERA, titulo, null);
        }

        static Fila cita(Appointment cita) {
            return new Fila(Tipo.CITA, null, cita);
        }
    }

    /** Acciones que el médico puede hacer sobre una cita desde la tarjeta. */
    public interface OnCitaAccion {
        void onMarcarAtendida(@NonNull Appointment cita);

        void onMarcarNoShow(@NonNull Appointment cita);
    }

    private static final String[] MESES = {
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"
    };

    private final List<Fila> filas = new ArrayList<>();
    private final String tituloPendientes;
    private final String tituloAtendidas;
    @Nullable
    private final OnCitaAccion acciones;

    public AgendaDoctorAdapter(@NonNull String tituloPendientes,
                               @NonNull String tituloAtendidas,
                               @Nullable OnCitaAccion acciones) {
        this.tituloPendientes = tituloPendientes;
        this.tituloAtendidas = tituloAtendidas;
        this.acciones = acciones;
    }

    /**
     * Sustituye el contenido con los dos grupos ya calculados.
     * <p>
     * Sólo se añade la cabecera de un grupo si ese grupo tiene citas: una sección
     * «Ya atendidas» vacía sería ruido en la pantalla.
     */
    public void submit(@NonNull List<Appointment> pendientes,
                       @NonNull List<Appointment> atendidas) {
        List<Fila> nuevas = new ArrayList<>();
        if (!pendientes.isEmpty()) {
            nuevas.add(Fila.cabecera(tituloPendientes));
            for (Appointment a : pendientes) {
                nuevas.add(Fila.cita(a));
            }
        }
        if (!atendidas.isEmpty()) {
            nuevas.add(Fila.cabecera(tituloAtendidas));
            for (Appointment a : atendidas) {
                nuevas.add(Fila.cita(a));
            }
        }
        List<Fila> anteriores = new ArrayList<>(filas);
        filas.clear();
        filas.addAll(nuevas);
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return anteriores.size();
            }

            @Override
            public int getNewListSize() {
                return filas.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                Fila a = anteriores.get(oldPos);
                Fila b = filas.get(newPos);
                if (a.tipo != b.tipo) {
                    return false;
                }
                if (a.tipo == Tipo.CABECERA) {
                    return a.titulo.equals(b.titulo);
                }
                return igual(a.cita.getId(), b.cita.getId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Fila a = anteriores.get(oldPos);
                Fila b = filas.get(newPos);
                if (a.tipo != b.tipo) {
                    return false;
                }
                if (a.tipo == Tipo.CABECERA) {
                    return a.titulo.equals(b.titulo);
                }
                return igual(a.cita.getTime(), b.cita.getTime())
                        && igual(a.cita.getPatientName(), b.cita.getPatientName())
                        && igual(a.cita.getReason(), b.cita.getReason())
                        && java.util.Objects.equals(a.cita.getStatus(), b.cita.getStatus());
            }
        }).dispatchUpdatesTo(this);
    }

    private static boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int getItemCount() {
        return filas.size();
    }

    @Override
    public int getItemViewType(int position) {
        return filas.get(position).tipo == Tipo.CABECERA ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            return new CabeceraViewHolder(
                    inflater.inflate(R.layout.item_agenda_cabecera, parent, false));
        }
        return new CitaViewHolder(inflater.inflate(R.layout.item_agenda_cita, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Fila fila = filas.get(position);
        if (holder instanceof CabeceraViewHolder) {
            ((CabeceraViewHolder) holder).bind(fila.titulo);
        } else if (holder instanceof CitaViewHolder) {
            ((CitaViewHolder) holder).bind(fila.cita, acciones);
        }
    }

    /** Cabecera de sección: «Pendientes de atender» / «Ya atendidas». */
    static class CabeceraViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTitulo;

        CabeceraViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTituloSeccion);
        }

        void bind(String titulo) {
            tvTitulo.setText(titulo);
        }
    }

    static class CitaViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTime, tvPatient, tvAge, tvStatus, tvReason, tvSpecialty;
        private final MaterialButton btnAtendida, btnNoShow;

        CitaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvPatient = itemView.findViewById(R.id.tvPatient);
            tvAge = itemView.findViewById(R.id.tvPatientAge);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvSpecialty = itemView.findViewById(R.id.tvSpecialty);
            btnAtendida = itemView.findViewById(R.id.btnAtendida);
            btnNoShow = itemView.findViewById(R.id.btnNoShow);
        }

        void bind(@NonNull Appointment item, @Nullable OnCitaAccion acciones) {
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

            AppointmentStatus estado = item.getStatus() != null
                    ? item.getStatus() : AppointmentStatus.CONFIRMED;
            pintarEstado(ctx, estado);

            // Los botones sólo aparecen si la cita admite acción: una ya atendida no
            // necesita que se le vuelva a preguntar, y una cancelada tampoco.
            boolean accionable = estado.sePuedeAtender() && acciones != null;
            int visibilidad = accionable ? View.VISIBLE : View.GONE;
            if (btnAtendida != null) {
                btnAtendida.setVisibility(visibilidad);
                btnAtendida.setOnClickListener(v -> {
                    if (accionable) {
                        acciones.onMarcarAtendida(item);
                    }
                });
            }
            if (btnNoShow != null) {
                btnNoShow.setVisibility(visibilidad);
                btnNoShow.setOnClickListener(v -> {
                    if (accionable) {
                        acciones.onMarcarNoShow(item);
                    }
                });
            }
        }

        private void pintarEstado(Context ctx, AppointmentStatus status) {
            switch (status) {
                case CANCELLED:
                    tvStatus.setText(R.string.status_cancelled);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_cancelled));
                    break;
                case ATTENDED:
                    tvStatus.setText(R.string.status_attended);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_attended));
                    break;
                case NO_SHOW:
                    tvStatus.setText(R.string.status_no_show);
                    tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_no_show));
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
