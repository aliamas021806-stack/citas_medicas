package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "sw600dp")
public class ResponsiveTabletResourcesTest {

    @Test
    public void tabletUsesTwoDoctorColumns() {
        int spanCount = ApplicationProvider.getApplicationContext()
                .getResources().getInteger(R.integer.doctors_span_count);

        assertEquals(2, spanCount);
    }

    @Test
    public void tabletDoctorCardKeepsAdapterViewIds() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_CitasMedicas);
        View doctorCard = LayoutInflater.from(themedContext)
                .inflate(R.layout.item_doctor, null, false);

        assertNotNull(doctorCard.findViewById(R.id.doctorCard));
        assertNotNull(doctorCard.findViewById(R.id.tvAvatar));
        assertNotNull(doctorCard.findViewById(R.id.tvName));
        assertNotNull(doctorCard.findViewById(R.id.tvSpecialty));
        assertNotNull(doctorCard.findViewById(R.id.tvRating));
        assertNotNull(doctorCard.findViewById(R.id.tvAge));
        assertNotNull(doctorCard.findViewById(R.id.tvHospital));
        assertNotNull(doctorCard.findViewById(R.id.tvFee));
    }
}
