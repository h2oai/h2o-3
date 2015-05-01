package water.util;

import java.util.Map;
import java.util.TreeMap;

/** Iced / Freezable Sorted HashMap.  Delegates to a TreeMap for
 *  all its operations.
 */
public class IcedSortedHashMap<K, V> extends IcedHashMapBase<K,V> {
  transient TreeMap<K,V> _map;
  public IcedSortedHashMap() { init(); }
  @Override protected Map<K, V> map() { return _map; }
  @Override protected Map<K, V> init() { return _map = new TreeMap<>(); }
}
