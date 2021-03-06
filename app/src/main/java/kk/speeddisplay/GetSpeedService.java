package kk.speeddisplay;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
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
 * Sends current speed via a broadcast to the main activity.
 * If the main activity is not visible, the current speed is not sent to the main activity.
 * Sets up a broadcast receiver to receive the update rate at which the the location provider
 * should provide location updates. The broadcast receiver also process two flags:
 *  1. if the main activity is visible or not visible,
 *  2. if user has requested a reset of max speed
 */

public class GetSpeedService extends Service {
    //region Fields
    private static final String TAG = GetSpeedService.class.getSimpleName();

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private float mSavedSpeed;
    private float mMaxSpeed;
    private boolean mMainActivityRunning;

    // gets update rate and other flags from the main activity
    BroadcastReceiver mRateBroadcastReceiver;

    private volatile HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;

    /* class that stores the update rate
     * used her as a convenience to access methods in UpdateRate
     * do not store the rate in mUpdateRate */
    private UpdateRate mUpdateRate;
    //endregion

    //region Lifecycle
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

        //display maximum speed obtained from saved value in preferences
        mMaxSpeed = Preferences.getPrefMaxSpeed(getApplicationContext());

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

                //send speed to main activity so it is displayed on startup
                sendToMain(0.0F, mMaxSpeed);

                //set up fused location client, which is API from Google Play Services
                mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                /* use the location request to set up the parameters for the fused location provider */
                mLocationRequest = new LocationRequest();
                startLocationService();

                /* set the update rate in  milliseconds for the location provider to the default value.
                 * The broadcasts from the main activity will update with the real update rate,
                 * the onResume() method in MainActivity sends the update rate to ths service.
                 */
                Context context = getApplicationContext();

                long rate = mUpdateRate.getDefaultRunningRateInMilliSecs(context);
                // start the updates from the location provider
                requestUpdates(rate);
            }
        });
        /* If the system kills the service after onStartCommand() returns,
         * recreate the service and call onStartCommand()
         */
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onDestroy()");
        super.onDestroy();

        /* stop location updates */
        if (mLocationCallback != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);

        //unregister broadcast receiver for update rate for location provider
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mRateBroadcastReceiver);

        // Cleanup service before destruction
        mHandlerThread.quit();
    }
    //endregion

    //region Methods
    /**
     * Start the updates from the location provider
     */
    void requestUpdates(long rate) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "requestUpdates()");

        //set the updates rates into the LocationRequest mLocationRequest
        setLocationUpdateRate(rate);

        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (
                SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
        }
    }


    /**
     * Updates the LocationRequest for the rate at which the location provider provides updates
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


    /**
     * send notification to the main activity and run as a foreground service
     */
    private void sendNotification() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "sendNotification()");

        /* send notification to the main activity and run as a foreground service */
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        //If Android 8.0 (API level 26), must implement one or more notification channels
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notifySpeedChannel = new
                    NotificationChannel(Constant.NOTIFY_CHANNEL_ID,
                    "Speed", NotificationManager.IMPORTANCE_DEFAULT);

            //Configure the channel's initial settings
            notifySpeedChannel.setLightColor(Color.GREEN);
            //Submit the channel to the Notification Manager
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notifySpeedChannel);
            }
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.speed)
                .setContentIntent(pendingIntent)
                .setChannelId(Constant.NOTIFY_CHANNEL_ID)               //for android 8.0
                .setTicker(getText(R.string.notification_ticker_text))
                .build();

        startForeground(Constant.ONGOING_NOTIFICATION_ID, notification);
    }


    /**
     *
     */
    private void startLocationService() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "startLocationService()");

        /* set up the location callback for when the location has changed */
        mLocationCallback = new LocationCallback() {
            /* LocationResult a data class representing a geographic location result
             * from the fused location provider */
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onLocationResult()");

                if (locationResult == null) {
                    if (MyDebug.DEBUG_LOCATION_SERVICE) Log.d(TAG, "LocationResult = null");
                    return;
                }
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    float speed = location.getSpeed();

                    /* convert speed from metres/sec to km/hour */
                    speed = speed * 3600F / 1000F;
                    if (MyDebug.DEBUG_LOCATION_SERVICE)
                        Log.d(TAG, "Speed = " + Float.toString(speed));

                    //check if speed has changed, only process if there has been a change
                    if (mSavedSpeed != speed) {
                        mSavedSpeed = speed;
                        //check if previous max speed has been exceeded
                        if (speed > mMaxSpeed) {
                            mMaxSpeed = speed;
                            saveMaxSpeed(speed);
                        }
                        //sends speed to main activity only if main activity is running
                        if (mMainActivityRunning) {
                            sendToMain(mSavedSpeed, mMaxSpeed);
                        }
                    }
                }
            }
        };
    }


    /**
     * @param maxSpeed new max speed to be saved
     */
    private void saveMaxSpeed(float maxSpeed) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "saveMaxSpeed()");

        //save new maximum speed to shared preferences
        Preferences.saveMaxSpeed(getApplicationContext(), maxSpeed);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "onBind()");

        return null;
    }


    /**
     * set up broadcast to pass the speed
     *
     * @param speed latest speed
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
    //endregion

    //region InnerClasses
    public class MyRateBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = MyRateBroadcastReceiver.class.getSimpleName();

        /**
         * gets the update rate from the intent and updates the location provider
         * updates flag indicating if main activity is running or not running
         * checks if reset max speed requested by the user
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

            //request updates form the fusion provider with the new update rate
            requestUpdates(rate);

            /* update status of mMainActivityRunning
             * if UI was not running & changed to running, send the latest speeds to be displayed */
            if (!mMainActivityRunning && intent.getBooleanExtra(getString(R.string.extra_key_main_running), false)) {
                sendToMain(mSavedSpeed, mMaxSpeed);
            }
            //update running flag
            mMainActivityRunning = intent.getBooleanExtra(getString(R.string.extra_key_main_running), false);

            /* check if maxSpeed reset by the user
             * if it has clear maxSpeed, save it in the preferences,
             * as MainActivity does not do any processing on maxSpeed, it only displays it.
             * Send back to MainActivity so MainActivity displays the new reset maxSpeed.
             */
            if (intent.getBooleanExtra(getString(R.string.extra_key_max_speed_reset), false)) {
                mMaxSpeed = 0.0F;
                saveMaxSpeed(mMaxSpeed);
                //send updated max speed back to the user
                sendToMain(mSavedSpeed, mMaxSpeed);
            }
        }
    }


    // Define how the handler will process messages
    private final class ServiceHandler extends Handler {
        private final String TAG = ServiceHandler.class.getSimpleName();

        private ServiceHandler(Looper looper) {
            super(looper);
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "ServiceHandler()");
        }


        // Define how to handle any incoming messages here
        @Override
        public void handleMessage(Message message) {
            if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "handleMessage()");
        }
    }
    //endregion
} //end of class GetSpeedService