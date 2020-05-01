package com.example.covidappactivities.dashboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.covidappactivities.R;
import com.example.covidappactivities.bluetoothscan.MyForeGroundService;
import com.example.covidappactivities.dashboard.fragments.CoughFragment;
import com.example.covidappactivities.dashboard.fragments.HomeFragment;
import com.example.covidappactivities.dashboard.fragments.MapFragment;
import com.example.covidappactivities.location.LocationService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity{



    // Bottom Navigation Menu Related Stuff
    public BottomNavigationView bottomNavigation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);



        ////////////////////////FOREGROUND MONITORING SERVICE////////////
        {
            // Start Foreground Service
            Intent intent = new Intent(DashboardActivity.this, MyForeGroundService.class);
            intent.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
            startService(intent);

            // Start Foreground Service
            intent = new Intent(DashboardActivity.this, LocationService.class);
            startService(intent);
        }


        ////////////////////////BOTTOM NAVIGATION////////////////////////
        {
            bottomNavigation = findViewById(R.id.bottom_navigation);

            bottomNavigation = findViewById(R.id.bottom_navigation);
            bottomNavigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
            openFragment(HomeFragment.newInstance());
        }
    }

    public void openFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
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
