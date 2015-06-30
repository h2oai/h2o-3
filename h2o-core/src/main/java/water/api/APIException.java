package water.api;

/**
 * The exception to report various errors during
 * handling API requests.
 */
abstract public class APIException extends RuntimeException {

  public APIException(String s, Throwable t) {
    super(s,t);
  }
  public APIException(String s) {
    super(s);
  }
}
