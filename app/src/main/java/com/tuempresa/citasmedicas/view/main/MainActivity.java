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

    /** Clave con la que se recuerda la pestaña activa al rotar o recrear. */
    private static final String STATE_SELECTED_TAB = "state_selected_tab";

    private MaterialToolbar toolbar;
    /** Pestaña activa. Fuente de verdad frente al estado interno del BottomNav. */
    private int selectedTab = Routes.Tab.DOCTORS;
    /** Evita volver a montar el fragmento cuando sólo se está restaurando. */
    private boolean restoringState = false;

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
            selectedTab = Routes.Tab.DOCTORS;
            bottomNav.setSelectedItemId(selectedTab);
        } else {
            // Al rotar, el FragmentManager ya restauró el fragmento, pero NO el
            // título de la toolbar. Y no sirve leer bottomNav.getSelectedItemId():
            // el BottomNavigationView restaura su propio estado MÁS TARDE que
            // onCreate, así que aquí todavía devolvería la primera pestaña.
            // Por eso la pestaña se guarda y se restaura explícitamente.
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, Routes.Tab.DOCTORS);
            toolbar.setTitle(Navigator.titleForTab(selectedTab));

            restoringState = true;
            bottomNav.setSelectedItemId(selectedTab);
            restoringState = false;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        selectedTab = item.getItemId();
        toolbar.setTitle(Navigator.titleForTab(selectedTab));

        if (restoringState) {
            // El fragmento correcto ya está montado por el FragmentManager.
            return true;
        }

        Fragment fragment = Navigator.fragmentForTab(selectedTab);
        if (fragment == null) {
            return false;
        }
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
