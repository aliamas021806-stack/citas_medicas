package com.tuempresa.citasmedicas;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import com.tuempresa.citasmedicas.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ResponsivePhoneResourcesTest {

    @Test
    public void phoneUsesSingleDoctorColumn() {
        int spanCount = ApplicationProvider.getApplicationContext()
                .getResources().getInteger(R.integer.doctors_span_count);

        assertEquals(1, spanCount);
    }
}
