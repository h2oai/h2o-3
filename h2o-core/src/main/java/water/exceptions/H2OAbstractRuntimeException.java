package water.exceptions;

import water.util.HttpResponseStatus;
import water.H2OError;
import water.util.IcedHashMapGeneric;

/**
 * RuntimeException which results in a http 400 error by default, and serves as a base class for other error types.
 * Note that the HTTP error status can be overridden when converting the exception to {@link H2OError}
 * when overriding {@link #toH2OError(String)}.
 * 
 * NOTE: don't use this directly; use more specific types.
 */
abstract public class H2OAbstractRuntimeException extends RuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.BAD_REQUEST.getCode(); }

  public long timestamp;
  public String dev_message;
  public IcedHashMapGeneric.IcedHashMapStringObject values;

  public H2OAbstractRuntimeException(String message, String dev_message, IcedHashMapGeneric.IcedHashMapStringObject values) {
    super(message);

    this.timestamp = System.currentTimeMillis();
    this.dev_message = dev_message;
    this.values = values;
  }

  public H2OAbstractRuntimeException(String msg, String dev_msg) {
    this(msg, dev_msg, new IcedHashMapGeneric.IcedHashMapStringObject());
  }

  public H2OError toH2OError() {
    return toH2OError(null);
  }

  public H2OError toH2OError(String error_url) {
    return new H2OError(timestamp, error_url, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, this);
  }
}
