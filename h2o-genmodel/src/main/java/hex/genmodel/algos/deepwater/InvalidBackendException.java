package hex.genmodel.algos.deepwater;

/**
 * Created by fmilo on 12/22/16.
 */
public class InvalidBackendException extends Exception{
    private final Exception original;

    public InvalidBackendException(String s) {
        super(s);
        original = null;
    }

    public InvalidBackendException(String s, Exception ignored) {
        super(s);
        original = ignored;
    }
}
