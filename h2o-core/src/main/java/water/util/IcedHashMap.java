package water.util;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import water.AutoBuffer;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

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
  @Override protected void writeMap(AutoBuffer ab, int mode) {

    // For faster K/V store walking get the NBHM raw backing array,
    // and walk it directly.
    Object[] kvs = _map.raw_array();

    // Start the walk at slot 2, because slots 0,1 hold meta-data
    // In the raw backing array, Keys and Values alternate in slots
    // Ignore tombstones and Primes and null's
    switch( mode ) {
    case 1:
      for( int i=2; i<kvs.length; i += 2 )
        if( kvs[i] instanceof String && kvs[i+1] instanceof String )
          ab.putStr((String)kvs[i]).putStr((String)kvs[i+1]);
      break;
    case 2:   
      for( int i=2; i<kvs.length; i += 2 )
        if( kvs[i] instanceof String && kvs[i+1] instanceof Iced   )
          ab.putStr((String)kvs[i]).put   ((Iced  )kvs[i+1]);
      break;
    case 3:   
      for( int i=2; i<kvs.length; i += 2 )
        if( kvs[i] instanceof Iced   && kvs[i+1] instanceof String )
          ab.put   ((Iced  )kvs[i]).putStr((String)kvs[i+1]);
      break;
    case 4:   
      for( int i=2; i<kvs.length; i += 2 )
        if( kvs[i] instanceof Iced   && kvs[i+1] instanceof Iced   )
          ab.put   ((Iced  )kvs[i]).put   ((Iced  )kvs[i+1]);
      break;
    default: H2O.fail();
    }
  }

  // Subtypes which allow us to determine the type parameters at runtime, for generating schema metadata.
  public static class IcedHashMapStringString extends IcedHashMap<String, String> {}
  public static class IcedHashMapStringObject extends IcedHashMap<String, Object> {}
}
