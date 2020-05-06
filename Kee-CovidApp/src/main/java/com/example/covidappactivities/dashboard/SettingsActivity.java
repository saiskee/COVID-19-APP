package com.example.covidappactivities.dashboard;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.example.covidappactivities.R;
import com.example.covidappactivities.bluetoothscan.ForegroundMonitoringService;
import com.example.covidappactivities.location.LocationService;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_LOCATION = 50;
    private static final int PERMISSION_REQUEST_MICROPHONE = 51;

    private static CheckBox locationCheckbox;
    private static CheckBox micCheckbox;

    private static boolean locationAccessGranted;
    private static boolean microphoneAccessGranted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);


        PackageManager pm = getPackageManager();
        locationAccessGranted = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;
        microphoneAccessGranted = pm.checkPermission(Manifest.permission.RECORD_AUDIO, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;


        micCheckbox = (CheckBox) findViewById(R.id.microphoneAccessCheckBox);
        locationCheckbox = (CheckBox) findViewById(R.id.locationServicesCheckbox);

        if (locationAccessGranted) {
            locationCheckbox.setChecked(true);
            locationCheckbox.setEnabled(false);
        }
        if (microphoneAccessGranted){
            micCheckbox.setChecked(true);
            micCheckbox.setEnabled(false);
        }
        locationCheckbox.setOnClickListener(checkboxListener(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_REQUEST_LOCATION, "Request Microphone Access Permission"));
        micCheckbox.setOnClickListener(checkboxListener(Manifest.permission.RECORD_AUDIO, PERMISSION_REQUEST_MICROPHONE, "Request Microphone Access Permission"));

        // Finish settings activity when dashboard button is pressed (return to dashboard)
        Button dashboardButton = findViewById(R.id.dashboardButton);
        dashboardButton.setOnClickListener(v -> {
            finish();
        });

        Switch foregroundSwitch = findViewById(R.id.foregroundSwitch);


        if (locationAccessGranted && microphoneAccessGranted){
            LinearLayout foregroundServiceLayout = findViewById(R.id.monitoringLayout);
            foregroundServiceLayout.setVisibility(View.VISIBLE);
        }

        // If BackgroundMonitoringService is currently running, switch to true
        if (prefs.getBoolean("serviceRunning", false)){
            foregroundSwitch.setChecked(true);
        }

        // Add listener to background service switch
        foregroundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                final SharedPreferences.Editor editor = prefs.edit();
                if (isChecked){
                    ////////////////////////FOREGROUND MONITORING SERVICE////////////

                    // Start Foreground Service
                    Intent intent = new Intent(SettingsActivity.this, ForegroundMonitoringService.class);
                    intent.setAction(ForegroundMonitoringService.ACTION_START_FOREGROUND_SERVICE);
                    startService(intent);

                    Toast.makeText(getApplicationContext(), "Starting Background Covid Monitoring Service", Toast.LENGTH_LONG);

                    // Start Location Service
                    intent = new Intent(SettingsActivity.this, LocationService.class);
                    intent.setAction(LocationService.ACTION_START_LOCATION_SERVICE);
                    startService(intent);
                }else{
                    Intent intent = new Intent(SettingsActivity.this, ForegroundMonitoringService.class);
                    intent.setAction(ForegroundMonitoringService.ACTION_STOP_FOREGROUND_SERVICE);
                    startService(intent);

                    intent = new Intent(SettingsActivity.this, LocationService.class);
                    intent.setAction(LocationService.ACTION_STOP_LOCATION_SERVICE);
                    startService(intent);
                    editor.putBoolean("serviceRunning", false);
                }
            }
        });

    }

    View.OnClickListener checkboxListener(String permission, int permissionCode, String message){
        return new View.OnClickListener(){
          @Override
          public void onClick(View v){
              CheckBox checkBox = (CheckBox) v;
              boolean checked = checkBox.isChecked();
              if (checked){
                  Log.d("PERMISSION_REQUEST", message);
                  ActivityCompat.requestPermissions(SettingsActivity.this, new String[] {permission}, permissionCode);
              }
          }
        };
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationAccessGranted = true;
                locationCheckbox.setChecked(true);
                locationCheckbox.setEnabled(false);

            } else {
                locationCheckbox.setChecked(false);
            }
        }
        else if (requestCode == PERMISSION_REQUEST_MICROPHONE){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                microphoneAccessGranted = true;
                micCheckbox.setChecked(true);
                micCheckbox.setEnabled(false);
            }
            else {
                micCheckbox.setChecked(false);
            }
        }
        if (locationAccessGranted && microphoneAccessGranted){
            LinearLayout foregroundServiceLayout = findViewById(R.id.monitoringLayout);
            foregroundServiceLayout.setVisibility(View.VISIBLE);
        }

    }
}
