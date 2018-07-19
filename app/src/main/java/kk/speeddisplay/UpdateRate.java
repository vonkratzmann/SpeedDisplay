package kk.speeddisplay;

/* Stores update interval at which the activity will receive location updates,
 * these are saved in the shared preferences as a string representing seconds
 * as the rate has to be positive, greater than zero and can be a decimal.
 * Can be changed by the user in settings.
 * If changed by the user or if the activity state changes,
 * the appropriate rate sent to the service
 * When passed to the service to set the update rate in the location provider,
 * they must be a long and in milliseconds.
 * Here stored as a float;
 */

import android.content.Context;
import android.util.Log;

public class UpdateRate {
    private final static String TAG = UpdateRate.class.getSimpleName();

    private float rate;

    /**
     * getter for stored rate
     *
     * @return rate in milliseconds
     */
    protected long getRateInMilliSecs() {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getRateInMilliSecs()");

        return (long) (rate * 1000F);
    }

    /**
     * Returns the default running rate as a long in milliseconds
     *
     * @return default rate in milliseconds
     */
    protected long getDefaultRunningRateInMilliSecs(Context context) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "getDefaultRunningRateInMilliSecs()");

        String rate = context.getString(R.string.pref_default_running_rate);
        Float rateFloat = Float.valueOf(rate);

        //convert to milliseconds, before the change to long because the rate can be a decimal
        rateFloat *= 1000F;
        return rateFloat.longValue();
    }

    /**
     * Setter which takes a string and then saves the rate as a float
     *
     * @param rate new rate
     */
    protected void setRate(String rate) {
        if (MyDebug.DEBUG_METHOD_ENTRY) Log.d(TAG, "setRate()");

        this.rate = Float.parseFloat(rate);
    }

}
