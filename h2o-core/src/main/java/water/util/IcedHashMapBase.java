package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Iced / Freezable NonBlockingHashMap abstract base class.
 */
public abstract class IcedHashMapBase<K, V> extends Iced implements Map<K, V>, Cloneable, Serializable {
  private transient volatile boolean _write_lock;
  abstract protected Map<K,V> map();
  public int size()                                     { return map().size(); }
  public boolean isEmpty()                              { return map().isEmpty(); }
  public boolean containsKey(Object key)                { return map().containsKey(key); }
  public boolean containsValue(Object value)            { return map().containsValue(value); }
  public V get(Object key)                              { return (V)map().get(key); }
  public V put(K key, V value)                          { assert !_write_lock; return (V)map().put(key, value);}
  public V remove(Object key)                           { assert !_write_lock; return map().remove(key); }
  public void putAll(Map<? extends K, ? extends V> m)   { assert !_write_lock;        map().putAll(m); }
  public void clear()                                   { assert !_write_lock;        map().clear(); }
  public Set<K> keySet()                                { return map().keySet(); }
  public Collection<V> values()                         { return map().values(); }
  public Set<Entry<K, V>> entrySet()                    { return map().entrySet(); }
  public boolean equals(Object o)                       { return map().equals(o); }
  public int hashCode()                                 { return map().hashCode(); }

  // This comment is stolen from water.parser.Categorical:
  //
  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  public final AutoBuffer write_impl( AutoBuffer ab ) {
    _write_lock = true;
    try {
      if (map().size() == 0) return ab.put1(0); // empty map
      Entry<K, V> entry = map().entrySet().iterator().next();
      K key = entry.getKey();
      V val = entry.getValue();
      assert key != null && val != null;
      int mode;
      if (key instanceof String) {
        if (val instanceof String) {
          mode = 1;
        } else {
          assert (val instanceof Freezable || val instanceof Freezable[]):"incompatible class " + val.getClass();
          mode = val instanceof Freezable ? 2 : 5;
        }
      } else {
        assert key instanceof Iced;
        if (val instanceof String) {
          mode = 3;
        } else {
          assert (val instanceof Freezable || val instanceof Freezable[]);
          mode = val instanceof Freezable ? 4 : 6;
        }
      }
      ab.put1(mode);              // Type of hashmap being serialized
      writeMap(ab, mode);          // Do the hard work of writing the map
      return (mode == 1 || mode == 2 || mode == 5) ? ab.putStr(null) : ab.put(null);
    } catch(Throwable t){
      System.err.println("Iced hash map serialization failed! " + t.toString() + ", msg = " + t.getMessage());
      t.printStackTrace();
      throw H2O.fail("Iced hash map serialization failed!" + t.toString() + ", msg = " + t.getMessage());
    } finally {
      _write_lock = false;
    }
  }

  abstract protected Map<K,V> init();

  protected void writeMap(AutoBuffer ab, int mode) {
    for( Entry<K, V> e : map().entrySet() ) {
      K key = e.getKey();   assert key != null;
      V val = e.getValue(); assert val != null;
      // put key
      if( mode==1 || mode==2 || mode==5 ) ab.putStr((String)key); else ab.put((Freezable)key);

      // put value
      if( mode==1 || mode==3 ) ab.putStr((String)val);
      else if( mode==5 || mode==6 ) {
        ab.put4(((Freezable[]) val).length);
        for (Freezable v : (Freezable[]) val) ab.put(v);
      }
      else ab.put((Freezable)val);
    }
  }

  /**
   * Helper for serialization - fills the mymap() from K-V pairs in the AutoBuffer object
   * @param ab Contains the serialized K-V pairs
   */
  public final IcedHashMapBase read_impl(AutoBuffer ab) {
    try {
      assert map() == null || map().isEmpty(); // Fresh from serializer, no constructor has run
      Map<K, V> map = init();
      int mode = ab.get1();
      if (mode == 0) return this;
      K key;
      V val;
      while ((key = ((mode == 1 || mode == 2) ? (K) ab.getStr() : (K) ab.get())) != null) {
        if (mode == 5 || mode == 6) {
          Freezable[] vals = new Freezable[ab.get4()];
          for (int i = 0; i < vals.length; ++i) vals[i] = ab.get();
          map.put(key, (V) vals);
        } else {
          val = ((mode == 1 || mode == 3) ? (V) ab.getStr() : (V) ab.get());
          map.put(key, val);
        }
      }
      return this;
    } catch(Throwable t) {
      t.printStackTrace();
      throw H2O.fail("IcedHashMap deserialization failed! + " + t.toString() + ", msg = " + t.getMessage());
    }
  }
  public final IcedHashMapBase readJSON_impl( AutoBuffer ab ) {throw H2O.unimpl();}

  public final AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    boolean first = true;
    for (Entry<K, V> entry : map().entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();

      assert entry.getKey() instanceof String;
      assert value instanceof String || value instanceof String[] || value instanceof Integer || value instanceof Freezable || value instanceof Freezable[];

      if (first) { first = false; } else {ab.put1(',').put1(' '); }
      ab.putJSONName((String) key);
      ab.put1(':');

      if (value instanceof String)
        ab.putJSONName((String) value);
      else if (value instanceof String[])
        ab.putJSONAStr((String[]) value);
      else if (value instanceof Integer)
        ab.putJSON4((Integer) value);
      else if (value instanceof Freezable)
        ab.putJSON((Freezable) value);
      else if (value instanceof Freezable[])
        ab.putJSONA((Freezable[]) value);
    }
    // ab.put1('}'); // NOTE: the serialization framework adds this automagically
    return ab;
  }
}
