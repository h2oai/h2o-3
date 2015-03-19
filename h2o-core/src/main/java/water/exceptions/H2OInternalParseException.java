package water.exceptions;

import water.util.HttpResponseStatus;

/**
 * Exception signalling that something unexpected happened in ParseSetup or Parse,
 * beyond expected user errors.  Used to catch conditions that should not be encountered.
 * <p>
 */
public class H2OInternalParseException extends H2OParseException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.INTERNAL_SERVER_ERROR.getCode(); }

  public H2OInternalParseException(String msg, String dev_msg) {
    super(msg, dev_msg);
  }
}