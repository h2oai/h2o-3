package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

import java.io.Serializable;
import java.util.*;

/** Iced / Freezable hash set. Delegates to a NonBlockingHashMap for
 *  all its operations. Implementation based on IcedHashMapBase.
 *  
 *  Only supports Freezables as values.
 */
public class IcedHashSet<V extends Freezable<V>> extends Iced<IcedHashSet<V>> implements Set<V>, Cloneable, Serializable {

  private transient volatile boolean _write_lock; // not an actual lock - used in asserts only to catch issues in development

  private transient NonBlockingHashMap<V, V> _map; // the backing NonBlockingHashMap

  public IcedHashSet() {
    init();
  }

  private Map<V, V> init() {
    return (_map = makeBackingMap());
  }

  private NonBlockingHashMap<V, V> makeBackingMap() {
    return new NonBlockingHashMap<>();
  }

  public V addIfAbsent(V value) {
    assert ! _write_lock;
    return _map.putIfAbsent(value, value);
  }

  public V get(V value) {
    assert ! _write_lock;
    return _map.getk(value);
  }

  @Override public int size() { return _map.size(); }

  @Override
  public boolean isEmpty() {
    return _map.isEmpty();
  }

  @Override
  public boolean contains(Object value) {
    return _map.containsKey(value);
  }

  @Override
  public Iterator<V> iterator() {
    return _map.values().iterator();
  }

  @Override
  public Object[] toArray() {
    return _map.values().toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    Objects.requireNonNull(a);
    return _map.values().toArray(a);
  }

  @Override
  public boolean add(V v) {
    assert ! _write_lock;
    return _map.putIfAbsent(v, v) == null;
  }

  @Override
  public boolean remove(Object o) {
    assert ! _write_lock;
    return _map.remove(o, o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return _map.keySet().containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends V> c) {
    assert ! _write_lock;
    boolean added = false;
    for (V item : c) {
      added |= _map.putIfAbsent(item, item) == null;
    }
    return added; 
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("Operation retainAll is not yet supported on IcedHashSet");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("Operation removeAll is not yet supported on IcedHashSet");
  }

  @Override
  public void clear() {
    assert ! _write_lock;
    _map.clear();
  }

  // Optimized for a set structure represented by NBHM - only values are written
  private void writeMap(AutoBuffer ab) {

    // For faster K/V store walking get the NBHM raw backing array,
    // and walk it directly.
    Object[] kvs = _map.raw_array();

    // Start the walk at slot 2, because slots 0,1 hold meta-data
    // In the raw backing array, Keys and Values alternate in slots
    // Ignore tombstones and Primes and null's
    for (int i = 2; i < kvs.length; i += 2)
      if (kvs[i+1] instanceof Iced)
        ab.put((Freezable)kvs[i+1]);
  }

  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  public final AutoBuffer write_impl(AutoBuffer ab) {
    _write_lock = true;
    try {
      if (_map.size() == 0)
        return ab.put1(0); // empty set
      ab.put1(1); // mark non-empty 
      writeMap(ab); // Do the hard work of writing the date
      return ab.put(null);
    } catch (Exception e) {
      throw new RuntimeException("IcedHashSet serialization failed!", e);
    } finally {
      _write_lock = false;
    }
  }

  public final IcedHashSet read_impl(AutoBuffer ab) {
    try {
      assert _map == null || _map.isEmpty(); // Fresh from serializer, no constructor has run
      Map<V, V> map = init();
      if (ab.get1() == 0)
        return this;
      V val;
      while ((val = ab.get()) != null) {
        map.put(val, val);
      }
      return this;
    } catch (Exception e) {
      throw new RuntimeException("IcedHashSet deserialization failed!", e);
    }
  }

  public final IcedHashSet readJSON_impl(AutoBuffer ab) {
    throw H2O.unimpl();
  }

  public final AutoBuffer writeJSON_impl(AutoBuffer ab) {
    boolean first = true;
    for (V value : _map.values()) {

      if (! first)
        ab.put1(',').put1(' ');
      else
        first = false;

      if (value != null)
        ab.putJSON(value);
    }
    // ab.put1('}'); // NOTE: the serialization framework adds this auto-magically
    return ab;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IcedHashSet<?> that = (IcedHashSet<?>) o;

    return _map != null ? _map.equals(that._map) : that._map == null;
  }

  @Override
  public int hashCode() {
    return _map != null ? _map.hashCode() : 0;
  }
}
