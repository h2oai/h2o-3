package water.util;

import water.H2O;
import water.nbhm.NonBlockingHashMap;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Utilities to deal with Java enums.
 */
public class EnumUtils {

  /**
   * Memoizer for {@link #valueOf(Class, String)}
   */
  private static NonBlockingHashMap<Class<? extends Enum>, NonBlockingHashMap<String, Enum>>
      enumMappings = new NonBlockingHashMap<>(150);

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
   *     log_normal, logNormal, LogNormal, __LoGnOrmaL___, "LogNormal", $Log.Normal, lognormal, etc.
   *
   * @param <T>  The enum type whose constant is to be returned
   * @param clz  the {@code Class} object of the enum type from which to return a constant
   * @param name the name of the constant to return
   * @return the enum constant of the specified enum type with the specified name
   */
  public static <T extends Enum<T>> T valueOf(Class<T> clz, String name) {
    NonBlockingHashMap<String, Enum> map = enumMappings.get(clz);
    if (map == null) {
      T[] enumValues = clz.getEnumConstants();
      map = new NonBlockingHashMap<>(enumValues.length * 2);
      for (Enum item : enumValues) {
        String origName = item.name();
        String unifName = origName.toUpperCase().replaceAll("[^0-9A-Z]", "");
        if (map.containsKey(origName)) throw H2O.fail("Unexpected key " + origName + " in enum " + clz);
        if (map.containsKey(unifName)) throw H2O.fail("Non-unique key " + unifName + " in enum " + clz);
        map.put(origName, item);
        map.put(unifName, item);
      }
      // Put the map into {enumMappings} no sooner than it is fully constructed. If there are multiple threads
      // accessing the same enum mapping, then it is possible they'll begin constructing the map simultaneously and
      // then overwrite each other's results. This is harmless.
      // However it would be an error to put the {map} into {enumMappings} before it is filled, because then the
      // other thread would think that the map is complete, and may not find some of the legitimate keys.
      enumMappings.put(clz, map);
    }

    Enum value = map.get(name);
    if (value == null && name != null) {
      String unifName = name.toUpperCase().replaceAll("[^0-9A-Z]", "");
      value = map.get(unifName);
      // Save the mapping name -> value, so that subsequent requests with the same name will be faster.
      if (value != null)
        map.put(name, value);
    }

    if (value == null)
      throw new IllegalArgumentException("No enum constant " + clz.getCanonicalName() + "." + name);
    if (name == null)
      throw new NullPointerException("Name is null");

    // noinspection unchecked
    return (T) value;
  }

  /**
   *
   * @param enumeration Enumeration to search in
   * @param value String to search fore
   * @param <T> Class of the Enumeration to search in
   * @return
   */
  public static <T extends Enum<?>> T valueOfIgnoreCase(Class<T> enumeration,
                                                 String value) {
    for (T field : enumeration.getEnumConstants()) {
      if (field.name().compareToIgnoreCase(value) == 0) {
        return field;
      }
    }
    return null;
  }

}
