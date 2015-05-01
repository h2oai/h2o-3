package water.util;

import water.nbhm.NonBlockingHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/** Iced / Freezable NonBlockingHashMap.  Delegates to a NonBlockingHashMap for
 *  all its operations.  Inspired by water.parser.Categorical.
 */
public class IcedHashMap<K, V> extends IcedHashMapBase<K,V> implements ConcurrentMap<K, V> {
  transient NonBlockingHashMap<K,V> _map;
  public IcedHashMap() { init(); }
  @Override protected Map<K, V> map() { return _map; }
  @Override protected Map<K, V> init() { return _map = new NonBlockingHashMap<>(); }

  public V putIfAbsent(K key, V value)                  { return _map.putIfAbsent(key, value); }
  public boolean remove(Object key, Object value)       { return _map.remove(key, value);  }
  public boolean replace(K key, V oldValue, V newValue) { return _map.replace(key, oldValue, newValue); }
  public V replace(K key, V value)                      { return _map.replace(key, value); }

  // Subtypes which allow us to determine the type parameters at runtime, for generating schema metadata.
  public static class IcedHashMapStringString extends IcedHashMap<String, String> {}
  public static class IcedHashMapStringObject extends IcedHashMap<String, Object> {}
}
