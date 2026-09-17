package com.tuempresa.citasmedicas.view.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.Specialty;

/**
 * Adaptador horizontal de especialidades médicas.
 */
public class SpecialtyAdapter extends ListAdapter<Specialty, SpecialtyAdapter.SpecialtyViewHolder> {

    public interface OnSpecialtyClickListener {
        void onSpecialtyClick(Specialty specialty);
    }

    private final OnSpecialtyClickListener listener;

    public SpecialtyAdapter(OnSpecialtyClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Specialty> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Specialty>() {
                @Override
                public boolean areItemsTheSame(@NonNull Specialty a, @NonNull Specialty b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Specialty a, @NonNull Specialty b) {
                    return a.getName().equals(b.getName());
                }
            };

    @NonNull
    @Override
    public SpecialtyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_specialty, parent, false);
        return new SpecialtyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SpecialtyViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class SpecialtyViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIcon, tvName;

        SpecialtyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIcon);
            tvName = itemView.findViewById(R.id.tvSpecialtyName);
        }

        void bind(Specialty specialty, OnSpecialtyClickListener listener) {
            tvIcon.setText(specialty.getIconEmoji());
            tvName.setText(specialty.getName());
            itemView.setOnClickListener(v -> listener.onSpecialtyClick(specialty));
        }
    }
}
