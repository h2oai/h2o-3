package hex.genmodel.easy.exception;

/**
 * All generated model exceptions that can occur on the various predict methods derive from this.
 */
public class PredictException extends Exception {
  public PredictException(String message) {
    super(message);
  }

  public PredictException(Throwable cause) {
    super(cause);
  }

  public PredictException(String message, Throwable cause) {
    super(message, cause);
  }
}
