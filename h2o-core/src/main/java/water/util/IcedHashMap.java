package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
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
  public K getk(K key)                                  { return _map.getk(key); }

  // Map-writing optimized for NBHM
  @Override protected void writeMap(AutoBuffer ab, byte mode) {

    // For faster K/V store walking get the NBHM raw backing array,
    // and walk it directly.
    Object[] kvs = _map.raw_array();

    KeyType keyType = keyType(mode);
    ValueType valueType = valueType(mode);
    ArrayType valueArrayType = arrayType(mode);
    // Start the walk at slot 2, because slots 0,1 hold meta-data
    // In the raw backing array, Keys and Values alternate in slots
    // Ignore tombstones and Primes and null's
    for (int i=2; i < kvs.length; i+=2) {
      K key = (K) kvs[i];
      if (!isValidKey(key, keyType)) continue;
      V value = (V) kvs[i+1];
      if (!isValidValue(value, valueType, valueArrayType)) continue;
      writeKey(ab, keyType, key);
      writeValue(ab, valueType, valueArrayType, value);
    }
  }

  @Override
  protected boolean writeable() {
    return true; // we are backed by NonBlockingHashMap, serialization is thus safe even while the map is being modified
                 // because we are working with a snapshot of NBHM
  }

}
