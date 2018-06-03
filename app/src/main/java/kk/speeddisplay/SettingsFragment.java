package kk.speeddisplay;


import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;


public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_speed);
    }
}
