package water.exceptions;

import water.util.HttpResponseStatus;
import water.util.IcedHashMap;
import water.util.IcedHashMapGeneric;

public class H2OIllegalArgumentException extends H2OAbstractRuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.PRECONDITION_FAILED.getCode(); }

  public H2OIllegalArgumentException(String argument, String function, Object value) {
    super("Illegal argument: " + argument + " of function: " + function + ": " + (value == null ? "null":value.toString()),
          "Illegal argument: " + argument + " of function: " + function + ": " + (value == null ? "null":value.toString()) + " of class: " + (value == null ? "null":value.getClass()));
    this.values = new IcedHashMapGeneric.IcedHashMapStringObject();
    this.values.put("function", function);
    this.values.put("argument", argument);
    if (value!=null) this.values.put("value", value);
  }

  /** Raw-message constructor for use by subclasses. */
  public H2OIllegalArgumentException(String message, String dev_message, IcedHashMapGeneric.IcedHashMapStringObject values) {
    super(message, dev_message, values);
  }

  /** Raw-message constructor for use by subclasses. */
  public H2OIllegalArgumentException(String message, String dev_message) {
    super(message, dev_message);
  }

  /** Raw-message constructor for use by subclasses. */
  public H2OIllegalArgumentException(String message) {
    super(message, message);
  }

  public static H2OIllegalArgumentException wrongKeyType(String fieldName, String keyName, String expectedType, Class actualType) {
    H2OIllegalArgumentException e =
            new H2OIllegalArgumentException(
                  expectedType + " argument: " + fieldName + " with value: " + keyName + " points to a non-" + expectedType + " object: " + actualType.getSimpleName(),
                  expectedType + " argument: " + fieldName + " with value: " + keyName + " points to a non-" + expectedType + " object: " + actualType.getName());
    e.values = new IcedHashMapGeneric.IcedHashMapStringObject();
    e.values.put("argument", fieldName);
    e.values.put("value", keyName);
    e.values.put("expected_type", expectedType);
    e.values.put("actual_type", actualType);
    return e;
  }
}
