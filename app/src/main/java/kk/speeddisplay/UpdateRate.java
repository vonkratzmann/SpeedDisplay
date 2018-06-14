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

public class UpdateRate {
    private float rate;

    protected long getRateInMilliSecs() {
        return (long) (rate * 1000F);
    }

    public void setmRate(String rate) {
       this.rate = Float.parseFloat(rate);
    }
}
