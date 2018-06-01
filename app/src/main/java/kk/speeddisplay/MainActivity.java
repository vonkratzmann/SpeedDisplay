package kk.speeddisplay;
/**
 * Speed Display v1.0
 * © 2018 kk
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * MainActivity
 * <p>
 *
 */
public class MainActivity extends AppCompatActivity {

    /* get a tag for output debugging */
    private final static String TAG = MainActivity.class.getSimpleName();

    /* displays current speed from the location provider */
    private TextView tvCurrentSpeed;

    /* displays maximum speed recorded to date, max speed is saved in the shared preferences */
    private TextView tvMaxSpeed;

    private float maxSpeed;
    private SharedPreferences mSharedPref;
    private com.google.android.gms.location.FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    /* set the update intervals at which the app will receive location updates */
    private final static long APP_ACTIVE_UPDATE_RATE = 1000L;
    private final static long APP_NOT_ACTIVE_UPDATE_RATE = 30000L;


    /**
     * Returns an Image object that can then be painted on the screen.
     * The url argument must specify an absolute {@link URL}. The name
     * argument is a specifier that is relative to the url argument.
     * <p>
     * This method always returns immediately, whether or not the
     * image exists. When this applet attempts to draw the image on
     * the screen, the data will be loaded. The graphics primitives
     * that draw the image will incrementally paint on the screen.
     *
     * @param url  an absolute URL giving the base location of the image
     * @param name the location of the image, relative to the url argument
     * @return the image at the specified URL
     * @see Image
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvCurrentSpeed = findViewById(R.id.tv_CurrentSpeed);
        tvMaxSpeed = findViewById(R.id.tv_MaxSpeed);

        mSharedPref = getPreferences(Context.MODE_PRIVATE);

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
                /* process all locations provided */
                for (Location location : locationResult.getLocations()) {
                    displaySpeed(location.getSpeed());
                    checkMaxSpeed(location.getSpeed());
                }
            }
        };
        /* check permissions */
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

    /**
     * request location updates from the fused location provider, which invokes LocationCallback
     */
    private void getLocation() {
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException securityException) {
            Log.e(TAG, getString(R.string.permission_denied));
            finish(); // terminate the program
        }
    }

    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        maxSpeed = mSharedPref.getFloat(getString(R.string.saved_max_speed), 0f);
        Log.d(TAG, "SharedPref MaxSpeed: " + Float.toString(maxSpeed));
        tvMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", maxSpeed));
    }

    /**
     * onResume set the update interval to 2 seconds or 2000 milliseconds
     */
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(APP_ACTIVE_UPDATE_RATE);
        mLocationRequest.setFastestInterval(APP_ACTIVE_UPDATE_RATE);
    }

    /**
     * onPause as not running set the update interval to 30 seconds or 30,000 milliseconds
     * and balance the power
     */
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(APP_NOT_ACTIVE_UPDATE_RATE);
        mLocationRequest.setFastestInterval(APP_NOT_ACTIVE_UPDATE_RATE);
    }


    /**
     * Displays the speed rounded to one decimal place, but first converts from meters per second to kilometers per hour
     *
     * @param newSpeed speed to be displayed in metres per second
     */
    private void displaySpeed(float newSpeed) {
        /* convert from m/sec to km/hour */
        newSpeed = newSpeed * 3600f / 1000f;
        /* display speed in km/hour */
        tvCurrentSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", newSpeed));
    }

    /**
     * Checks if speed above previously stored maximum speed
     * if so save the new maximum speed and display the maximum speed
     *
     * @param newSpeed
     */
    private void checkMaxSpeed(float newSpeed) {
        if (newSpeed > maxSpeed) {
            /* we have a new maximum speed */
            maxSpeed = newSpeed;
            /* save maximum speed to shared preferences */
            SharedPreferences.Editor mEditor = mSharedPref.edit();
            mEditor.clear();
            mEditor.putFloat(getString(R.string.saved_max_speed), maxSpeed);
            mEditor.apply();
            /* display new maximum speed */
            tvMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", maxSpeed));
        }
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
            tvMaxSpeed.setText(String.format(Locale.UK, "%1$.1f km/hr", 0f)); //zero display of maximum speed
            return true;
        }

        if (id == R.id.quit) {                                                       //request to exit/quit?
            finish();                                                                //yes, terminate the program
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

