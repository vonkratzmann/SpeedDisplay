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
import android.text.SpannableString;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * MainActivity
 * <p>
 * If permission granted, calls the method {@link #getMyLocation}. which starts
 * the foreground service which provides updates from the fused location provider.
 * If permission denied puts out a message and then exits.
 * <p>
 * User is able to specify the update intervals at which the activity will receive location updates,
 * separate update intervals for when the activity is running and not running.
 * The update intervals are saved in the shared preferences.
 * If the update intervals are changed by the user or if the activity state changes,
 * the appropriate rates are sent to the service via a broadcast.
 * Within the broadcast for the update intervals , there is one flag:
 * 1. Advises if the activity is running or not running, so speed updates are only sent from the
 * service when the main activity is running.
 */
public class MainActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    //region Fields
    // get a tag for output debugging
    private final static String TAG = MainActivity.class.getSimpleName();

    // used to start foreground service
    Intent mService;

    /* displays current speed from the location provider in the foreground service */
    private TextView mCurrentSpeedTextView;

    /* displays maximum speed recorded to date */
    private TextView mMaxSpeedTextView;

    /* gets speed updates from foreground service */
    private MySpeedBroadcastReceiver mSpeedBroadcastReceiver;

    /* update intervals at which the activity will receive location updates,
     * separate update intervals for when the activity is running and not running
     * the update interval is saved in the shared preferences
     * and can be changed by the user in settings
     * if changed by the user or if the activity state changes,
     * the appropriate rate sent to the service to set the update rate in the location provider.
     */
    private UpdateRate mRunningUpdateRate = new UpdateRate();
    private UpdateRate mNotRunningUpdateRate = new UpdateRate();
    //endregion

    //region Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCurrentSpeedTextView = findViewById(R.id.tv_CurrentSpeed);
        mMaxSpeedTextView = findViewById(R.id.tv_MaxSpeed);

        //register broadcast receiver to receive speed updates from service
        mSpeedBroadcastReceiver = new MySpeedBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.ACTION_SendSpeedToMain));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mSpeedBroadcastReceiver, intentFilter);

        //read settings from shared preferences
        setupSharedPreferences();

        //check permissions and if ok start foreground service
        checkPermissions();

        //display maximum speed obtained from saved value in preferences
        float maxSpeed = Preferences.getPrefMaxSpeed(getApplicationContext());
        String formattedMaxSpeed = Utilities.formatSpeed(this, maxSpeed);
        mMaxSpeedTextView.setText(formattedMaxSpeed);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onStart()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onResume()");

        /* screen now visible, send:
         * flag saying activity is running
         * flag saying not to reset max speed*/
        sendRateToService(mRunningUpdateRate.getRateInMilliSecs(), true, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onPause()");

        /* screen now not visible, send:
         * flag saying activity is not running
         * flag saying not to reset max speed */
        sendRateToService(mNotRunningUpdateRate.getRateInMilliSecs(), false, false);
    }


    @Override
    protected void onDestroy() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onDestroy()");

        super.onDestroy();
        shutDown();
    }
    //endregion

    //region Methods

    /**
     * Checks have permission to access location resources.
     * <p>
     * If permission granted, calls the method {@link #getMyLocation}. which starts
     * the foreground service which provides updates from the fused location provider.
     * If permission denied puts out a message and then exits.
     */
    private void checkPermissions() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "checkPermissions()");

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
            getMyLocation();
        } else {
            getMyLocation();
        }
    }


    /**
     * Retrieves from shared preferences update rates for when activity is running and not running.
     * Registers listener for any changes to shared preferences,
     * (listener unregistered in onDestroy)
     */
    private void setupSharedPreferences() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "setupSharedPreferences()");

        String rate;

        rate = Preferences.getPrefRunningRate(getApplicationContext());
        mRunningUpdateRate.setRate(rate);
        rate = Preferences.getPrefNotRunningRate(getApplicationContext());
        mNotRunningUpdateRate.setRate(rate);

        // register listener
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        pref.registerOnSharedPreferenceChangeListener(this);
    }


    private void shutDown() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "shutDown()");

        // unregister OnSharedPreferenceChangeListener
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(this);

        // unregister broadcast receiver for speed updates
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(mSpeedBroadcastReceiver);
    }


    /**
     * sends via a broadcast to the foreground service, the location provider update rate
     * flag if the activity is running
     *
     * @param rate update rate to be sent to the location provider
     */
    private void sendRateToService(long rate, boolean activityRunning, boolean maxSpeedReset) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "sendRateToService()");


        /* set up broadcast to pass the running update rate to the service*/
        Intent updateService = new Intent();
        updateService.setAction(getString(R.string.ACTION_SendRateToService));
        updateService.putExtra(getString(R.string.extra_key_rate_value), rate);
        updateService.putExtra(getString(R.string.extra_key_main_running), activityRunning);
        updateService.putExtra(getString(R.string.extra_key_max_speed_reset), maxSpeedReset);
        LocalBroadcastManager.getInstance(getApplicationContext())
                .sendBroadcast(updateService);
    }


    /**
     * Starts foreground service which
     * requests location updates from the fused location provider, which invokes LocationCallback
     * <p>
     * If fails exit application with a permission denied error message to the user
     */

    private void getMyLocation() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getMyLocation()");

        mService = new Intent(this, GetSpeedService.class);
        startService(mService);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onRequestPermissionsResult()");

        switch (requestCode) {
            case 10:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    getMyLocation();
        }
    }


    /**
     * Methods for setting up the menu
     **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onCreateOptionsMenu()");

        /* Use AppCompatActivity's method getMenuInflater to get a handle on the menu inflater */
        MenuInflater inflater = getMenuInflater();
        /* Use the inflater's inflate method to inflate our visualizer_menu layout to this menu */
        inflater.inflate(R.menu.menu_main, menu);
        /* Return true so that the visualizer_menu is displayed in the Toolbar */
        return true;
    }
    //endregion

    //region Listeners

    /**
     * Check which preference changed,
     * gets the new value which is returned as a float in seconds,
     * stores it in the appropriate global
     * the new rate will be sent to the service by the on resume() method
     *
     * @param sharedPreferences preference object with the change
     * @param keyInPrefs        key for preference that changed
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String keyInPrefs) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onSharedPreferenceChanged()");

        String key;
        String rate;

        //check if running update rate has changed
        key = getString(R.string.pref_key_running_update_rate);
        if (keyInPrefs.equals(key)) {
            rate = Preferences.getPrefRunningRate(getApplicationContext());
            mRunningUpdateRate.setRate(rate);
            return;
        }
        //check if not running update rate has changed
        key = getString(R.string.pref_key_not_running_update_rate);
        if (keyInPrefs.equals(key)) {
            rate = Preferences.getPrefNotRunningRate(getApplicationContext());
            mNotRunningUpdateRate.setRate(rate);
        }

        //check if maximum speed has changed
        key = getString(R.string.pref_key_saved_max_speed);
        if (keyInPrefs.equals(key)) {
            float maxSpeed = Preferences.getPrefMaxSpeed(getApplicationContext());
            String formattedMAxSpeed = Utilities.formatSpeed(getApplicationContext(), maxSpeed);
            mMaxSpeedTextView.setText(formattedMAxSpeed);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onOptionsItemSelected()");

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        /* check if request to reset maximum speed */
        if (id == R.id.max_reset) {
            /* if user has just cleared the max speed
             * so send running rate,
             * assume activity must be running,
             * send a flags to say activity running and reset max speed to service
             * service saves maximum speed in preferences,
             * max speed will be displayed when service sends next speed and max speed update
             */
            sendRateToService(mRunningUpdateRate.getRateInMilliSecs(), true, true);
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
            //terminate service and registered receivers
            shutDown();
            //Stop background service
            if (mService != null) {
                stopService(mService);
                mService = null;
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    //endregion

    //region InnerClasses
    public class MySpeedBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = MySpeedBroadcastReceiver.class.getSimpleName();

        /**
         * Retrieves speed and displays speed
         * ensures the correct units are displayed with the correct format
         *
         * @param context context
         * @param intent  source of broadcast
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onReceive()");

            // get the speed, format the speed, make spannable and display it
            Float speed = intent.getFloatExtra(getString(R.string.extra_key_speed), 0.0F);
            String formattedSpeed = Utilities.formatSpeed(context, speed);
            SpannableString spannedSpeed = Utilities.spanSpeed(formattedSpeed);
            mCurrentSpeedTextView.setText(spannedSpeed);

            //get the max speed, format the speed, and display it

            float maxSpeed = intent.getFloatExtra(getString(R.string.extra_key_max_speed), 0.0F);
            String formattedMaxSpeed = Utilities.formatSpeed(context, maxSpeed);
            mMaxSpeedTextView.setText(formattedMaxSpeed);

        }
        //endregion
    }
}
