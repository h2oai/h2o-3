package water.exceptions;

import water.fvec.Frame;
import water.util.IcedHashMap;

/**
 * Exception signalling that a Vec was not found.
 * <p>
 * If the Vec name came from an argument, especially from an API parameter, use
 * {@code H2OColumnNotFoundArgumentException(String argument, Frame frame, String column_name)} or
 * {@code H2OColumnNotFoundArgumentException(String argument, String frame_name, String column_name)},
 * which let you specify the argument name.  If not, use
 * {@code H2OColumnNotFoundArgumentException(Frame frame, String column_name)} or
 * {@code H2OColumnNotFoundArgumentException(String frame_name, String column_name)}.
 */
public class H2OColumnNotFoundArgumentException extends H2ONotFoundArgumentException {

  public H2OColumnNotFoundArgumentException(String argument, Frame frame, String column_name) {
    this(argument, (null == frame._key ? "null" : frame._key.toString()), column_name);
  }

  public H2OColumnNotFoundArgumentException(String argument, String frame_name, String column_name) {
    super("Column: " + column_name + " not found in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString(),
          "Column: " + column_name + " not found in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString());
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("argument", argument);
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }

  public H2OColumnNotFoundArgumentException(Frame frame, String column_name) {
    this((null == frame._key ? "null" : frame._key.toString()), column_name);
  }

  public H2OColumnNotFoundArgumentException(String frame_name, String column_name) {
    super("Column: " + column_name + " not found in frame: " + frame_name + ".",
          "Column: " + column_name + " not found in frame: " + frame_name + ".");
    this.values = new IcedHashMap.IcedHashMapStringObject();
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }
}
