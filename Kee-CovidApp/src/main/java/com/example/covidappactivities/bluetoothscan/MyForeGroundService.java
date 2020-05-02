package com.example.covidappactivities.bluetoothscan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.example.covidappactivities.location.AppUtils;
import com.example.covidappactivities.map.MapsActivityCurrentPlace;
import com.example.covidappactivities.R;

public class MyForeGroundService extends Service {

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

    // Wifi
    private WifiManager mWifiManager;

    // Bluetooth LE
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothAdapter mBluetoothAdapter;
    HashMap<String, ScanResult> scanResults = new HashMap<String, ScanResult>();

    // stores contacts indexed by time, for suppressing contacts after they've been detected too much
    HashMap<String, Long> recentContactList = new HashMap<String, Long>();


    // contacts that have been observed in a certain period of time
    HashMap<String, Integer> contactsThisCycle = new HashMap<>();
    HashSet<String> seenPairs = new HashSet<>();

    // signals closer than this count as a close contact
    int CONTACT_THRESH = -60;

    // Location
    private static final int DIST_THRESH = 100; // meters

    boolean isRunning = false; // flag for whether the service is running or not

    //these settings control how contacts stop "counting" once they have been observed for a certain period of time.
    //This makes the score more interpretable because long lasting contacts are often things that just happen to be in the vicinity and don't represent "real" contacts
    //contact_list time is how long the system keeps track of contacts, and contact list max in the number of 30-second periods in which they must be
    //observed before they stop counting.

    //number of ms contacts on the list should be kept for
    long CONTACT_LIST_TIME = 1000 * 60 * (60 * 24);
    //start disregarding signals if they appear in more than this many scans
    int CONTACT_LIST_MAX = 10;

    private static Location currentLocation;

    //signals of any strength that have already been encountered in the current scan
    HashMap<String, Integer> signalsThisCycle = new HashMap<>();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_FOREGROUND_SERVICE, "foreground service onCreate().");

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("START_COMMAND", "Got start command");
        if (intent != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_FOREGROUND_SERVICE:
                    if (!isRunning) {
                        startForegroundService();
                        isRunning = true;
                    }
                    break;

                case ACTION_STOP_FOREGROUND_SERVICE:
                    isRunning = false;
                    stopForegroundService();
                    break;
            }
        }
        return START_REDELIVER_INTENT;
    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                List<android.net.wifi.ScanResult> mScanResults = mWifiManager.getScanResults();
                for (android.net.wifi.ScanResult result: mScanResults){
                    Log.d("WIFI_DEBUG", result.SSID + " " + result.BSSID);
                }
            }
        }
    };

    private final BroadcastReceiver mLocationScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AppUtils.ACTION)){
                Location location = intent.getParcelableExtra(AppUtils.POSITION);
                Location mostRecentLocation = location;
                float distance = intent.getFloatExtra(AppUtils.DISTANCE, 0);
                Log.d("FOREGROUNDSERVICE_LOCATION", location.getLatitude() + " " + location.getLongitude() + " " + distance);

                // If person has moved significantly
                if (distance > DIST_THRESH || currentLocation == null){
                    currentLocation = mostRecentLocation;
                }
            }
        }
    };

    /* Used to build and start foreground service. */
    private void startForegroundService() {
        { // Notification Code
            createNotificationChannel(); //creates the required channel on newer platforms

        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("serviceRunning", true);
        editor.commit();
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");

        // Create notification default intent.
        Intent intent = new Intent(this, MapsActivityCurrentPlace.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Create notification builder.
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "contactapp");

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Tracking exposure");
        bigTextStyle.bigText("nCovid-19 Dashboard is monitoring your exposure.");

        // Set big text style.
        builder.setStyle(bigTextStyle);
        builder.setContentTitle("nCovid-19 Dashboard is monitoring your exposure.");
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.servicenotif);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);
    }

        //start Bluetooth
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { //check to make sure the Bluetooth is working, if not try to enable it.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //Request Bluetooth on
            startActivity(enableBtIntent);
        }
        // Wait Time for User to turn on bluetooth
        int waitTime = 60;
        while (waitTime > 0 && (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())) { //wait one minute for user to turn on bluetooth, if it is not on by then abort.
            waitTime--;
            SystemClock.sleep(1000);
        }
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { //permission was not granted, abort the service
            isRunning = false;
            stopForegroundService();
        }

        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY) //sticky match mode should help filter out "stray" contacts with unusually high signal strength
                .build();
        ScanFilter.Builder filterBuilder;
        filters = new ArrayList<ScanFilter>();

        // Allow for all possible Manufacturers, workaround for screen off bluetooth scan
        for (int idx = 0; idx < 1000; idx++){
            filterBuilder = new ScanFilter.Builder();
            filterBuilder.setManufacturerData(idx, new byte[] {});

            filters.add(filterBuilder.build());
        }

        scanLeDevice(true);

        //tell the system not to go to sleep
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ContactApp::BluetoothScan");
        wakeLock.acquire();

        // Get nearby AP's Callback
//        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//        mWifiManager.startScan();
//        registerReceiver(mWifiScanReceiver,
//                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Get Location Callback
        registerReceiver(mLocationScanReceiver, new IntentFilter(AppUtils.ACTION));


        // compute the number of contacts every 30 seconds.
        // This compensates for things like differences in bluetooth advertising rate.
        final Handler handler = new Handler();
        final Runnable updateLoop = new Runnable() {
            @Override
            public void run() {
//                android.os.Debug.waitForDebugger();
                //count the number of addresses in the contact list
                int contactCount = 0;
                String addresses = "";
                Log.d("CONTACTS_TODAY_ADDRESSES", addresses);
                for (String address: contactsThisCycle.keySet()){

                    if (recentContactList.containsKey(address)) {
                        String a = "";
                    }
                    else {
                        contactCount += contactsThisCycle.get(address);
                        addresses += " " + address + " " + contactsThisCycle.get(address);
                    }
                    recentContactList.put(address, Long.valueOf(System.currentTimeMillis()));


                }
                Log.d("KEERTHAN_DEBUG", contactsThisCycle.keySet().toString());

                contactsThisCycle.clear(); //reset the counter
                signalsThisCycle = new HashMap<>(); //same for all signals

                SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
                SimpleDateFormat hourFormat = new SimpleDateFormat("H-dd-MMM-yyyy");
                String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
                String hourKey = hourFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
                //formats and keys for weekly and hourly graphs
                SimpleDateFormat weekdayFormat = new SimpleDateFormat("u-W-MMM-yyyy");
                String weekdayKey = "week-" + weekdayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
                SimpleDateFormat minuteFormat = new SimpleDateFormat("m-H-dd-MMM-yyyy");
                String minuteKey = "min-" + minuteFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());

                final SharedPreferences.Editor editor = getSharedPreferences("com", MODE_PRIVATE).edit();
                if (currentLocation != null){
                    double currentLatitude = Math.round(currentLocation.getLatitude() * Math.pow(10,5)) / Math.pow(10,5);
                    double currentLongitude = Math.round(currentLocation.getLongitude() * Math.pow(10,5))/ Math.pow(10,5);
                    String loc = "(" + currentLocation.getLatitude() + "," + currentLocation.getLongitude() + ")";

                    String todayLocationKey = "locToday-" + loc;
                    String hourLocationKey = "locHour-"+ loc;
                    String weekDayLocationKey = "locWeek-"+ loc;
                    String minuteLocationKey = "locMin-"+ loc;



                    //get the total number of contacts today, add one, and write it back
                    editor.putInt(todayLocationKey, getSharedPreferences("com", MODE_PRIVATE).getInt(todayLocationKey, 0) + contactCount);
                    //also update the contacts this hour
                    editor.putInt(hourLocationKey, getSharedPreferences("com", MODE_PRIVATE).getInt(hourLocationKey, 0) + contactCount);
                    //update contacts for this minute and contacts for this day
                    editor.putInt(weekDayLocationKey, getSharedPreferences("com", MODE_PRIVATE).getInt(weekDayLocationKey, 0) + contactCount);
                    editor.putInt(minuteLocationKey, getSharedPreferences("com", MODE_PRIVATE).getInt(minuteLocationKey, 0) + contactCount);

                }




                //get the total number of contacts today, add one, and write it back
                editor.putInt(todayKey, getSharedPreferences("com", MODE_PRIVATE).getInt(todayKey, 0) + contactCount);
                //also update the contacts this hour
                editor.putInt(hourKey, getSharedPreferences("com", MODE_PRIVATE).getInt(hourKey, 0) + contactCount);
                //update contacts for this minute and contacts for this day
                editor.putInt(minuteKey, getSharedPreferences("com", MODE_PRIVATE).getInt(minuteKey, 0) + contactCount);
                editor.putInt(weekdayKey, getSharedPreferences("com", MODE_PRIVATE).getInt(weekdayKey, 0) + contactCount);
                editor.apply();


                cleanContactList(); //clean out old contacts from the contact list once they expire

                handler.postDelayed(this, 10000);

            }

        };
        // start loop
        handler.post(updateLoop);


    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {

            //start BLE scanning process
            mLEScanner.startScan(filters, settings, mScanCallback);
            Log.e("scan", "Starting scan...");

        } else {
            mLEScanner.stopScan(mScanCallback);
        }
    }

    private String manToString(SparseArray<byte[]> sparseArray){
        final int size = sparseArray.size();
        if (size > 0) { return String.valueOf(sparseArray.keyAt(0));}
        return "";
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            String deviceAddress = result.getDevice().getAddress();
            String manufacturerDetails = manToString(result.getScanRecord().getManufacturerSpecificData());


            scanResults.put(deviceAddress, result);
            ScanData.getInstance().setData(scanResults);

            //if this device has not been seen this cycle, add it to the total contact list regardless of signal strength
            if (!signalsThisCycle.containsKey(deviceAddress)) {
                signalsThisCycle.put(deviceAddress, 1);
            }

            //check to see if this is a contact
            if (result.getRssi() >= CONTACT_THRESH) {

                // Check if it is ignored
                if (!getSharedPreferences("com", MODE_PRIVATE).getString("ignoreDevices", "").contains(deviceAddress)) {
                    // Check the ignore list, and also the number of times this contact has been observed in the contact list. If it's not in the ignore list and hasn't been observed too much, add it to the contact list
                    if (!contactsThisCycle.containsKey(deviceAddress)) {
                        contactsThisCycle.put(deviceAddress, 1);
                    }

                }

            }
        }

    };

    private void stopForegroundService() {
        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    private void cleanContactList() { //remove any entries in contact list that are too old as defined by the CONTACT_LIST_TIME variable
        for (Long i : recentContactList.values()) {
            if (i < System.currentTimeMillis() - CONTACT_LIST_TIME) {
                recentContactList.remove(i);
            }
        }
    }

    private boolean seenRecently(String address){
        return recentContactList.containsKey(address);
    }

    private int countContacts(String address) { //count the number of contacts from this device
        int hits = 0;
        for (String a: recentContactList.keySet()) {
            if (a.equals(address)) {
                hits++;
            }
        }
        return hits;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("contactapp", "ContactApp notification", importance);
            channel.setDescription("Contactapp persistent notification");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


}