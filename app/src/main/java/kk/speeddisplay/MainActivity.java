package kk.speeddisplay;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.util.Locale;

/**
 * MainActivity
 * <p>
 */
public class MainActivity extends AppCompatActivity {

    /* get a tag for output debugging */
    private final static String TAG = MainActivity.class.getSimpleName();

    /* displays current speed from the location provider */
    private TextView tvCurrentSpeed;

    /* displays maximum speed recorded to date, max speed is saved in the shared preferences */
    private TextView tvMaxSpeed;

    /* update intervals at which the activity will receive location updates,
     * these are saved in the shared preferences
     */
    private float maxSpeed;
    private long activityActiveUpdateRate;
    private long activityNotActiveUpdateRate;

    /* default values in milliseconds for the update intervals at which the activity will receive location updates */
    private final static long ACTIVITY_ACTIVE_UPDATE_RATE_DEFAULT = 1000L;
    private final static long ACTIVITY_NOT_ACTIVE_UPDATE_RATE_DEFAULT = 30000L;

    private SharedPreferences mSharedPref;
    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvCurrentSpeed = findViewById(R.id.tv_CurrentSpeed);
        tvMaxSpeed = findViewById(R.id.tv_MaxSpeed);

        /* set up fused location client, which is API from Google Play Services  */
        mFusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        /* use the location request to set up the parameters for the fused location provider */
        mLocationRequest = new LocationRequest();

        mLocationCallback = new LocationCallback() {
            /* LocationResult a data class representing a geographic location result from the fused location provider */
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    displaySpeed(location.getSpeed());
                    checkMaxSpeed(location.getSpeed());
                }
            }
        };
        /* read settings form shared preferences and update location provider and screen */
        setupSharedPreferences();
        checkPermissions();
    }

    /**
     * Checks have permission to access location resources.
     * <p>
     * If permission granted, calls the method {@link #getLocation}. which requests
     * updates updates from the fused location provider.
     * If permission denied puts out a message and then exits.
     */

    private void checkPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET
                }, 10);
            }
            getLocation();
        } else {
            getLocation();
        }
    }

    /**
     * Returns an Image object that can then be painted on the screen.
     * <p>
     * This method always returns immediately, whether or not the
     * image exists. When this applet attempts to draw the image on
     */
    private void setupSharedPreferences() {
        mSharedPref = getPreferences(Context.MODE_PRIVATE);
        /* Get all of the values from shared preferences to set it up */
        setupSharedPrefMaxSpeed();
        setupSharedPrefAppActive();
        setupSharedPrefAppNotActive();
    }

    /**
     * Retrieves saved maximum speed detected to date from Shared preferences
     * and then displays this speed
     */
    private void setupSharedPrefMaxSpeed() {
        maxSpeed = mSharedPref.getFloat(getString(R.string.pref_saved_max_speed_key), 0.0F);
        Log.d(TAG, "SharedPref MaxSpeed: " + Float.toString(maxSpeed));
        /* display max speed */
        tvMaxSpeed.setText(String.format(Locale.UK, getString(R.string.units_and_number_of_decimals), maxSpeed));
    }

    /**
     * Retrieves update rate from Shared preferences for when activity is active
     */
    private void setupSharedPrefAppActive() {
        activityActiveUpdateRate = mSharedPref.getLong(getString(R.string.pref_activity_active_update_rate_key), ACTIVITY_ACTIVE_UPDATE_RATE_DEFAULT);
        Log.d(TAG, "SharedPref App active update rate: " + Float.toString(activityActiveUpdateRate));
        setLocationUpdateRate(activityActiveUpdateRate);
    }

    /**
     * Retrieves update rate from Shared preferences for when aactivity is not active
     */
    private void setupSharedPrefAppNotActive() {
        activityNotActiveUpdateRate = mSharedPref.getLong(getString(R.string.pref_activity_not_active_update_rate_key), ACTIVITY_NOT_ACTIVE_UPDATE_RATE_DEFAULT);
        Log.d(TAG, "SharedPref App not active update rate: " + Float.toString(activityNotActiveUpdateRate));
        setLocationUpdateRate(activityNotActiveUpdateRate);
    }

    /**
     * Updates the rate at which the location provider provides updates on location
     * always sets the accurcay to high
     *
     * @param updateRate rate at which location provider provides updates
     */
    private void setLocationUpdateRate(long updateRate) {
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(updateRate);
        mLocationRequest.setFastestInterval(updateRate);
    }


    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    /**
     * Sets the location update intervals to the activity active values
     */
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        setLocationUpdateRate(activityActiveUpdateRate);
    }

    /**
     * Sets the location update intervals to the activity not active values
     */
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        setLocationUpdateRate(activityNotActiveUpdateRate);
    }

    /**
     * Requests location updates from the fused location provider, which invokes LocationCallback
     * <p>
     * If fails exit application with a permission denied error message to the user
     */
    private void getLocation() {
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
            Toast.makeText(this, getResources().getString(R.string.error)
                    + ": " + getResources().getString(R.string.permission_denied)
                    + " " + getString(R.string.exiting), Toast.LENGTH_LONG).show();
            finish(); // terminate the program
        }
    }

    /**
     * Displays the speed, but first converts from meters per second to kilometers per hour
     *
     * @param newSpeed speed to be displayed in metres per second
     */
    private void displaySpeed(float newSpeed) {
        /* convert from m/sec to km/hour */
        newSpeed = newSpeed * 3600f / 1000f;
        /* display speed in km/hour */
        tvCurrentSpeed.setText(String.format(Locale.UK, getString(R.string.units_and_number_of_decimals), newSpeed));
    }

    /**
     * Checks if speed above previously stored maximum speed
     * if so save the new maximum speed and display the maximum speed
     *
     * @param speed latest speed from the location provider
     */
    private void checkMaxSpeed(float speed) {
        if (speed > maxSpeed) {
            /* we have a new maximum speed */
            maxSpeed = speed;
            /* save maximum speed to shared preferences */
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear().putFloat(getResources().getString(R.string.pref_saved_max_speed_key), maxSpeed).apply();
            /* display new maximum speed */
            tvMaxSpeed.setText(String.format(Locale.UK, getResources().getString(R.string.units_and_number_of_decimals), maxSpeed));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    getLocation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.max_reset) {                                                 //reset maximum speed?
            SharedPreferences.Editor mEditor = mSharedPref.edit();                    //yes, save to shared preferences
            mEditor.clear();
            mEditor.putFloat(getString(R.string.pref_saved_max_speed_key), 0f);                                   //zero saved maximum speed
            mEditor.apply();
            maxSpeed = 0f;
            tvMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f)); //zero display of maximum speed
            return true;
        }

        if (id == R.id.quit) {                                                       //request to exit/quit?
            finish();                                                                //yes, terminate the program
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

