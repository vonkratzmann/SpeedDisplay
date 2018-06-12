package kk.speeddisplay;
/*
 * Speed Display V1.0
 */

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Locale;

/**
 * MainActivity
 * <p>
 */
public class MainActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    /* get a tag for output debugging */
    private final static String TAG = "SpeedDisplay " + MainActivity.class.getSimpleName();

    /* displays current speed from the location provider in the foreground service */
    private TextView mCurrentSpeedTextView;

    /* displays maximum speed recorded to date,
     * max speed is saved in the shared preferences
      * max speed speed is calculated in the foreground service */
    private TextView mMaxSpeedTextView;

    /* gets speed updates from foreground service checking for location updates */
    private MySpeedBroadcastReceiver mSpeedBroadcastReceiver;
    /* gets maximum speed updates from foreground service */
    private MySpeedBroadcastReceiver mMaxSpeedBroadcastReceiver;

    Intent mIntentService;

    private float mMaxSpeed;

    /* update intervals at which the activity will receive location updates,
     * separate update intervals for when the activity is running and not running
     * these are saved in the shared preferences
     * and can be changed by the user in settings
     * if changed passed to the service or
     * if activity state changes, the appropriate rate sent to the service
     */
    private long mActivityRunningUpdateRate;
    private long mActivityNotRunningUpdateRate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCurrentSpeedTextView = findViewById(R.id.tv_CurrentSpeed);
        mMaxSpeedTextView = findViewById(R.id.tv_MaxSpeed);

        /* read settings form shared preferences and update location provider and screen */
        setupSharedPreferences();
        checkPermissions();
    }

    /**
     * Checks have permission to access location resources.
     * <p>
     * If permission granted, calls the method {@link #getLocation}. which starts
     * the foreground service which provides updates from the fused location provider.
     * If permission denied puts out a message and then exits.
     */

    private void checkPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET
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
        getPrefMaxSpeed();
        getPrefRunningRate(mSharedPref, getString(R.string.pref_running_update_rate_key));
        getPrefNotRunningRate(mSharedPref, getString(R.string.pref_not_running_update_rate_key));
        /* register listener here for any changes and unregister in onDestroy */
        mSharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_running_update_rate_key))) {
            getPrefRunningRate(sharedPreferences, key);
        } else if (key.equals(getString(R.string.pref_not_running_update_rate_key))) {
            getPrefNotRunningRate(sharedPreferences, key);
        }
    }

    /**
     * Retrieves saved maximum speed from Shared preferences
     * and then displays this speed
     */
    private void getPrefMaxSpeed() {
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mMaxSpeed = mSharedPref.getFloat(getString(R.string.pref_saved_max_speed_key), 0.0F);
        Log.d(TAG, "updatePrefMaxSpeed mMaxSpeed: " + Float.toString(mMaxSpeed));
        /* display max speed */
        mMaxSpeedTextView.setText(String.format(Locale.UK,
                getString(R.string.units_and_number_of_decimals), mMaxSpeed));
    }

    /**
     * Updates rate at which location provider provides updates when the activity is running
     * gets the new rate fom the share preferences
     * converts it to milliseconds and saves it as a long
     * in the rate variable
     *
     * @param sharedPreferences SharedPreference object where preferences are stored
     * @param key               preference key
     */
    private void getPrefRunningRate(SharedPreferences sharedPreferences, String key) {
        /* get rate, convert to milliseconds, save it as update provider requires milliseconds */
        String rate = sharedPreferences.getString(key,
                Constant.RUNNING_UPDATE_RATE_DEFAULT.toString());

        Log.d(TAG, "updatePrefRunningRate rate: " + rate);
        Float rateFloat = Float.valueOf(rate);
        /* convert to milliseconds and store as a long*/
        rateFloat = rateFloat * 1000F;
        mActivityRunningUpdateRate = rateFloat.longValue();
        /* If activity is currently running then update location provider */
        if (isActivityRunning()) {
            sendRateToService(mActivityRunningUpdateRate);
        }
    }

    /**
     * Updates rate at which location provider provides updates when the activity is not running
     * gets the new rate fom the share preferences
     * converts it to milliseconds and saves it as a long
     * in the rate variable
     *
     * @param sharedPreferences SharedPreference object where preferences are stored
     * @param key               preference key
     */
    private void getPrefNotRunningRate(SharedPreferences sharedPreferences, String key) {
        /* get rate, convert to milliseconds, save it as update provider requires milliseconds */
        String rate = sharedPreferences.getString(key,
                Constant.NOT_RUNNING_UPDATE_RATE_DEFAULT.toString());

        Log.d(TAG, "getPrefNotRunningRate rate: " + rate);
        Float rateFloat = Float.valueOf(rate);
        /* convert to milliseconds and store as a long as update provider requires milliseconds */
        rateFloat = rateFloat * 1000F;
        mActivityNotRunningUpdateRate = rateFloat.longValue();

        /* If activity is currently not running then update location provider */
        if (!isActivityRunning()) {
            sendRateToService(mActivityNotRunningUpdateRate);
        }
    }

    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    /**
     * Sets the location update intervals to the activity running values
     * updates a boolean key in shared preferences to say the activity is running
     * register broadcast receiver to receive speed updates from service
     */
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        
        // Update shared preference to say activity is running
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key), MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(getString(R.string.pref_activity_state_key), true);
        ed.apply();

        //register broadcast receiver to receive speed updates from service
        mSpeedBroadcastReceiver = new MySpeedBroadcastReceiver();

        IntentFilter intentFilter = new IntentFilter(getString(R.string.ACTION_SendSpeedToMain));
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mSpeedBroadcastReceiver, intentFilter);
        
        /* screen now visible and activity running, send new update rate to location provider */
        sendRateToService(mActivityRunningUpdateRate);
    }


    /**
     * Sets the location update intervals to the activity not running values
     * updates a boolean key in shared preferences to say the activity is not running
     * unregister broadcast receiver so do not receive speed updates from service
     */
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
       
        // Update shared preference to say activity is not running
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key), MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(getString(R.string.pref_activity_state_key), false);
        ed.apply();

        //unregister broadcast receiver for speed updates as no longer in focus
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mSpeedBroadcastReceiver);

        /* screen not visible and activity not running, send new update rate to location provider */
        sendRateToService(mActivityNotRunningUpdateRate);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        //Stop background service
        if (mIntentService != null) {
            stopService(mIntentService);
            mIntentService = null;
        }
    }

    /**
     * sends via broadcast location provider update rate to the service
     *
     * @param rate  update rate to be sent to the location provider
     */
    private void sendRateToService(long rate) {
        Log.d(TAG, "sendRateToService rate: " + rate);
        /* set up broadcast to pass the running update rate to the service*/
        Intent updateRate = new Intent();
        updateRate.setAction(getString(R.string.ACTION_SendRateToService));
        updateRate.putExtra(getString(R.string.extra_key_rate_value), rate);
        LocalBroadcastManager.getInstance(getApplicationContext())
                .sendBroadcast(updateRate);
    }

    /**
     * Checks state of activity ie is it running by accessing a shared preferences key
     * which is set to true by onResume() and set to false by onPause()
     *
     * @return true if activity is running or false if not running
     */
    boolean isActivityRunning() {
        SharedPreferences sp = getSharedPreferences(getString(R.string.pref_activity_state_key),
                MODE_PRIVATE);
        return sp.getBoolean(getString(R.string.pref_activity_state_key), false);
    }

    /**
     * Starts foreground service
     * Requests location updates from the fused location provider, which invokes LocationCallback
     * <p>
     * If fails exit application with a permission denied error message to the user
     */

    private void getLocation() {

        /* start the foreground service */
        mIntentService = new Intent(MainActivity.this, GetSpeedService.class);
        startService(mIntentService);
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

    /**
     * Check strings represents a valid floating point number
     * and its value is greater than zero and less than the largest Long number
     * (why anybody would want such a big number, I don't know)
     *
     * @param textNumber String which represents a floating point number
     * @return true if strings represents valid float number
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

    public class MySpeedBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = "SpeedDisplay " + MySpeedBroadcastReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {
            Float speed = intent.getFloatExtra(getString(R.string.extra_key_intent_speed), 0.0F);
            Log.d(TAG, "onReceive Speed: " + speed);

            /* display the speed */
            mCurrentSpeedTextView.setText(String.format(Locale.UK,
                    getString(R.string.units_and_number_of_decimals), speed));
        }
    }


    public class MyMaxBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = "SpeedDisplay " + MyMaxBroadcastReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {
            Float max = intent.getFloatExtra(getString(R.string.extra_key_intent_max_speed), 0.0F);
            Log.d(TAG, "onReceive max: " + max);

            /* display the speed */
            mMaxSpeedTextView.setText(String.format(Locale.UK,
                    getString(R.string.units_and_number_of_decimals), max));
        }
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
            mMaxSpeed = 0f;
            mMaxSpeedTextView.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f));
            return true;
        }
        /* check if request to navigate to the settings screen */
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
}
