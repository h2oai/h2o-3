package water.util;

import java.util.HashMap;
import java.util.Map;

/**
 */
public class CollectionUtils {

  public static <K, V> Map<K, V> createMap(K[] keys, V[] values) {
    assert keys.length == values.length : "Lenghts of keys and values should be the same";
    Map<K, V> res = new HashMap<>(keys.length);
    for (int i = 0; i < keys.length; i++)
      res.put(keys[i], values[i]);
    return res;
  }

}
