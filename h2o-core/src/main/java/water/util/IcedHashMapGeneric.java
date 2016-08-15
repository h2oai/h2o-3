package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 *
 * Generalization of standard IcedHashMap (Iced NBHM wrapper) with relaxed restrictions on K/V pairs.
 *
 * K/V pairs do not have to follow the same mode, each K/V pair is independent and can be one of:
 *
 * String | Freezable  -> Integer | String | Freezable | Freezable[].
 *
 * Values are type checked during put operation.
 *
 */
public  class IcedHashMapGeneric<K, V> extends Iced implements Map<K, V>, Cloneable, Serializable {

  public boolean isSupportedKeyType(Object K) {
    return (K instanceof Freezable[] || K instanceof Freezable || K instanceof String);
  }
  public boolean isSupportedValType(Object V) {
    return (V instanceof Freezable[] || V instanceof Freezable || V instanceof String || V instanceof Integer);
  }
  public IcedHashMapGeneric(){init();}
  private transient volatile boolean _write_lock;
  transient NonBlockingHashMap<K,V> _map;
  protected Map<K,V> map(){return _map;}
  public int size()                                     { return map().size(); }
  public boolean isEmpty()                              { return map().isEmpty(); }
  public boolean containsKey(Object key)                { return map().containsKey(key); }
  public boolean containsValue(Object value)            { return map().containsValue(value); }
  public V get(Object key)                              { return (V)map().get(key); }
  public V put(K key, V val)                          {
    assert !_write_lock;
    if(!isSupportedKeyType(key))
      throw new IllegalArgumentException("given key type is not supported: " + key.getClass().getName());
    if(!isSupportedValType(val))
      throw new IllegalArgumentException("given val type is not supported: " + val.getClass().getName());
    return (V)map().put(key, val);
  }
  public V remove(Object key)                           { assert !_write_lock; return map().remove(key); }
  public void putAll(Map<? extends K, ? extends V> m)   { assert !_write_lock;
    for(Entry<? extends K, ? extends V> e:m.entrySet())
      put(e.getKey(),e.getValue());
  }
  public void clear()                                   { assert !_write_lock;        map().clear(); }
  public Set<K> keySet()                                { return map().keySet(); }
  public Collection<V> values()                         { return map().values(); }
  public Set<Entry<K, V>> entrySet()                    { return map().entrySet(); }
  public boolean equals(Object o)                       { return map().equals(o); }
  public int hashCode()                                 { return map().hashCode(); }


  private boolean isStringKey(int mode){
    return mode % 2 == 1;
  }
  private boolean isStringVal(int mode){return mode == 1 || mode == 2;}
  private boolean isFreezeVal(int mode){return mode == 3 || mode == 4;}
  private boolean isFArrayVal(int mode){return mode == 5 || mode == 6;}
  private boolean isIntegrVal(int mode){return mode == 7 || mode == 8;}

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
    try{
      for( Entry<K, V> e : map().entrySet() ) {
        K key = e.getKey();
        assert key != null;
        V val = e.getValue();
        assert val != null;
        int mode = 0;
        if (key instanceof String) {
          if (val instanceof String) {
            mode = 1;
          } else if(val instanceof Freezable){
            mode = 3;
          } else if(val instanceof Freezable[]) {
            mode = 5;
          } else if( val instanceof Integer ){
            mode = 7;
          } else {
            throw new IllegalArgumentException("unsupported value class " + val.getClass().getName());
          }
        } else {
          if(!(key instanceof Iced))
            throw new IllegalArgumentException("key must be String or Freezable, got " + key.getClass().getName());
          if (val instanceof String) {
            mode = 2;
          } else if(val instanceof Freezable) {
            mode = 4;
          } else if(val instanceof Freezable[]) {
            mode = 6;
          } else if (val instanceof Integer){
            mode = 8;
          } else {
            throw new IllegalArgumentException("unsupported value class " + val.getClass().getName());
          }
        }
        ab.put1(mode);              // Type of hashmap being serialized
        // put key
        if (isStringKey(mode)) ab.putStr((String) key);
        else ab.put((Freezable) key);
        // put value
        if (isStringVal(mode))
          ab.putStr((String) val);
        else if(isFreezeVal(mode))
          ab.put((Freezable) val);
        else if (isFArrayVal(mode)) {
          ab.put4(((Freezable[]) val).length);
          for (Freezable v : (Freezable[]) val) ab.put(v);
        } else if(isIntegrVal(mode))
          ab.put4((Integer)val);
        else
          throw H2O.fail();
      }
      ab.put1(-1);
    } catch(Throwable t){
      System.err.println("Iced hash map serialization failed! " + t.toString() + ", msg = " + t.getMessage());
      t.printStackTrace();
      throw H2O.fail("Iced hash map serialization failed!" + t.toString() + ", msg = " + t.getMessage());
    } finally {
      _write_lock = false;
    }
    return ab;
  }

  protected Map<K, V> init() { return _map = new NonBlockingHashMap<>(); }



  /**
   * Helper for serialization - fills the mymap() from K-V pairs in the AutoBuffer object
   * @param ab Contains the serialized K-V pairs
   */
  public final IcedHashMapGeneric read_impl(AutoBuffer ab) {
    try {
      assert map() == null || map().isEmpty(); // Fresh from serializer, no constructor has run
      Map<K, V> map = init();
      K key;
      V val;
      int mode;
      while ((mode = ab.get1()) != -1) {
        key = isStringKey(mode)?(K)ab.getStr():(K)ab.get();

        if (isStringVal(mode))
          val = (V)ab.getStr();
        else if(isFreezeVal(mode))
          val = (V)ab.get();
        else if (isFArrayVal(mode)) {
          Freezable[] vals = new Freezable[ab.get4()];
          for (int i = 0; i < vals.length; ++i) vals[i] = ab.get();
          val = (V)vals;
        } else if(isIntegrVal(mode))
          val = (V) (new Integer(ab.get4()));
        else
          throw H2O.fail();
        map.put(key,val);
      }
      return this;
    } catch(Throwable t) {
      t.printStackTrace();

      if (null == t.getCause()) {
        throw H2O.fail("IcedHashMap deserialization failed! + " + t.toString() + ", msg = " + t.getMessage() + ", cause: null");
      } else {
        throw H2O.fail("IcedHashMap deserialization failed! + " + t.toString() + ", msg = " + t.getMessage() +
                ", cause: " + t.getCause().toString() +
                ", cause msg: " + t.getCause().getMessage() +
                ", cause stacktrace: " + java.util.Arrays.toString(t.getCause().getStackTrace()));
      }
    }
  }
  public final IcedHashMapGeneric readJSON_impl(AutoBuffer ab ) {throw H2O.unimpl();}

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
  // Subtypes which allow us to determine the type parameters at runtime, for generating schema metadata.
  public static class IcedHashMapStringString extends IcedHashMapGeneric<String, String> {}
  public static class IcedHashMapStringObject extends IcedHashMapGeneric<String, Object> {}
}
