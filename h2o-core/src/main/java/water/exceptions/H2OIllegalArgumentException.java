package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.util.IcedHashMap;

public class H2OIllegalArgumentException extends H2OAbstractRuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.PRECONDITION_FAILED.getCode(); }

  public H2OIllegalArgumentException(String argument, String function, Object value) {
    super("Illegal argument: " + argument + " of function: " + function + ": " + value.toString(),
          "Illegal argument: " + argument + " of function: " + function + ": " + value.toString() + " of class: " + value.getClass());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("function", function);
    this.values.put("argument", argument);
    this.values.put("value", value);
  }

  /** Raw-message constructor for use by subclasses. */
  public H2OIllegalArgumentException(String message, String dev_message, IcedHashMap values) {
    super(message, dev_message, values);
  }

  /** Raw-message constructor for use by subclasses. */
  public H2OIllegalArgumentException(String message, String dev_message) {
    super(message, dev_message);
  }
}
