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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.util.Locale;

public class GetSpeedService extends Service {

    private static final String TAG = "SpeedDisplay " + GetSpeedService.class.getSimpleName();

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private final static int ONGOING_NOTIFICATION_ID = 1;

    private float mMaxSpeed;

    /* gets update rate for the location provider from the main activity */
    BroadcastReceiver mRateBroadcastReceiver;

    public GetSpeedService() {
        super();
        Log.d(TAG, "GetSpeedService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        /* set up fused location client, which is API from Google Play Services  */
        mFusedLocationClient = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(this);
        /* use the location request to set up the parameters for the fused location provider */
        mLocationRequest = new LocationRequest();
        /* tell user we have started checking speed */
        Toast.makeText(getApplicationContext(), "checking speed", Toast.LENGTH_SHORT).show();

        /* set up the location callback for when the location has changed */
        mLocationCallback = new LocationCallback() {
            /* LocationResult a data class representing a geographic location result
             * from the fused location provider */
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    float speed = location.getSpeed();
                    /* convert speed from metres/sec to km/hour */
                    speed = speed * 3600f / 1000f;

                    Log.d(TAG, "onLocationResult Speed: " + speed);

                    /* set up broadcast to pass the speed back to main activity for display */
                    Intent updateSpeedIntent = new Intent();
                    updateSpeedIntent.setAction(getString(R.string.ACTION_SendSpeedToMain));
                    updateSpeedIntent.putExtra(getString(R.string.extra_key_intent_speed), speed);
                    LocalBroadcastManager.getInstance(getApplicationContext())
                            .sendBroadcast(updateSpeedIntent);
                    
                     /* check if maximum speed needs to be updated
                    /* if yes, save it in the preferences and send to main activity */
                    if (checkMaxSpeed(speed)) {
                        /* set up broadcast to pass max speed back to main activity for display */
                        Intent updateMaxSpeedIntent = new Intent();
                        updateMaxSpeedIntent.setAction(getString(R.string.ACTION_SendMaxToMain));
                        updateMaxSpeedIntent.putExtra(getString(R.string.extra_key_intent_max_speed),
                                speed);
                        LocalBroadcastManager.getInstance(getApplicationContext())
                                .sendBroadcast(updateMaxSpeedIntent);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand");

        /* check valid intent, service may have been stopped and restarted by android */
        if (intent != null) {
            /* get location update rate from main activity intent */
            long rate = intent.getLongExtra(getString(R.string.extra_key_rate_value),
                    Constant.RUNNING_UPDATE_RATE_DEFAULT);
            Log.d(TAG, "onStartCommand" + " rate: " + rate);
            /* update LocationRequest object */
            setLocationUpdateRate(rate);
        }
        /* send notification to the main activity and run as a foreground service */
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, "default")
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.gps)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.notification_ticker_text))
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        /* start the location provider */
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (
                SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
        }

        //register broadcast receiver to receive rate updates from main activity
        mRateBroadcastReceiver = new MyRateBroadcastReceiver();

        IntentFilter intentFilter = new IntentFilter(getString(R.string.ACTION_SendRateToService));
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mRateBroadcastReceiver, intentFilter);

        /* If the system kills the service after onStartCommand() returns,
         * recreate the service and call onStartCommand()
         * with the last intent that was delivered to the service.
         */
        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        //unregister broadcast receiver for update rate for location provider
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mRateBroadcastReceiver);
    }

    /**
     * Retrieves saved maximum speed from Shared preferences
     */
    private void getPrefMaxSpeed() {
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mMaxSpeed = mSharedPref.getFloat(getString(R.string.pref_saved_max_speed_key), 0.0F);
        Log.d(TAG, "updatePrefMaxSpeed mMaxSpeed: " + Float.toString(mMaxSpeed));
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
        Log.d(TAG, "setLocationUpdateRate: rate: " + rate);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(rate);
        mLocationRequest.setFastestInterval(rate);
    }

    /**
     * Checks if speed above previously stored maximum speed
     * if so save the new maximum speed in the preferences
     *
     * @param speed latest speed from the location provider
     * @return boolean return true if we have new maximum speed
     */
    private boolean checkMaxSpeed(float speed) {
        boolean newMaxSpeed = false;
        if (speed > mMaxSpeed) {
            /* we have a new maximum speed */
            mMaxSpeed = speed;
            Log.d(TAG, "checkMaxSpeed mMaxSpeed: " + mMaxSpeed);
            /* save maximum speed to shared preferences */
            SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear().putFloat(getString(R.string.pref_saved_max_speed_key),
                    mMaxSpeed).apply();
            newMaxSpeed = true;
        }
        return newMaxSpeed;
    }

    public class MyRateBroadcastReceiver extends BroadcastReceiver {
        private final String TAG = "SpeedDisplay " + MyRateBroadcastReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {
            long rate = intent.getLongExtra(getString(R.string.extra_key_rate_value), Constant.RUNNING_UPDATE_RATE_DEFAULT);
            Log.d(TAG, "onReceive rate: " + rate);
        }
    }
} //end of class GetSpeedService