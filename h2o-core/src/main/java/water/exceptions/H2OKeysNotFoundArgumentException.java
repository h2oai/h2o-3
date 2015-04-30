package water.exceptions;

import water.util.IcedHashMap;

public class H2OKeysNotFoundArgumentException extends H2ONotFoundArgumentException {

  public H2OKeysNotFoundArgumentException(String argument, String[] names) {
    super("Objects not found: " + argument + ": " + names.toString(),
            "Objects not found: " + argument + ": " + names.toString());
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("argument", argument);
    this.values.put("names", names);
  }
}
