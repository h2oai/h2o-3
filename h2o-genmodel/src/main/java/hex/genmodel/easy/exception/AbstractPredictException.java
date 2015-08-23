package hex.genmodel.easy.exception;

/**
 * All generated model exceptions that can occur on the various predict methods derive from this.
 */
public abstract class AbstractPredictException extends Exception {
  public AbstractPredictException(String message) {
    super(message);
  }
}
