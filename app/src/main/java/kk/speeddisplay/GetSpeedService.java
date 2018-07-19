package kk.speeddisplay;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;

/*
 * Implements a foreground service to get speed from the Fused Location provider.
 * This services runs continuously until stopped by the user.
 * Checks if the speed is above the previous maximum, and if it is saves the new maximum
 * in the preferences.
 * Sends current speed and maximum speed via a broadcast to the main activity.
 * If the main activity is not visible, the current & max speeds are not sent to the main activity.
 * Sets up a broadcast receiver to receive the update rate at which the the location provider
 * should provide location updates. The broadcast receiver also process two flags:
 *  1. if the main activity is visible or not visible,
 *  2. if we need to reset the maximum speed because the user has reset the maximum speed.
 */

public class GetSpeedService extends Service {

    private static final String TAG = GetSpeedService.class.getSimpleName();

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private float mMaxSpeed = 0.0F;
    private float savedSpeed;

    private boolean mMainActivityRunning;

    // gets update rate and other flags from the main activity
    BroadcastReceiver mRateBroadcastReceiver;

    private volatile HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;

    // class that stores the update rate
    private UpdateRate mUpdateRate;

    private Utilities mUtilities;

    public GetSpeedService() {
        super();
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "GetSpeedService()");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onCreate()");
        //initially say main activity is running
        mMainActivityRunning = true;

        mUtilities = new Utilities();

        // An Android handler thread internally operates on a looper.
        mHandlerThread = new HandlerThread("GetSpeed.HandlerThread");
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());

        //set background priority so CPU-intensive work doesn't disrupt our UI
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        //initialise new update rate which stores the update rates
        mUpdateRate = new UpdateRate();

        //register broadcast receiver to receive rate updates from main activity
        registerBroadcastReceiver();
    }

    /**
     * Builds a notification intent and,
     * runs the service in the foreground,
     * request location updates form Fused location client,
     * gets the maximum speed saved in the preferences and sends it to main for display
     *
     * @param intent  called by intent
     * @param flags   flags from intent
     * @param startId id of start
     * @return Integer that describes how the system should continue the service in the event that the system kills it
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onStartCommand");

        mServiceHandler.post(new Runnable() {
            @Override
            public void run() {

                //send notification to the main activity and run as a foreground service
                sendNotification();

                //set up fused location client, which is API from Google Play Services
                mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                /* use the location request to set up the parameters for the fused location provider */
                mLocationRequest = new LocationRequest();
                startLocationService();

                /* set the update rate in  milliseconds for the location provider to the default value.
                 * Fused location client does not work without an update rate before it is started
                 * The broadcasts from the main activity will update with the real update rate*/
                Context context = getApplicationContext();
                setLocationUpdateRate(mUpdateRate.getDefaultRunningRateInMilliSecs(context));

                // start the updates from the location provider
                try {
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
                } catch (
                        SecurityException securityException) {
                    Log.e(TAG, getString(R.string.permission_denied));
                }
            }
        });
        /* If the system kills the service after onStartCommand() returns,
         * recreate the service and call onStartCommand()
         */
        return START_STICKY;
    }

    /**
     * send notification to the main activity and run as a foreground service
     */
    private void sendNotification() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "sendNotification()");

        /* send notification to the main activity and run as a foreground service */
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.speed)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.notification_ticker_text))
                .build();

        startForeground(Constant.ONGOING_NOTIFICATION_ID, notification);
    }

    /**
     *
     */
    private void startLocationService() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "startLocationService())");

        /* set up the location callback for when the location has changed */
        mLocationCallback = new LocationCallback() {
            /* LocationResult a data class representing a geographic location result
             * from the fused location provider */
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    //Log.d(TAG, "onLocationResult locationResult=null");
                    return;
                }
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    float speed = location.getSpeed();
                    /* convert speed from metres/sec to km/hour */
                    speed = speed * 3600F / 1000F;

                    //check if speed has changed, only process if there has been a change
                    if (savedSpeed != speed) {
                        savedSpeed = speed;
                        //Log.d(TAG, "onLocationResult Speed: " + speed);
                        //check if previous max speed has been exceeded
                        mMaxSpeed = mUtilities.checkMaxSpeed(getApplicationContext(), speed, mMaxSpeed);
                        /* sends speed and max speed to main activity each time,
                         * Only send to main activity via broadcast if main activity running
                         */
                        if (mMainActivityRunning) {
                            sendToMain(speed, mMaxSpeed);
                        }
                    }
                }
            }
        };
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onBind()");

        return null;
    }

    @Override
    public void onDestroy() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onDestroy()");
        super.onDestroy();

        /* stop location updates */
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);

        //unregister broadcast receiver for update rate for location provider
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mRateBroadcastReceiver);

        // Cleanup service before destruction
        mHandlerThread.quit();
    }

    /**
     * set up broadcast to pass the speed, maxSpeed
     *
     * @param speed    latest speed
     * @param maxSpeed current maximum speed
     */

    private void sendToMain(float speed, float maxSpeed) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "sendToMain()");

        Intent updateSpeedIntent = new Intent();
        updateSpeedIntent.setAction(getString(R.string.ACTION_SendSpeedToMain));
        updateSpeedIntent.putExtra(getString(R.string.extra_key_speed), speed);
        updateSpeedIntent.putExtra(getString(R.string.extra_key_max_speed), maxSpeed);

        /* send the message to main activity */
        LocalBroadcastManager.getInstance(getApplicationContext())
                .sendBroadcast(updateSpeedIntent);
    }

    /**
     * Registers the broadcast receiver to receive rate updates from main activity
     * saves it in the global variable
     */

    private void registerBroadcastReceiver() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "registerBroadcastReceiver()");

        mRateBroadcastReceiver = new MyRateBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.ACTION_SendRateToService));
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mRateBroadcastReceiver, intentFilter);
    }

    /**
     * Updates the rate at which the location provider provides updates on location
     * always sets the accuracy to high
     * update rate is sent from the activity at startup, or
     * when the activity state changes, or when it has been changed by the user
     *
     * @param rate rate at which location provider provides updates
     */
    private void setLocationUpdateRate(long rate) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "setLocationUpdateRate()");

        mLocationRequest.setInterval(rate);
        mLocationRequest.setFastestInterval(rate);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
    }

    public class MyRateBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = MyRateBroadcastReceiver.class.getSimpleName();

        /**
         * gets the update rate from the intent and updates the location provider
         * checks if we need to reset the maximum speed, if yes, zero maximum speed,
         * save it in the preferences and send new maximum speed back to main for display,
         * updates flag indicating if main activity is running or not running
         *
         * @param context context
         * @param intent  broadcast intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onReceive()");

            //get the default update running rate
            long defaultRate = mUpdateRate.getDefaultRunningRateInMilliSecs(context);
            //get the update rate from the intent
            long rate = intent.getLongExtra(getString(R.string.extra_key_rate_value), defaultRate);
            /* update the location provider */
            setLocationUpdateRate(rate);

            /* check if we need to reset the maximum speed */
            boolean resetMaxSpeed = intent.getBooleanExtra(getString(R.string.extra_key_reset_max_speed), false);
            if (resetMaxSpeed) {
                mMaxSpeed = 0.0F;
                mUtilities.saveMaxSpeed(context, mMaxSpeed);
                //update main activity
                sendToMain(savedSpeed, mMaxSpeed);
            }

            /* update status of mMainActivityRunning */
            mMainActivityRunning = intent.getBooleanExtra(getString(R.string.extra_key_main_running), false);
            //Log.d(TAG, "onReceive rate: " + rate + " resetMaxSpeed; " + resetMaxSpeed +
            //        " mMainActivityRunning: " + mMainActivityRunning);
        }
    }

    // Define how the handler will process messages
    private final class ServiceHandler extends Handler {
        private final String TAG = ServiceHandler.class.getSimpleName();

        protected ServiceHandler(Looper looper) {
            super(looper);
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "ServiceHandler()");
        }

        // Define how to handle any incoming messages here
        @Override
        public void handleMessage(Message message) {
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "handleMessage()");

        }
    }
} //end of class GetSpeedService