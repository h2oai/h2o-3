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
  public H2OKeyNotFoundArgumentException(String argument, String name) {
    super("Key not found: " + argument + ": " + name.toString(),
          "Key not found: " + argument + ": " + name.toString());
    this.values = new IcedHashMap<>();
    this.values.put("argument", argument);
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(String argument, Key key) {
    this(argument, null == key ? "null" : key.toString());
  }

  public H2OKeyNotFoundArgumentException(String name) {
    super("Key not found: " + name.toString(),
          "Key not found: " + name.toString());
    this.values = new IcedHashMap<>();
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(Key key) {
    this(null == key ? "null" : key.toString());
  }

}
