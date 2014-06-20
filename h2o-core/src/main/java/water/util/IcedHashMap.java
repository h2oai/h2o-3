package water.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;
import water.util.DocGen.HTML;

/**
 * Iced / Freezable NonBlockingHashMap.  Delegates to a NonBlockingHashMap for all its
 * operations.  Inspired by water.parser.Enum.
 */
public class IcedHashMap<K, V>
  extends Iced
  implements ConcurrentMap<K, V>, Cloneable, Serializable {

  private volatile NonBlockingHashMap<K, V> _map;
  public IcedHashMap() {
    _map = new NonBlockingHashMap<K, V>();
  }

  public V putIfAbsent(K key, V value) {
    return _map.putIfAbsent(key, value);
  }

  public boolean remove(Object key, Object value) {
    return _map.remove(key, value);
  }

  public boolean replace(K key, V oldValue, V newValue) {
    return _map.replace(key, oldValue, newValue);
  }

  public V replace(K key, V value) {
    return _map.replace(key, value);
  }

  public int size() {
    return _map.size();
  }

  public boolean isEmpty() {
    return _map.isEmpty();
  }

  public boolean containsKey(Object key) {
    return _map.containsKey(key);
  }

  public boolean containsValue(Object value) {
    return _map.containsValue(value);
  }

  public V get(Object key) {
    return _map.get(key);
  }

  public V put(K key, V value) {
    return _map.put(key, value);
  }

  public V remove(Object key) {
    return _map.remove(key);
  }

  public void putAll(Map<? extends K, ? extends V> m) {
    _map.putAll(m);
  }

  public void clear() {
    _map.clear();
  }

  public Set<K> keySet() {
    return _map.keySet();
  }

  public Collection<V> values() {
    return _map.values();
  }

  public Set<Entry<K, V>> entrySet() {
    return _map.entrySet();
  }

  public boolean equals(Object o) {
    return _map.equals(o);
  }

  public int hashCode() {
    return _map.hashCode();
  }

  // This comment is stolen from water.parser.Enum:
  //
  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  @Override public AutoBuffer write_impl( AutoBuffer ab ) { throw H2O.unimpl(); }
  @Override public IcedHashMap read_impl( AutoBuffer ab ) { throw H2O.unimpl(); }

  @Override public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    if (_map == null) return ab.putJNULL();

    // ab.put1('{'); // NOTE: the serialization framework adds this automagically
    boolean first = true;
    for (Entry<K, V> entry : _map.entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();

      assert entry.getKey() instanceof String;
      assert value instanceof String || value instanceof Freezable;

      if (first) { first = false; } else {ab.put1(',').put1(' '); }
      ab.putJSONName((String) key);
      ab.put1(':');

      if (value instanceof String)
        ab.putJSONName((String)value);
      else if (value instanceof Freezable)
        ab.putJSON((Freezable) value);
    }
    // ab.put1('}'); // NOTE: the serialization framework adds this automagically
    return ab;
  }
  @Override public IcedHashMap<K, V> readJSON_impl( AutoBuffer ab ) { throw H2O.unimpl(); }
  @Override public HTML writeHTML_impl( HTML ab ) { throw H2O.unimpl(); }

}
