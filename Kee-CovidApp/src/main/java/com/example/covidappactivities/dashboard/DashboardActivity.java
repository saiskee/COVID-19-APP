package com.example.covidappactivities.dashboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.covidappactivities.R;
import com.example.covidappactivities.bluetoothscan.ForegroundMonitoringService;
import com.example.covidappactivities.dashboard.fragments.CoughFragment;
import com.example.covidappactivities.dashboard.fragments.HomeFragment;
import com.example.covidappactivities.dashboard.fragments.MapFragment;
import com.example.covidappactivities.location.LocationService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity{



    // Bottom Navigation Menu Related Stuff
    public BottomNavigationView bottomNavigation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);

        PackageManager pm = getPackageManager();
        boolean locationPerm = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;
        boolean micPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;
        if ( !locationPerm || !micPerm ){
            launchSettingsActivity();
        }


        ////////////////////////BOTTOM NAVIGATION////////////////////////
        {
            bottomNavigation = findViewById(R.id.bottom_navigation);

            bottomNavigation = findViewById(R.id.bottom_navigation);
            bottomNavigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
            openFragment(HomeFragment.newInstance());
        }



    }

    // Launches the settings menu
    public void launchSettingsActivity(){
        Intent startSettings = new Intent(DashboardActivity.this, SettingsActivity.class);
        DashboardActivity.this.startActivity(startSettings);
    }

    // Opens a "Fragment" (Contacts, Map, Sound Detection, etc.)
    public void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    // create an action bar button
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // R.menu.mymenu is a reference to an xml file named mymenu.xml which should be inside your res/menu directory.
        // If you don't have res/menu, just create a directory named "menu" inside res
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // handle button activities
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
            launchSettingsActivity();
        return super.onOptionsItemSelected(item);
    }




    private BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.navigation_home:
                            openFragment(HomeFragment.newInstance());
                            return true;
                        case R.id.navigation_map:
                            openFragment(MapFragment.newInstance());
                            return true;
                        case R.id.navigation_cough:
                            openFragment(CoughFragment.newInstance());
                            return true;
                    }
                    return false;
                }
            };
}
