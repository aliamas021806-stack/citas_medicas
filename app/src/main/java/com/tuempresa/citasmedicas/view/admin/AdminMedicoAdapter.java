package com.tuempresa.citasmedicas.view.admin;

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
import com.tuempresa.citasmedicas.model.Doctor;

/**
 * Lista de médicos registrados, para el administrador.
 * <p>
 * El dato importante de cada fila es el <b>estado de la invitación</b>: si la cuenta
 * ya está vinculada o si el médico todavía no ha entrado con su correo. Sin eso, el
 * administrador no sabría si su alta funcionó.
 */
public class AdminMedicoAdapter
        extends ListAdapter<Doctor, AdminMedicoAdapter.MedicoViewHolder> {

    public AdminMedicoAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<Doctor> DIFF =
            new DiffUtil.ItemCallback<Doctor>() {
                @Override
                public boolean areItemsTheSame(@NonNull Doctor a, @NonNull Doctor b) {
                    return igual(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Doctor a, @NonNull Doctor b) {
                    return igual(a.getId(), b.getId())
                            && igual(a.getFullName(), b.getFullName())
                            && igual(a.getEmail(), b.getEmail())
                            && igual(a.getUserId(), b.getUserId());
                }
            };

    private static boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public MedicoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MedicoViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medico_admin, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MedicoViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class MedicoViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvIniciales, tvNombre, tvEspecialidad, tvEmail, tvEstado;

        MedicoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIniciales = itemView.findViewById(R.id.tvIniciales);
            tvNombre = itemView.findViewById(R.id.tvMedicoNombre);
            tvEspecialidad = itemView.findViewById(R.id.tvMedicoEspecialidad);
            tvEmail = itemView.findViewById(R.id.tvMedicoEmail);
            tvEstado = itemView.findViewById(R.id.tvMedicoEstado);
        }

        void bind(@NonNull Doctor d) {
            tvIniciales.setText(d.getInitials());
            tvNombre.setText(d.getDisplayName());

            String especialidad = d.getSpecialtyName();
            if (especialidad != null && !especialidad.trim().isEmpty()) {
                tvEspecialidad.setText(especialidad);
                tvEspecialidad.setVisibility(View.VISIBLE);
            } else {
                tvEspecialidad.setVisibility(View.GONE);
            }

            String email = d.getEmail();
            if (email != null && !email.trim().isEmpty()) {
                tvEmail.setText(email);
                tvEmail.setVisibility(View.VISIBLE);
            } else {
                // Fichas creadas a mano en la consola antes de esta versión.
                tvEmail.setVisibility(View.GONE);
            }

            boolean vinculado = d.isLinked();
            tvEstado.setText(vinculado
                    ? R.string.admin_estado_vinculado : R.string.admin_estado_invitado);
            tvEstado.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    vinculado ? R.color.status_confirmed : R.color.status_pending));
            tvEstado.setVisibility(View.VISIBLE);
        }
    }
}
