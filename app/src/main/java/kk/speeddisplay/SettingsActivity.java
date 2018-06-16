package kk.speeddisplay;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.util.Log;
import android.view.MenuItem;

public class SettingsActivity extends AppCompatActivity {

    private final static String TAG = "SpeedDisplay" + SettingsActivity.class.getSimpleName();

    /* use to suppress error for line 'actionBar.setDefaultDisplayHomeAsUpEnabled(true);' */
    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ActionBar actionBar = this.getSupportActionBar();

        // Set the action bar back button to look like an up button
        if (actionBar != null) {

            actionBar.setDefaultDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // When the home button is pressed, take the user back to the VisualizerActivity
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }

//    @Override
//    public boolean onPreferenceClick(Preference preference)
//    {
//        Log.d(TAG, "onPreferenceClick");
//        EditTextPreference editPref = (EditTextPreference) preference;
//        editPref.getEditText().setSelection(editPref.getText().length());
//        return true;
//    }


}

