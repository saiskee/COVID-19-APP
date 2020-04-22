package com.example.covidappactivities.location;

import android.os.Build;

/**
 * @author Nayanesh Gupte
 */
public class AppUtils {

    /**
     * @return true If device has Android Marshmallow or above version
     */
    public static boolean hasM () {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static  final String ACTION = "LOCATION_ACTION";
    public static final String POSITION = "LOCATION_DATA";
    public static final String DISTANCE = "DISTANCE";

    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 30000;
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 30000;
    public static final int ACCURACY_THRESHOLD = 100;

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;


}