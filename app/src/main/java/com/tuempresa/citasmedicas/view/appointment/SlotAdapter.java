package com.tuempresa.citasmedicas.view.appointment;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.model.TimeSlot;

import java.util.List;

/**
 * Adaptador vertical de horarios (slots) de un doctor.
 * Los slots no disponibles se muestran deshabilitados y tachados.
 */
public class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.SlotViewHolder> {

    public interface OnSlotSelectedListener {
        void onSlotSelected(TimeSlot slot);
    }

    private final List<TimeSlot> slots;
    private final OnSlotSelectedListener listener;
    private int selectedIndex = -1;

    public SlotAdapter(List<TimeSlot> slots, OnSlotSelectedListener listener) {
        this.slots = slots;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        final int pos = holder.getAdapterPosition();
        holder.bind(slots.get(position), pos == selectedIndex, () -> {
            int index = holder.getAdapterPosition();
            if (index == RecyclerView.NO_POSITION) {
                return;
            }
            TimeSlot slot = slots.get(index);
            if (!slot.isAvailable()) {
                return;
            }
            int previous = selectedIndex;
            selectedIndex = index;
            notifyItemChanged(previous);
            notifyItemChanged(selectedIndex);
            if (listener != null) {
                listener.onSlotSelected(slot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    public TimeSlot getSelectedSlot() {
        if (selectedIndex < 0 || selectedIndex >= slots.size()) {
            return null;
        }
        return slots.get(selectedIndex);
    }

    static class SlotViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTime;
        private final TextView tvAvailability;

        SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAvailability = itemView.findViewById(R.id.tvAvailability);
        }

        interface OnClickCallback {
            void onClick();
        }

        void bind(TimeSlot slot, boolean selected, OnClickCallback callback) {
            tvTime.setText(slot.getTime());

            boolean available = slot.isAvailable();
            itemView.setSelected(selected);
            itemView.setEnabled(available);
            itemView.setAlpha(available ? 1f : 0.45f);

            tvTime.setPaintFlags(available
                    ? tvTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG
                    : tvTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            if (!available) {
                tvAvailability.setText("No disponible");
                tvAvailability.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else if (selected) {
                tvAvailability.setText("Seleccionado");
                tvAvailability.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
            } else {
                tvAvailability.setText("Disponible");
                tvAvailability.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.status_confirmed));
            }

            itemView.setOnClickListener(v -> {
                if (available) {
                    callback.onClick();
                }
            });
        }
    }
}
