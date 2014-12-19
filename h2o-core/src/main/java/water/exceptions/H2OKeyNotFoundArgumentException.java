package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.util.IcedHashMap;

public class H2OKeyNotFoundArgumentException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OKeyNotFoundArgumentException(String argument, String name) {

    super("Key not found: " + argument + ": " + name.toString(),
          "Key not found: " + argument + ": " + name.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("name", name);
  }
}
