package kk.speeddisplay;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {

    public final static String TAG = "SpeedDisplay" + SettingsFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_speed);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        PreferenceScreen prefScreen = getPreferenceScreen();
        int count = prefScreen.getPreferenceCount();

        // Go through all of the preferences, and set up their preference summary.
        for (int i = 0; i < count; i++) {
            Preference p = prefScreen.getPreference(i);
            // You don't need to set up preference summaries for checkbox preferences because
            // they are already set up in xml using summaryOff and summary On
            if (!(p instanceof CheckBoxPreference)) {
                String value = sharedPreferences.getString(p.getKey(), "");
                setPreferenceSummary(p, value);
            }
        }

        /* Attach the listener to the preferences */
        Preference preference = findPreference(getString(R.string.pref_key_running_update_rate));
        preference.setOnPreferenceChangeListener(this);

        preference = findPreference(getString(R.string.pref_key_not_running_update_rate));
        preference.setOnPreferenceChangeListener(this);
    }

    /**
     * Updates the summary for the preference
     *
     * @param preference The preference to be updated
     * @param value      The value that the preference was updated to
     */
    private void setPreferenceSummary(Preference preference, String value) {
        if (preference instanceof ListPreference) {
            // For list preferences, figure out the label of the selected value
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(value);
            if (prefIndex >= 0) {
                // Set the summary to that label
                listPreference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else if (preference instanceof EditTextPreference) {
            // For EditTextPreferences, set the summary to the value's simple string representation.
            preference.setSummary(value);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        /* Figure out which preference has changed */
        Preference preference = findPreference(key);
        if (null != preference) {
            /* Updates the summary for the preference, ignore checkboxes as do not have summary */
            if (!(preference instanceof CheckBoxPreference)) {
                String value = sharedPreferences.getString(preference.getKey(), "");
                setPreferenceSummary(preference, value);
            }
        }
    }

    /**
     * On change, checks valid floating point number has been entered for update rate fields
     *
     * @param preference preference which generated the change
     * @param newValue   new value entered
     * @return true if number valid, otherwise false
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Toast error = Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT);

        String runningRateKey = getString(R.string.pref_key_running_update_rate);
        String notRunningRateKey = getString(R.string.pref_key_not_running_update_rate);

        if (preference.getKey().equals(runningRateKey) || preference.getKey().equals(notRunningRateKey)) {
            String rate = (String) newValue;
            /* check valid entry */
            if (Utilities.checkFloatIsPostive(rate)) {
                return true;
            } else {
                error.show();
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }


}