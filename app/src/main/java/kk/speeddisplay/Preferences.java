package kk.speeddisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

public class Preferences {

    /* get a tag for output debugging */
    private final static String TAG = Preferences.class.getSimpleName();

    /**
     * Gets rate at which location provider provides updates when the activity is running
     * gets the new rate from the share preferences
     *
     * @param context context used to get the shared preferences
     * @return location update rate for when activity is running in seconds
     */
    protected static String getPrefRunningRate(Context context) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getPrefRunningRate()");

        SharedPreferences prefs = android.preference.PreferenceManager.
                getDefaultSharedPreferences(context);
        // get rate
        String key = context.getString(R.string.pref_key_running_update_rate);
        String defaultRate = context.getString(R.string.pref_default_running_rate);
        String rate = prefs.getString(key, defaultRate);

        //Log.d(TAG, "updatePrefRunningRate rate: " + rate);
        return rate;
    }

    /**
     * Gets rate at which location provider provides updates when the activity is not running
     * gets the new rate fom the share preferences
     *
     * @param context context used to get the shared preferences
     * @return location update rate for when activity is not running in seconds
     */
    protected static String getPrefNotRunningRate(Context context) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getPrefNotRunningRate()");

        SharedPreferences prefs = android.preference.PreferenceManager.
                getDefaultSharedPreferences(context);
        // get rate
        String key = context.getString(R.string.pref_key_not_running_update_rate);
        String defaultRate = context.getString(R.string.pref_default_not_running_rate);
        String rate = prefs.getString(key, defaultRate);

        //Log.d(TAG, "getPrefNotRunningRate rate: " + rate);
        return rate;
    }

    /**
     * Returns true if the user has selected metric speed display.
     *
     * @param context Context used to get the SharedPreferences
     * @return true if metric display should be used
     */
    protected static boolean isMetric(Context context) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "isMetric()");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String keyForUnits = context.getString(R.string.pref_key_units);
        String defaultUnits = context.getString(R.string.pref_value_units_metric);

        String preferredUnits = prefs.getString(keyForUnits, defaultUnits);
        String metric = context.getString(R.string.pref_value_units_metric);
        return metric.equals(preferredUnits);
    }

    /**
     * Retrieves saved maximum speed from Shared preferences
     *
     * @param context Context used to get the SharedPreferences
     * @return maximum speed
     */
    protected static float getPrefMaxSpeed(Context context) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getPrefMaxSpeed()");

        SharedPreferences prefs = android.preference.PreferenceManager.
                getDefaultSharedPreferences(context);
        String key = context.getString(R.string.pref_key_saved_max_speed);
        float maxSpeed = prefs.getFloat(key, 0.0F);
        //Log.d(TAG, "updatePrefMaxSpeed mMaxSpeed: " + Float.toString(maxSpeed));
        return maxSpeed;
    }
}
