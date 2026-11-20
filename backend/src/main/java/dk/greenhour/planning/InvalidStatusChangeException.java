package dk.greenhour.planning;

public class InvalidStatusChangeException extends RuntimeException {

    private final String code;

    public InvalidStatusChangeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
