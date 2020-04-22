package com.example.covidappactivities.dashboard.fragments;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.covidappactivities.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private static Handler handler;
    private static Runnable updateChartLoop;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final WebView chartView = (WebView) this.getActivity().findViewById(R.id.chartView);
        chartView.setInitialScale(30);
        chartView.setBackgroundColor(Color.WHITE);
        chartView.getSettings().setLoadWithOverviewMode(true); //set scaling to automatically fit the image returned by server
        chartView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        chartView.setScrollbarFadingEnabled(false);
        chartView.getSettings().setUseWideViewPort(true);

        final SharedPreferences prefs = this.getActivity().getSharedPreferences("com", this.getActivity().MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();

        handler = new Handler();
        updateChartLoop = new Runnable() {
            @Override
            public void run() {
                Log.d("CHART_DEBUG", "Updating Chart Now");
                // first update the total number of contacts today
                SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
                String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());

//                contactsToday.setText("Today's exposure score: " + prefs.getInt(todayKey, 0));

//                if (isVisible) {
                chartView.loadUrl(generateChartString(prefs.getInt("chartMode", 2))); //update the chart
//                }

                /*
                //show the devices contirbuting--this is not visible by default because the textView that holds it is set to GONE but can be turned pn
                String dispResult = "";
                for (String i : ScanData.getInstance().getData().keySet()) {
                    ScanResult temp = ScanData.getInstance().getData().get(i);
                    if (temp.getRssi() > LIST_THRESH) {
                        dispResult = dispResult + temp.getDevice().getAddress() + " : " + temp.getDevice().getName() + " " + temp.getRssi() + "\n";
                    }
                }
                status.setText(dispResult);
                */
                handler.postDelayed(this, 10000);
            }

        };
// start
        handler.post(updateChartLoop);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
        //set up the exposure chart using quickchart.io to make the chart and Webview to display it


    }

    String generateChartString(int type) { //format the hourly exposure data into a format that can be sent to quickchart.. Type  1=hour, type 2=day, type 3 = week

        //chart fomratting data
        String BASE_REQUEST_HOUR = "https://quickchart.io/chart?c={type:%27line%27,%20options:%20{legend:%20{display:%20false}},data:{labels:[%2712%20AM%27,%271%20AM%27,%272%20AM%27,%273%20AM%27,%274%20AM%27,%275%20AM%27,%276%20AM%27,%277%20AM%27,%278%20AM%27,%279%20AM%27,%2710%20AM%27,%2711%20AM%27,%2712%20PM%27,%20%271%20PM%27,%272%20PM%27,%273%20PM%27,%274%20PM%27,%275%20PM%27,%276%20PM%27,%277%20PM%27,%278%20PM%27,%279%20PM%27,%2710%20PM%27,%2711%20PM%27],%20datasets:[{label:%27%27,%20data:%20[#CHARTDATA#],%20fill:false,borderColor:%27blue%27}]}}";
        String BASE_REQUEST_MINUTE = "https://quickchart.io/chart?c={type:%27line%27,%20options:%20{legend:%20{display:%20false}},data:{labels:[#LABELDATA#],%20datasets:[{label:%27%27,%20data:%20[#CHARTDATA#],%20fill:false,borderColor:%27blue%27}]}}";
        String BASE_REQUEST_DAILY = "https://quickchart.io/chart?c={type:'line', options: {legend: {display: false}},data:{labels:['Mon','Tues','Wed','Thurs','Fri','Sat','Sun'], datasets:[{label:'', data: [#CHARTDATA#], fill:false,borderColor:'blue'}]}}";

        //Strings for obtained data
        String hourlyData = "";
        String dailyData = "";
        String minuteData = "";

        //formats and keys
        SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
        String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
        SimpleDateFormat weekdayFormat = new SimpleDateFormat("-W-MMM-yyyy");
        String weekdayKey = weekdayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
        SimpleDateFormat minuteFormat = new SimpleDateFormat("-H-dd-MMM-yyyy");
        String minuteKey = minuteFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
        SimpleDateFormat hourFormat = new SimpleDateFormat("H");
        final SharedPreferences prefs = this.getActivity().getSharedPreferences("com", this.getActivity().MODE_PRIVATE);

        int thisHour = Integer.parseInt(hourFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime())); //todo: clearer way of getting current hour

        // /go through each hour of the day and append teh number of contacts to hourlydata.
        // We want to plot points for all the hours that have actually happened. Some might be missing data (if the app wasn't running), so show these as 0
        for (int time = 0; time <= thisHour; time++) {
            int contactNum = prefs.getInt(time + "-" + todayKey, -1);
            if (contactNum > -1) { //we actually have data for this slot
                hourlyData = hourlyData + contactNum + ",";
            } else {
                hourlyData = hourlyData + "0,";
            }
        }
        //now do the same thing for minute
        for (int min = 0; min <= 59; min++) {
            int contactNum = prefs.getInt("min-" + min + minuteKey, -1);


            if (contactNum > -1) { //we actually have data for this slot
                minuteData = minuteData + contactNum + ",";
            } else {
                minuteData = minuteData + "0,";
            }
        }

        //and for the day of the week
        for (int day = 1; day <= 7; day++) {
            int contactNum = prefs.getInt("week-" + day + weekdayKey, -1);
            if (contactNum > -1) { //we actually have data for this slot
                dailyData = dailyData + contactNum + ",";
            } else {
                dailyData = dailyData + "0,";
            }
        }

        //return the URL for the request chart. 1=current hour, 2=current day, 3=current week
        if (type == 1) {
            String minLabel = "";
            for (int i = 0; i < 60; i++) { //create labels from 1 to 60 minutes
                minLabel = minLabel + i + ",";
            }
            BASE_REQUEST_MINUTE = BASE_REQUEST_MINUTE.replace("#LABELDATA#", minLabel).replace("#CHARTDATA#", minuteData);
            return BASE_REQUEST_MINUTE;
        } else if (type == 2) {
            BASE_REQUEST_HOUR = BASE_REQUEST_HOUR.replace("#CHARTDATA#", hourlyData); //plug the data into the URL
            return BASE_REQUEST_HOUR;
        } else {
            BASE_REQUEST_DAILY = BASE_REQUEST_DAILY.replace("#CHARTDATA#", dailyData); //plug the data into the URL
            return BASE_REQUEST_DAILY;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(updateChartLoop);

    }
}