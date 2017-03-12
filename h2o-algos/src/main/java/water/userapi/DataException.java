package water.userapi;

/**
 * Wrapper for all kinds of H2O and IO exceptions
 * 
 * Created by vpatryshev on 2/20/17.
 */
public class DataException extends RuntimeException {
  final Exception cause;
  final String message;
  
  public DataException(String message, Exception cause) {
    this.message = message;
    this.cause = cause;
  }

  public DataException(Exception cause) {
    this.message = cause.getMessage();
    this.cause = cause;
  }

  public DataException(String message) {
    this.message = message;
    this.cause = null;
  }
  
  
}
