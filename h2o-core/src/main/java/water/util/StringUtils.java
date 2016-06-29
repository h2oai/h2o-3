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
    return sw.toString();
  }

  /**
   * Convenience function to test whether a string is empty.
   * @param s String to test
   * @return True if the string is either null or empty, false otherwise
   */
  public static boolean isNullOrEmpty(String s) {
    return s == null || s.length() == 0;
  }

}
