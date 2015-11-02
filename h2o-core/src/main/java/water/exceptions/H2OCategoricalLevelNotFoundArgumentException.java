package water.exceptions;

import water.util.IcedHashMap;

/**
 * Exception signalling that an categorical level was not found.
 */
public class H2OCategoricalLevelNotFoundArgumentException extends H2ONotFoundArgumentException {

  public H2OCategoricalLevelNotFoundArgumentException(String argument, String categorical_level, String frame_name, String column_name) {
    super("Categorical level: " + categorical_level + " not found in column_name: " + column_name + " in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString(),
          "Categorical level: " + categorical_level + " not found in column_name: " + column_name + " in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString());
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("argument", argument);
    this.values.put("categorical_level", categorical_level);
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }
}
