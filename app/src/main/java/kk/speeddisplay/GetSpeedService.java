package kk.speeddisplay;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

    private static final String TAG = GetSpeedService.class.getSimpleName();

    //public static final String ACTION_IntentService = "com.example.android.GetSpeedService.RESPONSE";
    public static final String ACTION_UpdateFromBackground = "com.example.android.GetSpeedService.UPDATE";

    /* used to get the rate values from the activity intent
     * these set the rate of location updates from the fused location provider */
    public static final String EXTRA_KEY_RATE_VALUE = "EXTRA_RATE_VALUE";

    // public static final String EXTRA_KEY_OUT = "EXTRA_OUT";
    public static final String EXTRA_KEY_UPDATE_SPEED = "EXTRA_UPDATE_SPEED";

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private final static int ONGOING_NOTIFICATION_ID = 1;

    private float mMaxSpeed;

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
                    updateSpeedIntent.setAction("com.example.kk.speeddisplay.SPEED_NOTIFICATION");
                    updateSpeedIntent.putExtra(getString(R.string.intent_speed), speed);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(updateSpeedIntent);
                    
                     /* check if maximum speed needs to be updated
                    /* if yes, save it in the preferences and send to main activity */
                    checkMaxSpeed(speed);
                    /* set up broadcast to pass the max speed back to main activity for display */
                    Intent updateMaxSpeedIntent = new Intent();
                    updateMaxSpeedIntent.setAction("com.example.kk.speeddisplay.MAX_NOTIFICATION");
                    updateMaxSpeedIntent.putExtra(getString(R.string.intent_max_speed),
                            speed);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(updateMaxSpeedIntent);
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
            long rate = intent.getLongExtra(EXTRA_KEY_RATE_VALUE,
                    MainActivity.RUNNING_UPDATE_RATE_DEFAULT);
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
     */
    private void checkMaxSpeed(float speed) {
        if (speed > mMaxSpeed) {
            /* we have a new maximum speed */
            mMaxSpeed = speed;
            Log.d(TAG, "checkMaxSpeed mMaxSpeed: " + mMaxSpeed);
            /* save maximum speed to shared preferences */
            SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear().putFloat(getString(R.string.pref_saved_max_speed_key),
                    mMaxSpeed).apply();
        }
    }

} //end of class


