package kk.speeddisplay;
/*
 * Speed Display v1.0
 */

import android.Manifest;
import android.content.Context;
import android.content.Intent;
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
import android.view.MenuInflater;
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
public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

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
    private long activityRunningUpdateRate;
    private final static String RunningUpdateRateDefault = "2";

    private long activityNotRunningUpdateRate;
    private final static String NotRunningUpdateRateDefault = "30";

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvCurrentSpeed = (TextView) findViewById(R.id.tv_CurrentSpeed);
        tvMaxSpeed = (TextView) findViewById(R.id.tv_MaxSpeed);

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
                    float speed = location.getSpeed();
                    displaySpeed(speed);
                    checkMaxSpeed(speed);
                }
            }
        };
        /* read settings form shared preferences and update location provider and screen */
        setupSharedPreferences();
        checkPermissions();
    }

    /**
     * Methods for setting up the menu
     **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Use AppCompatActivity's method getMenuInflater to get a handle on the menu inflater */
        MenuInflater inflater = getMenuInflater();
        /* Use the inflater's inflate method to inflate our visualizer_menu layout to this menu */
        inflater.inflate(R.menu.menu_main, menu);
        /* Return true so that the visualizer_menu is displayed in the Toolbar */
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        /* check if request to reset maximum speed */
        if (id == R.id.max_reset) {
            /* save the new speed in shared preferences */
            SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear();
            mEditor.putFloat(getString(R.string.pref_saved_max_speed_key), 0f);
            mEditor.apply();
            /* display the zeroed speed */
            maxSpeed = 0f;
            tvMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f));
            return true;
        }
        /* check if request to navigate to the settings scren */
        if (id == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        }
        /* check if request to exit */
        if (id == R.id.quit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
     * Retrieves shared preferences for maximum speed & location provider update rate
     */
    private void setupSharedPreferences() {
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        /* Get the values from shared preferences and store in appropriate variables */
        updatePrefMaxSpeed();
        updatePrefRunningRate(mSharedPref, getString(R.string.pref_running_update_rate_key));
        updatePrefNotRunningRate(mSharedPref, getString(R.string.pref_not_running_update_rate_key));
        /* register listener here for any changes and unregister in onDestroy */
        mSharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_running_update_rate_key))) {
            updatePrefRunningRate(sharedPreferences, key);
        } else if (key.equals(getString(R.string.pref_not_running_update_rate_key))) {
            updatePrefNotRunningRate(sharedPreferences, key);
        }
    }

    /**
     * Retrieves saved maximum speed detected t from Shared preferences
     * and then displays this speed
     */
    private void updatePrefMaxSpeed() {
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        maxSpeed = mSharedPref.getFloat(getString(R.string.pref_saved_max_speed_key), 0.0F);
        Log.d(TAG, "updatePrefMaxSpeed maxSpeed: " + Float.toString(maxSpeed));
        /* display max speed */
        tvMaxSpeed.setText(String.format(Locale.UK, getString(R.string.units_and_number_of_decimals), maxSpeed));
    }

    /**
     * Updates rate at which location provider provides updates when the activity is running
     * gets the new rate fom the share preferences
     * checks valid format
     * converts it to milliseconds and saves it
     *
     * @param sharedPreferences SharedPreference object where preferences are stored
     * @param key               preference key
     */
    private void updatePrefRunningRate(SharedPreferences sharedPreferences, String key) {
        /* get the rate and convert to milliseconds and save it */
        String updateRate = sharedPreferences.getString(key, RunningUpdateRateDefault);
        Float rate;
        if (checkFloatFormat(updateRate)) {
            rate = Float.valueOf(updateRate);
        } else {
            rate = Float.valueOf(RunningUpdateRateDefault);
        }
        Log.d(TAG, "updatePrefRunningRate rate: " + rate);

        /* convert to milliseconds and store as a long*/
        rate = rate * 1000f;
        activityRunningUpdateRate = rate.longValue();
        Log.d(TAG, "updatePrefRunningRate activityRunningUpdateRate: " + activityRunningUpdateRate);
        /* If activity is currently running then update location provider */
        if (isActivityRunning()) {
            setLocationUpdateRate(activityRunningUpdateRate);
        }
    }

    /**
     * Updates rate at which location provider provides updates when the activity is not running
     * gets the new rate fom the share preferences
     * checks valid format
     * converts it to milliseconds and saves it
     * getting the new rate fom the share preferences and
     * then updating the rate variable
     *
     * @param sharedPreferences SharedPreference object where preferences are stored
     * @param key               preference key
     */
    private void updatePrefNotRunningRate(SharedPreferences sharedPreferences, String key) {
        /* get the rate and convert to milliseconds and save it*/
        String updateRate = sharedPreferences.getString(key, NotRunningUpdateRateDefault);
        Float rate;
        if (checkFloatFormat(updateRate)) {
            rate = Float.valueOf(updateRate);
        } else {
            rate = Float.valueOf(NotRunningUpdateRateDefault);
        }
        Log.d(TAG, "getPrefNotRunningRate rate: " + rate);

        /* convert to milliseconds and store as a long*/
        rate = rate * 1000f;
        activityNotRunningUpdateRate = rate.longValue();

        Log.d(TAG, "updatePrefNotRunningRate activityNotRunningUpdateRate: " + activityNotRunningUpdateRate);
        /* If activity is currently not running then update location provider */
        if (!isActivityRunning()) {
            setLocationUpdateRate(activityNotRunningUpdateRate);
        }
    }


    /**
     * Check strings represents a valid floating point number
     * and its value is greater than zero and less than the largest Long number
     * (why anybody would want such a big number, I don't know)
     *
     * @param textNumber String which represents a floating point number
     * @return true if strings representa valid float number
     */
    public static boolean checkFloatFormat(String textNumber) {
        try {
            Float number = Float.valueOf(textNumber);
            if (number <= 0 || number >= Long.MAX_VALUE) {
                return false;
            }
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    /**
     * Updates the rate at which the location provider provides updates on location
     * always sets the accuracy to high
     *
     * @param rate rate at which location provider provides updates
     */
    private void setLocationUpdateRate(long rate) {
        Log.d(TAG, "setLocationUpdateRate: rate: " + rate);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(rate);
        mLocationRequest.setFastestInterval(rate);
    }

    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    /**
     * Sets the location update intervals to the activity running values
     * updates a boolean key in shared preferences to say the activity is running
     */
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        setLocationUpdateRate(activityRunningUpdateRate);
        // Update shared preference to say activity is running
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key), MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(getString(R.string.pref_activity_state_key), true);
        ed.commit();
    }

    /**
     * Sets the location update intervals to the activity not running values
     * updates a boolean key in shared preferences to say the activity is not running
     */
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        /* set the update rate in the location provider to the not running value*/
        setLocationUpdateRate(activityNotRunningUpdateRate);
        // Update shared preference to say activity is not running
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key), MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(getString(R.string.pref_activity_state_key), false);
        ed.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Checks state of activity ie is it running by accessing a shared preferences key
     * which is set to true by onResume() and set to false by onPause()
     *
     * @return
     */
    boolean isActivityRunning() {
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key), MODE_PRIVATE);
        return sp.getBoolean(getString(R.string.pref_activity_state_key), false);
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
            Log.d(TAG, "checkMaxSpeed maxSpeed: " + maxSpeed);
            /* save maximum speed to shared preferences */
            SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear().putFloat(getString(R.string.pref_saved_max_speed_key), maxSpeed).apply();
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
}

