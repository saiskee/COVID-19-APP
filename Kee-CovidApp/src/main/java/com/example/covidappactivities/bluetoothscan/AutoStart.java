package com.example.covidappactivities.bluetoothscan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AutoStart extends BroadcastReceiver {
    /**
     * Attempt to boot ForeGroundService on phone boot
     * @param context Context for application
     * @param intent Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i("trackingService", "attempting to start on boot");
        Intent startserv = new Intent(context, ForegroundMonitoringService.class);
        startserv.setAction(ForegroundMonitoringService.ACTION_START_FOREGROUND_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startserv);
        } else {
            context.startService(startserv);
        }

    }
}
