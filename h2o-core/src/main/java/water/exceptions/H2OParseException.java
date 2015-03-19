package water.exceptions;

import water.Key;
import water.util.HttpResponseStatus;

/**
 * Exception signalling that a parse failed from bad data.
 * <p>
 */
public class H2OParseException extends H2OAbstractRuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.BAD_REQUEST.getCode(); }

  public H2OParseException(String msg, String dev_msg) {
    super(msg, dev_msg);
  }

  public H2OParseException(Key key, H2OParseException pse) {
    super("Problem parsing "+key.toString()+"\n"+pse.getMessage(), pse.dev_message);
  }

}