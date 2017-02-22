package hex.genmodel.utils;

import java.io.StringWriter;

public class StringEscapeUtils {

  /**
   * Escapes new line characters of a given string.
   * It also escapes the forward slash characters.
   * @param str string to be escaped
   * @return escaped string
   */
  public static String escapeNewlines(String str) {
    final int len = str.length();
    StringWriter out = new StringWriter(len * 2);
    for (int i = 0; i < len; i++) {
      char c = str.charAt(i);
      switch (c) {
        case '\\':
          out.write('\\');
          out.write('\\');
          break;
        case '\n':
          out.write('\\');
          out.write('n');
          break;
        default:
          out.write(c);
      }
    }
    return out.toString();
  }

  /**
   * Inverse function to {@see escapeNewlines}
   * @param str escaped string
   * @return unescaped
   */
  public static String unescapeNewlines(String str) {
    boolean hadSlash = false;
    final int len = str.length();
    StringWriter out = new StringWriter(len);
    for (int i = 0; i < len; i++) {
      char c = str.charAt(i);
      if (hadSlash) {
        switch (c) {
          case 'n':
            out.write('\n');
            break;
          case '\\':
            out.write('\\');
            break;
          default:
            out.write(c);
        }
        hadSlash = false;
      } else {
        if (c == '\\')
          hadSlash = true;
        else
          out.write(c);
      }
    }
    return out.toString();
  }

}
