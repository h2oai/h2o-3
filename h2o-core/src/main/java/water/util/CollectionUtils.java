package water.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class CollectionUtils {

  public static <K, V> Map<K, V> createMap(K[] keys, V[] values) {
    assert keys.length == values.length : "Lengths of keys and values should be the same";
    Map<K, V> res = new HashMap<>(keys.length);
    for (int i = 0; i < keys.length; i++)
      res.put(keys[i], values[i]);
    return res;
  }

  /** Convert a Collection of Bytes to a primitive array byte[]. */
  public static byte[] unboxBytes(Collection<Byte> coll) {
    byte[] res = new byte[coll.size()];
    int i = 0;
    for (Byte elem : coll)
      res[i++] = elem;
    return res;
  }

  /** Convert a Collection of Strings to a plain array String[]. */
  public static String[] unboxStrings(Collection<String> coll) {
    return coll.toArray(new String[coll.size()]);
  }

  /** Convert a Collection of Strings[] to a plain array String[][]. */
  public static String[][] unboxStringArrays(Collection<String[]> coll) {
    return coll.toArray(new String[coll.size()][]);
  }
}
