package water.exceptions;

import water.Key;
import water.util.HttpResponseStatus;

/**
 * Exception signalling that a ParseSetup configuration cannot work.
 * <p>
 */
public class H2OParseSetupException extends H2OParseException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.BAD_REQUEST.getCode(); }

  public H2OParseSetupException(String msg, String dev_msg) {
    super(msg, dev_msg);
  }

  public H2OParseSetupException(String msg) {
    super(msg, msg);
  }
  public H2OParseSetupException(Key key, H2OParseException pe) {
    super("Problem parsing "+key.toString()+"\n"+pe.getMessage(), pe.dev_message);
  }
}