package kk.speeddisplay;

import android.content.Context;
import android.content.SharedPreferences;
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

    public static SpannableString spanSpeed(String speed) {

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
}