package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.fvec.Frame;
import water.util.IcedHashMap;

/**
 * Exception signalling that an enum (categorical) level was not found.
 */
public class H2OEnumLevelNotFoundArgumentException extends H2ONotFoundArgumentException {

  public H2OEnumLevelNotFoundArgumentException(String argument, String enum_level, String frame_name, String column_name) {
    super("Enum level: " + enum_level + " not found in column_name: " + column_name + " in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString(),
          "Enum level: " + enum_level + " not found in column_name: " + column_name + " in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("enum_level", enum_level);
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }
}
