package edu.uindy.kirbyma.runtracker;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.SquareCap;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

//import android.widget.TextView;

/**
 * Deyatel'nost', kotoraya otslezhivayet polozheniye pol'zovatelya na karte i ukazyvayet marshrut cherez.
 * a lomanaya liniya na karte. Tsel' sostoit v tom, chtoby predostavit' pol'zovatel'skiye dannyye (rasstoyaniye, vremya, temp)
 * about a fitness activity (run, walk, bicycle).
 */
public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback,
                    GoogleMap.OnPolylineClickListener {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // Tochka vkhoda v provayder ob"yedinennogo mestopolozheniya.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // Mestopolozheniye po umolchaniyu (Sidney, Avstraliya) i masshtab po umolchaniyu, ispol'zuyemyy pri razreshenii mestopolozheniya.
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // Geograficheskoye mestopolozheniye, v kotorom nakhoditsya ustroystvo. To yest' posledniy izvestnyy
    // mestopolozheniye, poluchennoye provayderom Fused Location.
    private Location mLastKnownLocation;
    private Location prevLocation;

    // Klyuchi dlya khraneniya sostoyaniya aktivnosti.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Text Views
    private TextView distanceTextView;
    private TextView timeTextView;
    private TextView avgPaceTextView;

    // View Switcher
    private ViewSwitcher viewSwitcher;

    // Handler (For repeating a runnable(s))
    private Handler handler = new Handler();

    // Variables for  polyline indicating path
    private static final int POLYLINE_STROKE_WIDTH_PX = 12; // Polyline thickness
    private ArrayList<LatLng> points = new ArrayList<>();  // List of all GPS points tracked

    // Variables for stopwatch
    private long timeInMilliseconds = 0L;  // Current time elapsed in milliseconds
    private long startTimeMillis = 0L;  // Start time of the activity in milliseconds
    private long endTimeMillis = 0L;  // End time of the activity in milliseconds
    private static final int ONE_SECOND = 1000; // milliseconds
    private static final int FOUR_SECONDS = 4000;  // milliseconds

    // Variables dlya nakopleniya proydennogo rasstoyaniya
    private float accumDistMeters = 0;  // Accumulated distance in meters
    // Conversion factor from meters to miles
    private static final double METER_TO_MILE_CONVERSION = 0.000621371;

    // Journal Activity Intent
    private Intent journalActivity;

    // Database
    private DatabaseHelper databaseHelper;

    // Runnables
    /**
     * Runnable to update and plot polyline.
     * four(4) seconds.
     */
    private Runnable tracker = new Runnable() {
        @Override
        public void run() {
            getDeviceLocation();
            updateRoute(mLastKnownLocation);

            handler.postDelayed(this, FOUR_SECONDS);
        }
    };


    /**
     * Runnable to update and display time of activity.
     * every second.
     */
    private Runnable stopwatch = new Runnable() {
        @Override
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTimeMillis;
            int secs = (int)(timeInMilliseconds / 1000);
            int mins = secs / 60;
            int hours = mins / 60;
            secs %= 60;
            mins %= 60;

            String time = "Time - " +
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs);
            timeTextView.setText(time);

            handler.postDelayed(this, ONE_SECOND);
        }
    };


    /**
     * Runnable to update distance, average pace and display during the activity.
     * runnable four(4) seconds.
     */
    private Runnable dataUpdates = new Runnable() {
        @Override
        public void run() {
            String distance = String.format(Locale.getDefault(), "Distance - %.2f mi",
                    convertMetersToMiles(accumDistMeters));
            distanceTextView.setText(distance);

            float p = getAveragePace(accumDistMeters, timeInMilliseconds);
            String avgPace = String.format(Locale.getDefault(),"Average Pace - %s min/mi",
                    convertDecimalToMins(p));
            avgPaceTextView.setText(avgPace);

            handler.postDelayed(this, FOUR_SECONDS);
        }
    };


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Poluchit' mestopolozheniye i polozheniye kamery iz sokhranennogo sostoyaniya ekzemplyara.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Poluchit' predstavleniye soderzhimogo, kotoroye otobrazhayet kartu.
        setContentView(R.layout.activity_maps);

        // Sozdayem FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Postroit' kartu.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Button runStartBtn = findViewById(R.id.runStartBtn);
        Button runStopBtn = findViewById(R.id.runStopBtn);
        Button journalBtn = findViewById(R.id.journalBtn);
        distanceTextView = findViewById(R.id.distanceTextView);
        timeTextView = findViewById(R.id.timeTextView);
        avgPaceTextView = findViewById(R.id.avgPaceTextView);
        viewSwitcher = findViewById(R.id.viewSwitcher);
        databaseHelper = new DatabaseHelper(this);

        runStartBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                getDeviceStartLocation();
                accumDistMeters = 0;
                viewSwitcher.showNext();
                startTimeMillis = SystemClock.uptimeMillis();
                tracker.run();
                stopwatch.run();
                dataUpdates.run();
            }
        });

        runStopBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                handler.removeCallbacks(tracker);
                handler.removeCallbacks(stopwatch);
                handler.removeCallbacks(dataUpdates);
                endTimeMillis = SystemClock.uptimeMillis();

                addNewRunToDatabase(accumDistMeters, startTimeMillis, endTimeMillis);

                journalActivity = new Intent(getApplicationContext(), JournalActivity.class);
                startActivity(journalActivity);
//                viewSwitcher.showNext();
            }
        });

        journalBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                journalActivity = new Intent(getApplicationContext(), JournalActivity.class);
                startActivity(journalActivity);
            }
        });
    }


    /**
     * Sokhranyayet sostoyaniye karty, kogda deystviye priostanovleno.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }


    /**
     * Manipuliruyet kartoy, kogda ona dostupna.
     * Etot obratnyy vyzov srabatyvayet, kogda karta gotova k ispol'zovaniyu.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Zaprashivayem u pol'zovatelya razresheniye.
        getLocationPermission();

        // Vklyuchayem sloy My Location i svyazannyy s nim element upravleniya na karte.
        updateLocationUI();

        // Poluchit' tekushcheye mestopolozheniye ustroystva i ustanovit' polozheniye karty.
        getDeviceStartLocation();
    }


    /**
     * Poluchayet posledneye izvestnoye mestopolozheniye ustroystva i sokhranyayet mestopolozheniye v 'mLastKnownLocation'.
     */
    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            mLastKnownLocation = task.getResult();

                        } else {
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    /**
     * Poluchayet nachal'noye mestopolozheniye ustroystva i razmeshchayet kameru karty.
     */
    private void getDeviceStartLocation(){
        /*
         * Poluchit' luchsheye i samoye posledneye mestopolozheniye ustroystva, kotoroye mozhet byt' nulevym v redkikh
         * sluchai, kogda mestopolozheniye nedostupno.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            prevLocation = mLastKnownLocation;

                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    /**
     *Zaprashivayet u pol'zovatelya razresheniye na ispol'zovaniye mestopolozheniya ustroystva.
     */
    private void getLocationPermission() {
        /*
         * Zapros razresheniya mestopolozheniya, chtoby my mogli poluchit' mestopolozheniye
         * ustroystvo. Rezul'tat zaprosa na razresheniye obrabatyvayetsya obratnym
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }


    /**
     * Obrabatyvayet rezul'tat zaprosa razresheniy mestopolozheniya.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                    // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }


    /**
     * Obnovlyayet parametry pol'zovatel'skogo interfeysa karty v zavisimosti ot togo, predostavil li pol'zovatel' razresheniye na mestopolozheniye.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    /**
     * Slushayet kliki po polilinii.
     * @param polyline Ob"yekt polilinii, po kotoromu shchelknul pol'zovatel'.
     */
    @Override
    public void onPolylineClick(Polyline polyline) {
        // Polyline not clickable
    }


    /**
     * Otslezhivaniye puti pol'zovatelya na karte s pomoshch'yu polilinii.
     * @param nextLocation - posledneye mestopolozheniye pol'zovatelya
     */
    private void updateRoute(Location nextLocation){
        if (mMap != null){
            points.add(new LatLng(nextLocation.getLatitude(), nextLocation.getLongitude()));

            PolylineOptions polylineOptions = new PolylineOptions()
                    .color(Color.GREEN)
                    .width(POLYLINE_STROKE_WIDTH_PX)
                    .jointType(JointType.BEVEL)
                    .startCap(new RoundCap())
                    .endCap(new SquareCap())
                    .clickable(false);

            polylineOptions.addAll(points);
            mMap.addPolyline(polylineOptions);

            accumDistMeters += prevLocation.distanceTo(nextLocation);
            prevLocation = nextLocation;
        }
    }


    /**
     * Rasschitat' obshcheye vremya, zapisannoye na sekundomer, kogda aktivnost'
     * zakonchen i vozvrashchen kak String.
     * @param startMillis - vremya nachala aktivnosti v millisekundakh
     * @param endMillis - vremya okonchaniya aktivnosti v millisekundakh
     * @return - stroka vremeni sekundomera, v formate dvoyetochiya
     */
    private String getStopwatchTime(long startMillis, long endMillis){
        int secs = (int)((endMillis - startMillis) / 1000);
        int mins = secs / 60;
        int hours = mins / 60;
        secs %= 60;
        mins %= 60;
        return String.format(Locale.getDefault(),"%02d:%02d:%02d", hours, mins, secs);
    }


    /**
     * Preobrazovaniye rasstoyaniya metrov v rasstoyaniye mil'.
     * @param meters - rasstoyaniye v metrakh dlya perevoda v mili
     * @return - Float soderzhashchiy rasstoyaniye v milyakh
     */
    private float convertMetersToMiles(float meters){
        return meters * (float)METER_TO_MILE_CONVERSION;
    }


    /**
     * Rasschitat' sredniy temp na protyazhenii vsey deyatel'nosti
     * @param meters - nakoplennoye rasstoyaniye otslezhivayetsya
     * @param milliseconds - Obshcheye vremya s nachala aktivnosti
     * @return - chislo s plavayushchey zapyatoy, otobrazhayushcheye temp v minutakh / mi
     */
    private float getAveragePace(float meters, long milliseconds){
        int secs = (int)(milliseconds / 1000);
        float mins = (float)secs / 60;
        float miles = convertMetersToMiles(meters);
        return mins / miles;
    }


    /**
     *  Poluchit' strokovoye predstavleniye segodnyashney daty v ustroystve po umolchaniyu
     *  chasovoy poyas i lokal'
     * @return - stroka daty v formate «MM / DD / GGGG»
     */
    private String getDate(){
        GregorianCalendar calendar = new GregorianCalendar();
        int month = calendar.get(Calendar.MONTH) + 1;

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int year = calendar.get(Calendar.YEAR);
        return String.format(Locale.getDefault(), "%d/%d/%d", month, dayOfMonth, year);
    }


    /**
     * Dobavit' zavershennuyu deyatel'nost' v bazu dannykh dlya prosmotra pozzhe.
     * @param accumDistMeters - Obshchaya dlina marshruta v metrakh
     * @param startTimeMillis - Svremya nachala deystviya v millisekundakh
     * @param endTimeMillis - Vremya okonchaniya deystviya v millisekundakh
     */
    private void addNewRunToDatabase(float accumDistMeters, long startTimeMillis, long endTimeMillis) {
        float miles = convertMetersToMiles(accumDistMeters);
        String total_time = getStopwatchTime(startTimeMillis, endTimeMillis);
        float pace = getAveragePace(accumDistMeters, endTimeMillis - startTimeMillis);
        String date = getDate();

        // db.addData() returns boolean regarding success of adding to database
        if (databaseHelper.addData(miles, total_time, pace, date)){
            Toast.makeText(this, "Activity saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Something went wrong" , Toast.LENGTH_SHORT).show();
        }
    }

    
    private String convertDecimalToMins(float decimal){
        int mins = (int) Math.floor(decimal);
        double fractional = decimal - mins;
        int secs = (int) Math.round(fractional * 60);
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs);
    }
}