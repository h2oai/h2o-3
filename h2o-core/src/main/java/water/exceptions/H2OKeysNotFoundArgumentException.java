package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.util.IcedHashMap;

public class H2OKeysNotFoundArgumentException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OKeysNotFoundArgumentException(String argument, String[] names) {
    super("Keys not found: " + argument + ": " + names.toString(),
            "Key not found: " + argument + ": " + names.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("names", names);
  }
}
