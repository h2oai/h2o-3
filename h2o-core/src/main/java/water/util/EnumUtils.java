package water.util;

import water.H2O;

import java.util.Arrays;
import java.util.HashMap;

/** Utilities to deal with Java enums. */
public class EnumUtils {

  // Helper for .valueOf()
  private static HashMap<Class<? extends Enum>, HashMap<String, Enum>> enumMappings = new HashMap<>();

  /**
   * Return an array of Strings of all the enum levels.
   * <p>
   * Taken from http://stackoverflow.com/questions/13783295/getting-all-names-in-an-enum-as-a-string.
   */
  public static String[] getNames(Class<? extends Enum<?>> e) {
    return Arrays.toString(e.getEnumConstants()).replaceAll("^.|.$", "").split(", ");
  }

  /**
   * This is like Enum.valueOf() only better: it matches enum constants very loosely: case-insensitive and disregarding
   * any non-alphanumeric characters (e.g. "_"). For example, if Enum declares constant LOG_NORMAL, then all of the
   * following would also match to this constant:
   *     log_normal, logNormal, LogNormal, __LoGnOrmaL___, "LogNormal", $Log.Normal, ツlognormal, etc.
   *
   * @param <T> The enum type whose constant is to be returned
   * @param clz the {@code Class} object of the enum type from which to return a constant
   * @param name the name of the constant to return
   * @return the enum constant of the specified enum type with the specified name
   */
  public static <T extends Enum<T>> T valueOf(Class<T> clz, String name) {
    HashMap<String, Enum> map = enumMappings.get(clz);
    if (map == null) {
      map = new HashMap<>();
      enumMappings.put(clz, map);
      for (Enum item : clz.getEnumConstants()) {
        String origName = item.name();
        String unifName = origName.toUpperCase().replaceAll("[^0-9A-Z]", "");
        if (map.containsKey(origName)) throw H2O.fail("Unexpected key " + origName + " in enum " + clz);
        if (map.containsKey(unifName)) throw H2O.fail("Non-unique key " + unifName + " in enum " + clz);
        map.put(origName, item);
        map.put(unifName, item);
      }
    }

    Enum value = map.get(name);
    if (value == null && name != null)
      value = map.get(name.toUpperCase().replaceAll("[^0-9A-Z]", ""));

    if (value == null)
      throw new IllegalArgumentException("No enum constant " + clz.getCanonicalName() + "." + name);
    if (name == null)
      throw new NullPointerException("Name is null");

    // noinspection unchecked
    return (T) value;
  }

}
