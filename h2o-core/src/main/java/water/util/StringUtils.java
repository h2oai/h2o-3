package water.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * String manipulation utilities.
 */
public class StringUtils {

  /**
   * Print exception stack trace into a string.
   *
   * @param t  an exception
   * @return string containing pretty printed exception
   */
  public static String toString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    String stackTrace = sw.toString();
    return stackTrace;
  }
}
