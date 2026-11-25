package com.tuempresa.citasmedicas.view.main;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tuempresa.citasmedicas.R;
import com.tuempresa.citasmedicas.navigation.Navigator;
import com.tuempresa.citasmedicas.navigation.Routes;
import com.tuempresa.citasmedicas.util.SessionManager;

/**
 * Actividad contenedora principal (Doctores, Mis Citas, Perfil).
 */
public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.getInstance(this).isLoggedIn()) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(this::onNavItemSelected);

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_doctors);
        }
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Fragment fragment = Navigator.fragmentForTab(id);
        if (fragment == null) {
            return false;
        }
        toolbar.setTitle(Navigator.titleForTab(id));
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        return true;
    }

    public void goToLogin() {
        Navigator.navigateClearingStack(this, new Routes.Login());
    }
}
