package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.Key;
import water.util.IcedHashMap;

public class H2OKeyNotFoundException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OKeyNotFoundException(String name) {
    super("Key not found: " + name.toString(),
          "Key not found: " + name.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("name", name);
  }

  public H2OKeyNotFoundException(Key key) {
    this(null == key ? "null" : key.toString());
  }
}
