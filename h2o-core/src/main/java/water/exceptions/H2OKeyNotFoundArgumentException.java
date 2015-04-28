package water.exceptions;

import water.Key;
import water.util.IcedHashMap;

/**
 * Exception signalling that a Key was not found.
 * <p>
 * If the Key name came from an argument, especially from an API parameter, use
 * {@code H2OKeyNotFoundArgumentException(String argument, String name)} or {@code H2OKeyNotFoundArgumentException(String argument, Key key)},
 * which let you specify the argument name.  If not, use {@code H2OKeyNotFoundArgumentException(String argument, String name)} or
 * {@code H2OKeyNotFoundArgumentException(String argument, Key key)}.
 */

public class  H2OKeyNotFoundArgumentException extends H2ONotFoundArgumentException {
  public H2OKeyNotFoundArgumentException(String argument, String function, String name) {
    super("Object '" + name.toString() + "' not found in function: " + function + " for argument: " + argument,
            "Object '" + name.toString() + "' not found in function: " + function + " for argument: " + argument);
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("function", function);
    this.values.put("argument", argument);
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(String argument, String name) {
    super("Object '" + name.toString() + "' not found for argument: " + argument,
            "Object '" + name.toString() + "' not found for argument: " + argument);
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("argument", argument);
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(String argument, Key key) {
    this(argument, null == key ? "null" : key.toString());
  }

  public H2OKeyNotFoundArgumentException(String name) {
    super("Object not found: " + name.toString(),
          "Object not found: " + name.toString());
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(Key key) {
    this(null == key ? "null" : key.toString());
  }

}
