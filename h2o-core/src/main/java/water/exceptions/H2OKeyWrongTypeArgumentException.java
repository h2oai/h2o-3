package water.exceptions;

import water.util.HttpResponseStatus;
import water.Keyed;
import water.util.IcedHashMap;

public class H2OKeyWrongTypeArgumentException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OKeyWrongTypeArgumentException(String argument, Object value, Class<? extends Keyed> expected, Class actual) {

    super("Expected a " + expected.getSimpleName() + " for key argument: " + argument + " with value: " + value + ".  Found a: " + actual.getSimpleName(),
          "Expected a " + expected.getCanonicalName() + " for key argument: " + argument + " with value: " + value + ".  Found a: " + actual.getCanonicalName());
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("argument", argument);
    this.values.put("value", value);
    this.values.put("expected_type", expected.getCanonicalName());
    this.values.put("actual_type", actual.getCanonicalName());
  }
}
