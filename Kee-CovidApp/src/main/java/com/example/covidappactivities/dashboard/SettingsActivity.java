package com.example.covidappactivities.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.widget.CheckBox;
import com.example.covidappactivities.R;

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

        PackageManager pm = getPackageManager();
        boolean locationPerm = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;
        boolean micPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, getApplicationContext().getPackageName()) == PackageManager.PERMISSION_GRANTED;


        micCheckbox = (CheckBox) findViewById(R.id.microphoneAccessCheckBox);
        locationCheckbox = (CheckBox) findViewById(R.id.locationServicesCheckbox);

        if (locationPerm) {
            locationCheckbox.setChecked(true);
            locationCheckbox.setEnabled(false);
        }
        if (micPerm){
            micCheckbox.setEnabled(true);
            micCheckbox.setEnabled(false);
        }
        locationCheckbox.setOnClickListener(checkboxListener(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_REQUEST_LOCATION, "Request Microphone Access Permission"));
        micCheckbox.setOnClickListener(checkboxListener(Manifest.permission.RECORD_AUDIO, PERMISSION_REQUEST_MICROPHONE, "Request Microphone Access Permission"));

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
                locationCheckbox.setEnabled(false);

            } else {
                locationCheckbox.setChecked(false);
            }
        }
        else if (requestCode == PERMISSION_REQUEST_MICROPHONE){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                microphoneAccessGranted = true;
                micCheckbox.setEnabled(false);
            }
            else {
                micCheckbox.setChecked(false);
            }
        }
        if (locationAccessGranted && microphoneAccessGranted){
            finish();
        }

    }
}
