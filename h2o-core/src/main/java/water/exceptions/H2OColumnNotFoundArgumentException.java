package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.util.IcedHashMap;

/**
 * Exception signalling that a Key was not found.  If the Key name came from an argument, especially from an API parameter,
 * H2OKeyNotFoundArgumentException(String argument, String name) or H2OKeyNotFoundArgumentException(String argument, Key key),
 * which let you specify the argument name.  If not, use H2OKeyNotFoundArgumentException(String argument, String name) or
 * H2OKeyNotFoundArgumentException(String argument, Key key).
 */

public class  H2OColumnNotFoundArgumentException extends H2OIllegalArgumentException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2OColumnNotFoundArgumentException(String argument, String frame_name, String column_name) {
    super("Column: " + column_name + " not found in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString(),
          "Column: " + column_name + " not found in frame: " + frame_name + " from argument: " + argument + ": " + argument.toString());
    this.values = new IcedHashMap<String, Object>();
    this.values.put("argument", argument);
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }

  public H2OColumnNotFoundArgumentException(String frame_name, String column_name) {
    super("Column: " + column_name + " not found in frame: " + frame_name + ".",
          "Column: " + column_name + " not found in frame: " + frame_name + ".");
    this.values = new IcedHashMap<String, Object>();
    this.values.put("frame_name", frame_name);
    this.values.put("column_name", column_name);
  }

}
