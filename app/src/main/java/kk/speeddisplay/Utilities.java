package kk.speeddisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import java.util.Locale;

/**
 * Contains useful utilities for speed app, such as:
 * <p>
 * conversion between kilometres per hour and miles per hour
 * checking for a new maximum speed
 * spannable string
 */
public final class Utilities {

    private final static String TAG = "SpeedDisplay" + Utilities.class.getSimpleName();

    /**
     * Speed is stored km/hour in app. Depending on the user's preference,
     * the app may need to display the speed in miles/hour. This method will perform that
     * conversion if necessary and present the speed in the correct format and units
     *
     * @param context Android Context to access preferences and resources
     * @param speed   Speed in kilometres per hour
     * @return Formatted speed
     */
    public static String formatSpeed(Context context, float speed) {
        //get the default format to display speed
        int speedFormatResourceId = R.string.speed_format_kph;

        if (!Preferences.isMetric(context)) {
            speed = kphToMph(speed);
            speedFormatResourceId = R.string.speed_format_mph;
        }
        return String.format(Locale.UK, context.getString(speedFormatResourceId), speed);
    }

    /**
     * Changes the text size of the units to half the size of the speed value.
     * The units text size is set to half of the speed
     *
     * @param speed    speed and units to be displayed in a single text size eg "10.0 km/h"
     * @return         speed and units to be displayed with units in a smaller text size
     */

    public static SpannableString spanSpeed(String speed) {
        //find the start of the units
        int start = speed.indexOf(' ');
        int end = speed.length();
        SpannableString ss = new SpannableString(speed);
        ss.setSpan(new RelativeSizeSpan(.5f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //Log.d(TAG, "ss: " + ss.toString() + " " + start + " " + end);
        return ss;
    }

    /**
     * Converts a speed in kilometres/hour (kph) to miles/hour (mph).
     *
     * @param speedInKph Temperature in degrees Celsius(°C)
     * @return speed in miles/hour (mph)
     */
    private static float kphToMph(float speedInKph) {
        return speedInKph * .6214F;
    }

    /**
     * Checks the string represents a valid floating point number
     * and its value is greater than zero and less than the largest Long number
     * (why anybody would want such a big number, I don't know)
     *
     * @param textNumber String which represents a floating point number
     * @return true if strings represents valid float number
     */
    public static boolean checkFloatIsPostive(String textNumber) {
        try {
            Float number = Float.valueOf(textNumber);
            if (number <= 0 || number >= Long.MAX_VALUE) {
                return false;
            }
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    /**
     * Checks if speed above previously maximum speed
     * if so save the new maximum speed in the preferences
     * always return the maximum speed, either the old maximum or the new maximum
     *
     * @param speed    latest speed from the location provider
     * @param maxSpeed current maximum speed
     * @return float    always return the maximum speed
     */
    public float checkMaxSpeed(Context context, float speed, float maxSpeed) {
        if (speed > maxSpeed) {
            /* we have a new maximum speed */
            //Log.d(TAG, "checkMaxSpeed new maximum: " + speed);
            /* save new maximum speed to shared preferences */
            saveMaxSpeed(context, speed);
            // return the new maximum speed
            return speed;
        }
        return maxSpeed;
    }

    /**
     * save maximum speed to shared preferences
     *
     * @param maxSpeed maximum speed to be saved
     */
    public void saveMaxSpeed(Context context, float maxSpeed) {
        //Log.d(TAG, "saveMaxSpeed maxSpeed: " + maxSpeed);
        /* save maximum speed to shared preferences */
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor mEditor = mSharedPref.edit();
        mEditor.clear().putFloat(context.getString(R.string.pref_key_saved_max_speed), maxSpeed).apply();
    }
}