package water.util;

import java.util.Arrays;

/** Utilities to deal with Java enums. */
public class EnumUtils {
  /**
   * Return an array of Strings of all the enum levels.
   * <p>
   * Taken from http://stackoverflow.com/questions/13783295/getting-all-names-in-an-enum-as-a-string.
   */
  public static String[] getNames(Class<? extends Enum<?>> e) {
    return Arrays.toString(e.getEnumConstants()).replaceAll("^.|.$", "").split(", ");
  }
}
