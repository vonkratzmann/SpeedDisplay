package kk.speeddisplay;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MyActivity";
    private TextView textViewSpeed;
    private TextView textViewMaxSpeed;
    private float maxSpeed;
    private SharedPreferences mSharedPref;
    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textViewSpeed = findViewById(R.id.textViewSpeed);
        textViewMaxSpeed = findViewById(R.id.textViewMaxSpeed);

        mSharedPref = getPreferences(Context.MODE_PRIVATE);

        mFusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        mLocationRequest = new LocationRequest();

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    displayAndCheckMaxSpeed(location.getSpeed());
                }
            }
        };

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

   protected void onStart() {
        super.onStart();
       Log.d(TAG, "onStart");

       maxSpeed = mSharedPref.getFloat(getString(R.string.saved_max_speed), 0f);
       Log.d(TAG, "SharedPref MaxSpeed: " + Float.toString(maxSpeed));
       textViewMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", maxSpeed));
   }

    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(2000L);
    }

    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(30000L);                                       //set interval in milliseconds
    }


    private void getLocation() {
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
            finish(); // terminate the program
        }
    }

    private void displayAndCheckMaxSpeed(float newSpeed) {
        newSpeed = newSpeed * 3600f / 1000f;                                                                        //convert from m/sec to km/hour
        if (newSpeed > maxSpeed) {
            Log.d(TAG, "New maxSpeed: " + Float.toString(maxSpeed));
            maxSpeed = newSpeed;
            SharedPreferences.Editor mEditor = mSharedPref.edit();                                                  //save maximum speed                 //yes, save to shared preferences
            mEditor.clear();
            mEditor.putFloat(getString(R.string.saved_max_speed), maxSpeed);
            mEditor.apply();
            textViewMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", maxSpeed));                         //display new maximum speed
        }
        textViewSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", newSpeed));                                //display speed
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    getLocation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.max_reset) {                                                 //reset maximum speed?
            SharedPreferences.Editor mEditor = mSharedPref.edit();                    //yes, save to shared preferences
            mEditor.clear();
            mEditor.putFloat(getString(R.string.saved_max_speed), 0f);                                   //zero saved maximum speed
            mEditor.apply();
            maxSpeed = 0f;
            textViewMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f)); //zero display of maximum speed
            return true;
        }

        if (id == R.id.quit) {                                                       //request to exit/quit?
            finish();                                                                //yes, terminate the program
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

