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

public class UpdateRate {
    private float rate;

    /**
     * getter for stored rate
     *
     * @return rate in milliseconds
     */
    protected long getRateInMilliSecs() {
        return (long) (rate * 1000F);
    }

    /**
     * Returns the default running rate as a long in milliseconds
     * the default rate used is always the activity running rate
     *
     * @return default rate in milliseconds
     */
    protected long getDefaultRunningRateInMilliSecs(Context context) {

        String rate = context.getString(R.string.pref_default_running_rate);
        Float rateFloat = Float.valueOf(rate);

        //convert to milliseconds, before the change to long because the rate can be a decimal
        rateFloat *= 1000F;
        return rateFloat.longValue();
    }

    /**
     * Setter which takes a string and then saves the rate as a float
     *
     * @param rate  new rate
     */
    protected void setRate(String rate) {
        this.rate = Float.parseFloat(rate);
    }

}
