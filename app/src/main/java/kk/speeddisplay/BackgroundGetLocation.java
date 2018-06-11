package kk.speeddisplay;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.util.Locale;

public class BackgroundGetLocation extends IntentService {

    private static final String TAG = BackgroundGetLocation.class.getSimpleName();

    //public static final String ACTION_IntentService = "com.example.android.backgroundgetlocation.RESPONSE";
    public static final String ACTION_UpdateFromBackground = "com.example.android.backgroundgetlocation.UPDATE";

    /* used to get the rate values from the activity intent
    * these set the rate of location updates from the fused location provider */
    public static final String EXTRA_KEY_RATE_VALUE = "EXTRA_RATE_VALUE";

   // public static final String EXTRA_KEY_OUT = "EXTRA_OUT";
    public static final String EXTRA_KEY_UPDATE_SPEED = "EXTRA_UPDATE_SPEED";

    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    public BackgroundGetLocation() {
        super("com.example.androidintentservice.BackgroundGetLocation");
        Log.d(TAG, "BackgroundGetLocation");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Log.d(TAG, "onHandleIntent");

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
                Log.d(TAG, "onLocationResult");
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    float speed = location.getSpeed();
                    /* convert speed from metres/sec to km/hour */
                    speed = speed * 3600f / 1000f;

                    //send new speed to activity
                    Intent intentResponse = new Intent();
                    intentResponse.setAction(ACTION_UpdateFromBackground);
                    intentResponse.addCategory(Intent.CATEGORY_DEFAULT);
                    intentResponse.putExtra(EXTRA_KEY_UPDATE_SPEED, speed);
                    sendBroadcast(intentResponse);
                }
            }
        };

        /* update rate is sent from the activity at startup, or
         * when the activity state changes, or when it has been changed by the user */

        long rate = intent.getLongExtra(EXTRA_KEY_RATE_VALUE,
                 MainActivity.RUNNING_UPDATE_RATE_DEFAULT);
        Log.d(TAG, "onHandleIntent" + " rate: " + rate);
        setLocationUpdateRate(rate);

        /* start request location updates */
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
        }
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
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}

