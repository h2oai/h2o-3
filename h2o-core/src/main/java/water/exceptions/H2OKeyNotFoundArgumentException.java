package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.Key;
import water.util.IcedHashMap;

/**
 * Exception signalling that a Key was not found.  If the Key name came from an argument, especially from an API parameter,
 * H2OKeyNotFoundArgumentException(String argument, String name) or H2OKeyNotFoundArgumentException(String argument, Key key),
 * which let you specify the argument name.  If not, use H2OKeyNotFoundArgumentException(String argument, String name) or
 * H2OKeyNotFoundArgumentException(String argument, Key key).
 */

public class  H2OKeyNotFoundArgumentException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OKeyNotFoundArgumentException(String argument, String name) {
    super("Key not found: " + argument + ": " + name.toString(),
          "Key not found: " + argument + ": " + name.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(String argument, Key key) {
    this(argument, null == key ? "null" : key.toString());
  }

  public H2OKeyNotFoundArgumentException(String name) {
    super("Key not found: " + name.toString(),
            "Key not found: " + name.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("name", name);
  }

  public H2OKeyNotFoundArgumentException(Key key) {
    this(null == key ? "null" : key.toString());
  }

}
