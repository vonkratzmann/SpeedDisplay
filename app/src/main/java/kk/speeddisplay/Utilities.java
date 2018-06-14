package kk.speeddisplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Contains useful utilities for speed app, such as:
 *
 * conversion between kilometres per hour and miles per hour
 * checking for a new maximum speed
 */
public final class Utilities {

    /**
     * Speed is stored km/hour in app. Depending on the user's preference,
     * the app may need to display the speed in miles/hour. This method will perform that
     * conversion if necessary and present the speed in the correct format and units
     *
     * @param context    Android Context to access preferences and resources
     * @param speed      Speed in kilometres per hour
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
     * Converts a speed in kilometres/hour (kph) to miles/hour (mph).
     *
     * @param speedInKph Temperature in degrees Celsius(°C)
     * @return speed in miles/hour (mph)
     */
    private static float kphToMph(float speedInKph) {
        return speedInKph * 1.6214F;
    }

    /**
     * Checks the string represents a valid floating point number
     * and its value is greater than zero and less than the largest Long number
     * (why anybody would want such a big number, I don't know)
     *
     * @param textNumber    String which represents a floating point number
     * @return true if strings represents valid float number
     */
    public static boolean checkFloatisPostive(String textNumber) {
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