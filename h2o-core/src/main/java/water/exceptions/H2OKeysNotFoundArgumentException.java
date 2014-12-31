package water.exceptions;

import water.util.IcedHashMap;

public class H2OKeysNotFoundArgumentException extends H2ONotFoundArgumentException {

  public H2OKeysNotFoundArgumentException(String argument, String[] names) {
    super("Keys not found: " + argument + ": " + names.toString(),
            "Key not found: " + argument + ": " + names.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("names", names);
  }
}
