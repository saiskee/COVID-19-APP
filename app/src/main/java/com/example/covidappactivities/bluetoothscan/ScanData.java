package com.example.covidappactivities.bluetoothscan;

import android.bluetooth.le.ScanResult;

import java.util.HashMap;

//singlton class for sharing BT scan data between UI and scan thread
public class ScanData {
    HashMap<String, ScanResult> results = new HashMap<String, ScanResult>();

    public HashMap<String, ScanResult> getData() {
        return results;
    }

    public void setData(HashMap<String, ScanResult> data) {
        this.results = data;
    }

    private static final ScanData holder = new ScanData();

    public static ScanData getInstance() {
        return holder;
    }
}

