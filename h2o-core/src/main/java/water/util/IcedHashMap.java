package water.util;

import water.AutoBuffer;
import water.nbhm.NonBlockingHashMap;

import java.util.concurrent.ConcurrentMap;

/** Iced / Freezable NonBlockingHashMap.  Delegates to a NonBlockingHashMap for
 *  all its operations.  Inspired by water.parser.Categorical.
 */
public class IcedHashMap<K, V> extends IcedHashMapBase<K,V> implements ConcurrentMap<K, V> {
  public IcedHashMap() { _map = new NonBlockingHashMap<>(); }
  public V putIfAbsent(K key, V value)                  { return (V)((NonBlockingHashMap)_map).putIfAbsent(key, value); }
  public boolean remove(Object key, Object value)       { return ((NonBlockingHashMap)_map).remove(key, value);  }
  public boolean replace(K key, V oldValue, V newValue) { return ((NonBlockingHashMap)_map).replace(key, oldValue, newValue); }
  public V replace(K key, V value)                      { return (V)((NonBlockingHashMap)_map).replace(key, value); }

  @Override public IcedHashMap read_impl( AutoBuffer ab ) {
//    assert _map == null; // Fresh from serializer, no constructor has run
    _map = new NonBlockingHashMap<>();
    fillFrom(ab);
    return this;
  }

  // Subtypes which allow us to determine the type parameters at runtime, for generating schema metadata.
  public static class IcedHashMapStringString extends IcedHashMap<String, String> {}
  public static class IcedHashMapStringObject extends IcedHashMap<String, Object> {}
}
