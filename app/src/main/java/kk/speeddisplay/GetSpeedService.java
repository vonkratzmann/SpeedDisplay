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

public class GetSpeedService extends Service {

    private static final String TAG = "SpeedDisplay " + GetSpeedService.class.getSimpleName();

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private float mMaxSpeed;
    private float savedSpeed;

    private boolean mMainActivityRunning;

    // gets update rate and other flags from the main activity
    BroadcastReceiver mRateBroadcastReceiver;

    private volatile HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;

    private  UpdateRate mUpdateRate;

    public GetSpeedService() {
        super();
        //Log.d(TAG, "GetSpeedService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Log.d(TAG, "onCreate");

        // An Android handler thread internally operates on a looper.
        mHandlerThread = new HandlerThread("GetSpeed.HandlerThread");
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());
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

        //Log.d(TAG, "onStartCommand");

        mServiceHandler.post(new Runnable() {
            @Override
            public void run() {
                // set background priority so CPU-intensive work doesn't disrupt our UI
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                mUpdateRate = new UpdateRate();

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

                /* set up fused location client, which is API from Google Play Services  */
                mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                /* use the location request to set up the parameters for the fused location provider */
                mLocationRequest = new LocationRequest();

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
                            /* save speed for possible broadcasts back to main */
                            savedSpeed = speed;

                            //Log.d(TAG, "onLocationResult Speed: " + speed);

                            mMaxSpeed = checkMaxSpeed(speed, mMaxSpeed);
                            /* sends speed and max speed to main activity each time,
                             * rather than sending flags which say maxSpeed has changed and
                             * then having code to check the flags and update the maxSpeed.
                             * Only send to main activity via broadcast if main activity is running */
                            if (mMainActivityRunning) {
                                sendToMain(speed, mMaxSpeed);
                            }
                        }
                    }
                };


                /* initially set the update rate to the default value.
                 * Rate has to be in  milliseconds for the location provider.
                 * Fused location client does not work without an update rate before it is started
                 * The broadcasts from the main activity will update with the real update rate*/
                Context context = getApplicationContext();
                setLocationUpdateRate(mUpdateRate.getDefaultRunningRateInMilliSecs(context));

                // start the location provider
                try {
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
                } catch (
                        SecurityException securityException) {
                    Log.e(TAG, getString(R.string.permission_denied));
                }

                //register broadcast receiver to receive rate updates from main activity
                registerBroadcastReceiver();

                /* get max speed from preferences and
                 * send it to main activity for display
                 * speed is zero initially, but will be updated when location provider provides updates
                 */
                mMaxSpeed = Preferences.getPrefMaxSpeed(getApplicationContext());
                sendToMain(0.0F, mMaxSpeed);
            }
        });

        /* If the system kills the service after onStartCommand() returns,
         * recreate the service and call onStartCommand(),
         *  but do not redeliver the last intent. Instead, the system calls onStartCommand()
         *  with a null intent unless there are pending intents to start the service.
         */
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy");
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
     * set up broadcast to pass the speed, maxSpeed, and flag to say if main activity should
     * process or ignore maxSpeed
     *
     * @param speed    latest speed
     * @param maxSpeed current maximum speed
     */

    private void sendToMain(float speed, float maxSpeed) {
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
        //Log.d(TAG, "setLocationUpdateRate: rate: " + rate);
        mLocationRequest.setInterval(rate);
        mLocationRequest.setFastestInterval(rate);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
    }

    /**
     * Checks if speed above previously maximum speed
     * if so save the new maximum speed in the preferences
     * always return the maximum speed, either the old maximum or the new maximum
     *
     * @param speed    latest speed from the location provider
     * @param maxSpeed current maximum speed
     * @return float    always return the maximum speed
     */
    private float checkMaxSpeed(float speed, float maxSpeed) {
        if (speed > maxSpeed) {
            /* we have a new maximum speed */
            //Log.d(TAG, "checkMaxSpeed new maximum: " + speed);
            /* save new maximum speed to shared preferences */
            saveMaxSpeed(speed);
            // return the new maximum speed
            return speed;
        }
        return maxSpeed;
    }

    /**
     * save maximum speed to shared preferences
     *
     * @param maxSpeed maximum speed to be saved
     */
    private void saveMaxSpeed(float maxSpeed) {
        //Log.d(TAG, "saveMaxSpeed maxSpeed: " + maxSpeed);
        /* save maximum speed to shared preferences */
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor mEditor = mSharedPref.edit();
        mEditor.clear().putFloat(getString(R.string.pref_key_saved_max_speed), maxSpeed).apply();
    }

    public class MyRateBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = "SpeedDisplay " + MyRateBroadcastReceiver.class.getSimpleName();

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
            long defaultRate = mUpdateRate.getDefaultRunningRateInMilliSecs(context);
            long rate = intent.getLongExtra(getString(R.string.extra_key_rate_value), defaultRate);
            /* update the location provider */
            setLocationUpdateRate(rate);

            /* check if we need to reset the maximum speed */
            boolean resetMaxSpeed = intent.getBooleanExtra(getString(R.string.extra_key_reset_max_speed), false);
            if (resetMaxSpeed) {
                mMaxSpeed = 0.0F;
                saveMaxSpeed(mMaxSpeed);
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
        protected ServiceHandler(Looper looper) {
            super(looper);
        }

        // Define how to handle any incoming messages here
        @Override
        public void handleMessage(Message message) {
            // ...
            // When needed, stop the service with
            // stopSelf();
        }
    }
} //end of class GetSpeedService