package kk.speeddisplay;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MyActivity";
    private Button button;
    private TextView textViewSpeed;
    private TextView textViewMaxSpeed;
    private float maxSpeed = 0f;
    private SharedPreferences sharedPref;
    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textViewSpeed = findViewById(R.id.textViewSpeed);
        textViewMaxSpeed = findViewById(R.id.textViewMaxSpeed);

        sharedPref = MainActivity.this.getPreferences(Context.MODE_PRIVATE);

        float maxSpeed = sharedPref.getFloat(getString(R.string.savedMaxSpeed), 0f);
        textViewMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", maxSpeed));

        mFusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

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

    private void getLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new com.google.android.gms.tasks.OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                displayCheckMaxSpeed(location.getSpeed());
                            }
                        }
                    });
        } catch (SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
            System.exit(1); // terminate the program
        }
    }
    private void displayCheckMaxSpeed(float newSpeed) {
        if (newSpeed > maxSpeed) {
            maxSpeed = newSpeed;
            SharedPreferences.Editor editor = sharedPref.edit();                                                  //save maximum speed
            editor.putFloat(getString(R.string.savedMaxSpeed), maxSpeed);
            editor.apply();
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
            SharedPreferences.Editor editor = sharedPref.edit();                    //yes, save to shared preferences
            editor.putFloat(getString(R.string.savedMaxSpeed), 0);                  //zero saved maximum speed
            editor.apply();
            textViewMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f)); //zero display of maximum speed
            return true;
        }

        if (id == R.id.quit) {                                                       //request to exit/quit?
            System.exit(1);                                                          //yes, terminate the program
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

