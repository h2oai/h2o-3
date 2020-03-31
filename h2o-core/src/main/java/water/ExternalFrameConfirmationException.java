package water;

/**
 * Exception used for reporting failure during write/read confirmation in external mode
 */
public class ExternalFrameConfirmationException extends Exception {
    public ExternalFrameConfirmationException(String message) {
        super(message);
    }
}
