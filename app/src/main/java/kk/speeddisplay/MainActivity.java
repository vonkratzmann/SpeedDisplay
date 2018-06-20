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
     * max speed speed is calculated in the foreground service
     * max speed is saved in the shared preferences by the foreground service
     */
    private TextView mMaxSpeedTextView;

    /* gets speed updates from foreground service */
    private MySpeedBroadcastReceiver mSpeedBroadcastReceiver;

    Intent mService;

    /* update intervals at which the activity will receive location updates,
     * separate update intervals for when the activity is running and not running
     * the update interval is saved in the shared preferences
     * and can be changed by the user in settings
     * if changed by the user or if the activity state changes,
     * the appropriate rate sent to the service to set the update rate in the location provider.
     */
    private UpdateRate mRunningUpdateRate = new UpdateRate();
    private UpdateRate mNotRunningUpdateRate = new UpdateRate();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCurrentSpeedTextView = findViewById(R.id.tv_CurrentSpeed);
        mMaxSpeedTextView = findViewById(R.id.tv_MaxSpeed);

        /* read settings form shared preferences and update location provider and screen */
        checkPermissions();
        setupSharedPreferences();

        /*register broadcast receiver to receive speed updates from service */
        mSpeedBroadcastReceiver = new MySpeedBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.ACTION_SendSpeedToMain));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mSpeedBroadcastReceiver, intentFilter);
    }

    /**
     * Checks have permission to access location resources.
     * <p>
     * If permission granted, calls the method {@link #getMyLocation}. which starts
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
        //Log.d(TAG, " setupSharedPreferences");
        String rate;

        rate = Preferences.getPrefRunningRate(this);
        mRunningUpdateRate.setRate(rate);
        rate = Preferences.getPrefNotRunningRate(this);
        mNotRunningUpdateRate.setRate(rate);

        // register listener
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Check which preference changed,
     * gets the new value which is returned as a float in seconds,
     * stores it in the appropriate global
     *
     * @param sharedPreferences preference object with the change
     * @param keyInSecs         key for preference that changed
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String keyInSecs) {
        String key;
        String rate;

        key = getString(R.string.pref_key_running_update_rate);
        if (keyInSecs.equals(key)) {
            rate = Preferences.getPrefRunningRate(this);
            mRunningUpdateRate.setRate(rate);
            return;
        }
        key = getString(R.string.pref_key_not_running_update_rate);
        if (keyInSecs.equals(key)) {
            rate = Preferences.getPrefNotRunningRate(this);
            mNotRunningUpdateRate.setRate(rate);
        }
    }

    protected void onStart() {
        super.onStart();
        //Log.d(TAG, "onStart");
    }

    protected void onResume() {
        super.onResume();
        //Log.d(TAG, "onResume");

        /* screen now visible, send:
         * running update rate, flag saying to not reset maximum speed, and
         * flag saying activity is running */
        sendRateToService(mRunningUpdateRate.getRateInMilliSecs(), false, true);
    }

    protected void onPause() {
        super.onPause();
        //Log.d(TAG, "onPause");

        /* screen now not visible, send:
         * not running update rate, flag saying to not reset maximum speed, and
         * flag saying activity is not running */
        sendRateToService(mNotRunningUpdateRate.getRateInMilliSecs(), false, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutDown();
    }

    private void shutDown() {
        // unregister OnSharedPreferenceChangeListener
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        // unregister broadcast receiver for speed updates
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(mSpeedBroadcastReceiver);

        //Stop background service
        if (mService != null) {
            stopService(mService);
            mService = null;
        }
    }

        /**
         * sends via broadcast location provider update rate to the service and
         * flag if the maximum speed should be reset and
         * flag if the activity is running
         *
         * @param rate          update rate to be sent to the location provider
         * @param resetMaxSpeed if true tell service to clear max speed
         */
        private void sendRateToService ( long rate, boolean resetMaxSpeed, boolean activityRunning){
            // Log.d(TAG, "sendRateToService rate: " + rate + " resetMaxSpeed: " + resetMaxSpeed +
            //         " activityRunning: " + activityRunning);

            /* set up broadcast to pass the running update rate to the service*/
            Intent updateRate = new Intent();
            updateRate.setAction(getString(R.string.ACTION_SendRateToService));
            updateRate.putExtra(getString(R.string.extra_key_rate_value), rate);
            updateRate.putExtra(getString(R.string.extra_key_reset_max_speed), resetMaxSpeed);
            updateRate.putExtra(getString(R.string.extra_key_main_running), activityRunning);
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .sendBroadcast(updateRate);
        }

        /**
         * Starts foreground service which
         * requests location updates from the fused location provider, which invokes LocationCallback
         * <p>
         * If fails exit application with a permission denied error message to the user
         */

        private void getMyLocation () {
            mService = new Intent(this, GetSpeedService.class);
            startService(mService);
        }


        @Override
        public void onRequestPermissionsResult ( int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults){
            switch (requestCode) {
                case 10:
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                        getMyLocation();
            }
        }

        public class MySpeedBroadcastReceiver extends BroadcastReceiver {
            private final String TAG = "SpeedDisplay " + MySpeedBroadcastReceiver.class.getSimpleName();

            /**
             * Retrieves both speed and maximum speed and displays them both
             * ensures the correct units are displayed with the correct format
             *
             * @param context context
             * @param intent  source of broadcast
             */
            @Override
            public void onReceive(Context context, Intent intent) {
                // get the speed, format the speed, make spannable and display it
                Float speed = intent.getFloatExtra(getString(R.string.extra_key_speed), 0.0F);
                String formattedSpeed = Utilities.formatSpeed(context, speed);
                SpannableString spannedSpeed = Utilities.spanSpeed(formattedSpeed);
                mCurrentSpeedTextView.setText(spannedSpeed);

                /* get the max speed, format it  and display it */
                Float maxSpeed = intent.getFloatExtra(getString(R.string.extra_key_max_speed), 0.0F);

                String formattedMAxSpeed = Utilities.formatSpeed(context, maxSpeed);
                mMaxSpeedTextView.setText(formattedMAxSpeed);
            }
        }

        /**
         * Methods for setting up the menu
         **/
        @Override
        public boolean onCreateOptionsMenu (Menu menu){
            /* Use AppCompatActivity's method getMenuInflater to get a handle on the menu inflater */
            MenuInflater inflater = getMenuInflater();
            /* Use the inflater's inflate method to inflate our visualizer_menu layout to this menu */
            inflater.inflate(R.menu.menu_main, menu);
            /* Return true so that the visualizer_menu is displayed in the Toolbar */
            return true;
        }

        @Override
        public boolean onOptionsItemSelected (MenuItem item){
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            int id = item.getItemId();

            /* check if request to reset maximum speed */
            if (id == R.id.max_reset) {
                /* sends message to service to clear max speed */
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
                finish();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
