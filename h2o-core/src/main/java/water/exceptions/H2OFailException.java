package water.exceptions;

import water.H2OError;
import water.util.HttpResponseStatus;

/**
 * RuntimeException which causes H2O to shut down.  This should only be used for cases in
 * which the code is bad, for example because a case isn't covered which must be for the
 * product to function correctly, and which therefore should be caught quickly in the
 * development process.
 */
public class H2OFailException extends H2OAbstractRuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.INTERNAL_SERVER_ERROR.getCode(); }

  public H2OFailException(String message) {
    super(message, message);

    this.timestamp = System.currentTimeMillis();
  }

  public H2OFailException(String msg, Throwable cause) {
    this(msg);
    this.initCause(cause);
  }

  public H2OError toH2OError() {
    return new H2OError(timestamp, null, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, this);
  }

  public H2OError toH2OError(String error_url) {
    return new H2OError(timestamp, error_url, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, this);
  }
}
