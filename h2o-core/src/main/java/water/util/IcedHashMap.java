package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;
import water.util.DocGen.HTML;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/** Iced / Freezable NonBlockingHashMap.  Delegates to a NonBlockingHashMap for
 *  all its operations.  Inspired by water.parser.Categorical.
 */
public class IcedHashMap<K, V> extends Iced implements ConcurrentMap<K, V>, Cloneable, Serializable {

  private NonBlockingHashMap<K, V> _map;
  public IcedHashMap() { _map = new NonBlockingHashMap<>(); }
  public V putIfAbsent(K key, V value)                  { return _map.putIfAbsent(key, value); }
  public boolean remove(Object key, Object value)       { return _map.remove(key, value);  }
  public boolean replace(K key, V oldValue, V newValue) { return _map.replace(key, oldValue, newValue); }
  public V replace(K key, V value)                      { return _map.replace(key, value); }
  public int size()                                     { return _map.size(); }
  public boolean isEmpty()                              { return _map.isEmpty(); }
  public boolean containsKey(Object key)                { return _map.containsKey(key); }
  public boolean containsValue(Object value)            { return _map.containsValue(value); }
  public V get(Object key)                              { return _map.get(key); }
  public V put(K key, V value)                          { return _map.put(key, value); }
  public V remove(Object key)                           { return _map.remove(key); }
  public void putAll(Map<? extends K, ? extends V> m)   {        _map.putAll(m); }
  public void clear()                                   {        _map.clear(); }
  public Set<K> keySet()                                { return _map.keySet(); }
  public Collection<V> values()                         { return _map.values(); }
  public Set<Entry<K, V>> entrySet()                    { return _map.entrySet(); }
  public boolean equals(Object o)                       { return _map.equals(o); }
  public int hashCode()                                 { return _map.hashCode(); }

  // This comment is stolen from water.parser.Categorical:
  //
  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  @Override public AutoBuffer write_impl( AutoBuffer ab ) {
    if( _map.size()==0 ) return ab.put1(0); // empty map
    Entry<K,V> entry = _map.entrySet().iterator().next();
    K key = entry.getKey();  V val = entry.getValue();
    assert key != null && val != null;
    int mode;
    if( key instanceof String ) {
      if( val instanceof String ) {      mode = 1; } 
      else { assert val instanceof Iced; mode = 2; }
    } else {
      assert key instanceof Iced;
      if( val instanceof String ) {      mode = 3; }
      else { assert val instanceof Iced; mode = 4; }
    }
    ab.put1(mode);              // Type of hashmap being serialized
    for( Entry<K, V> e : _map.entrySet() ) {
      key = e.getKey();   assert key != null;
      val = e.getValue(); assert val != null;
      if( mode==1 || mode ==2 ) ab.putStr((String)key); else ab.put((Iced)key);
      if( mode==1 || mode ==3 ) ab.putStr((String)val); else ab.put((Iced)val);
    }
    return (mode==1 || mode==2) ? ab.putStr(null) : ab.put(null);
  }
  @Override public IcedHashMap read_impl( AutoBuffer ab ) {
    assert _map == null; // Fresh from serializer, no constructor has run
    _map = new NonBlockingHashMap<>();
    int mode = ab.get1();
    if( mode == 0 ) return this; // empty map
    K key;  V val;
    while( (key = ((mode==1 || mode==2) ? (K)ab.getStr() : (K)ab.get())) != null ) {
      val = ((mode==1 || mode==3) ? (V)ab.getStr() : (V)ab.get());
      _map.put(key,val);
    }
    return this;
  }

  @Override public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    // ab.put1('{'); // NOTE: the serialization framework adds this automagically
    boolean first = true;
    for (Entry<K, V> entry : _map.entrySet()) {
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
  @Override public IcedHashMap<K, V> readJSON_impl( AutoBuffer ab ) { throw H2O.fail(); }
  @Override public HTML writeHTML_impl( HTML ab ) { throw H2O.fail(); }

}
