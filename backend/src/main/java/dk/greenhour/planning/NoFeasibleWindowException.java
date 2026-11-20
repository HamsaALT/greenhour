package dk.greenhour.planning;

import java.util.Map;

/**
 * No start time works inside the requested window. The code tells clients which
 * case it is so they can phrase it in the user's language; the message is for logs
 * and API users.
 */
public class NoFeasibleWindowException extends RuntimeException {

    public enum Reason {
        WINDOW_TOO_SHORT,
        PRICES_NOT_PUBLISHED,
        CO2_FORECAST_MISSING
    }

    private final Reason reason;
    private final Map<String, Object> params;

    public NoFeasibleWindowException(Reason reason, String message, Map<String, Object> params) {
        super(message);
        this.reason = reason;
        this.params = Map.copyOf(params);
    }

    public Reason reason() {
        return reason;
    }

    public Map<String, Object> params() {
        return params;
    }
}
