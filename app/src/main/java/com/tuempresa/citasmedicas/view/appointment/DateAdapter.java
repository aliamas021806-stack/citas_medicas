package com.tuempresa.citasmedicas.view.appointment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.data.mock.MockData;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador horizontal de fechas seleccionables. Formato interno yyyy-MM-dd.
 * Muestra el nombre del dia en espanol y el numero del dia.
 * El estado seleccionado se refleja con {@link View#setSelected(boolean)},
 * controlado por el selector {@code res/drawable/bg_day.xml}.
 */
public class DateAdapter extends RecyclerView.Adapter<DateAdapter.DateViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(String date);
    }

    private final List<String> dates;
    private final OnDateSelectedListener listener;
    private int selectedIndex = 0;

    private static final String[] DAY_NAMES = {
            "Dom", "Lun", "Mar", "Mie", "Jue", "Vie", "Sab"
    };

    public DateAdapter(List<String> dates, OnDateSelectedListener listener) {
        this.dates = dates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        final int pos = holder.getAdapterPosition();
        holder.bind(dates.get(position), pos == selectedIndex, () -> {
            int previous = selectedIndex;
            selectedIndex = holder.getAdapterPosition();
            if (selectedIndex == RecyclerView.NO_POSITION) {
                return;
            }
            notifyItemChanged(previous);
            notifyItemChanged(selectedIndex);
            if (listener != null) {
                listener.onDateSelected(dates.get(selectedIndex));
            }
        });
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    public String getSelectedDate() {
        if (dates.isEmpty()) {
            return null;
        }
        return dates.get(selectedIndex);
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDayName;
        private final TextView tvDayNumber;

        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tvDayName);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
        }

        interface OnClickCallback {
            void onClick();
        }

        void bind(String date, boolean selected, OnClickCallback callback) {
            tvDayName.setText(dayName(date));
            tvDayNumber.setText(dayNumber(date));
            itemView.setSelected(selected);
            itemView.setOnClickListener(v -> callback.onClick());
        }

        private String dayName(String date) {
            Date d = parse(date);
            if (d == null) {
                return "";
            }
            Calendar c = Calendar.getInstance(Locale.getDefault());
            c.setTime(d);
            return DAY_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1];
        }

        private String dayNumber(String date) {
            Date d = parse(date);
            if (d == null) {
                return "";
            }
            Calendar c = Calendar.getInstance(Locale.getDefault());
            c.setTime(d);
            return String.valueOf(c.get(Calendar.DAY_OF_MONTH));
        }

        private Date parse(String date) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(MockData.DATE_FORMAT.toPattern(), Locale.getDefault());
                return sdf.parse(date);
            } catch (ParseException e) {
                return null;
            }
        }
    }
}
