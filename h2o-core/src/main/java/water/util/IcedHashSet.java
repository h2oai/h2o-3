package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/** Iced / Freezable NonBlockingHashMap.  Delegates to a NonBlockingHashMap for
 *  all its operations.  Inspired by water.parser.Categorical.
 */
public class IcedHashSet<V extends Freezable<V>> implements Set<V> {

  transient NonBlockingHashMap<V,V> _map;

  public V addIfAbsent(V value)                         { return _map.putIfAbsent(value, value); }
  public V get(V value)                                 { return _map.getk(value); }

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
    return _map.values().toArray(a);
  }

  @Override
  public boolean add(V v) {
    return _map.putIfAbsent(v, v) == null;
  }

  @Override
  public boolean remove(Object o) {
    return _map.remove(o, o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return _map.keySet().containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends V> c) {
    return false;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return false;
  }

  @Override
  public void clear() {
    _map.clear();
  }

  // Map-writing optimized for NBHM
  protected void writeMap(AutoBuffer ab, int mode) {

    // For faster K/V store walking get the NBHM raw backing array,
    // and walk it directly.
    Object[] kvs = _map.raw_array();

    // Start the walk at slot 2, because slots 0,1 hold meta-data
    // In the raw backing array, Keys and Values alternate in slots
    // Ignore tombstones and Primes and null's
    switch( mode ) {
      case 1:  // <String,String>
        for( int i=2; i<kvs.length; i += 2 )
          if( kvs[i] instanceof String && kvs[i+1] instanceof String )
            ab.putStr((String)kvs[i]).putStr((String)kvs[i+1]);
        break;
      case 2: // <String,Freezable>
        for( int i=2; i<kvs.length; i += 2 )
          if( kvs[i] instanceof String && kvs[i+1] instanceof Iced   )
            ab.putStr((String)kvs[i]).put   ((Freezable)kvs[i+1]);
        break;
      case 3: // <Freezable,String>
        for( int i=2; i<kvs.length; i += 2 )
          if( kvs[i] instanceof Iced   && kvs[i+1] instanceof String )
            ab.put   ((Freezable  )kvs[i]).putStr((String)kvs[i+1]);
        break;
      case 4: // <Freezable,Freezable>
        for( int i=2; i<kvs.length; i += 2 )
          if( kvs[i] instanceof Freezable   && kvs[i+1] instanceof Freezable   )
            ab.put   ((Freezable  )kvs[i]).put   ((Freezable  )kvs[i+1]);
        break;
      case 5:  // <String,Freezable[]>
        for( int i=2;i<kvs.length; i+=2 )
          if( kvs[i] instanceof String && kvs[i+1] instanceof Freezable[] ) {
            Freezable[] vals = (Freezable[])kvs[i+1];
            ab.putStr((String)kvs[i]).put4(vals.length);  // key len vals
            for(Freezable v: vals) ab.put(v);
          }
        break;
      case 6: // <Freezable,Freezable[]>
        for( int i=2;i<kvs.length; i+=2 )
          if( kvs[i] instanceof Freezable && kvs[i+1] instanceof Freezable[] ) {
            Freezable[] vals = (Freezable[])kvs[i+1];
            ab.put((Freezable)kvs[i]).put4(vals.length);  // key len vals
            for(Freezable v: vals) ab.put(v);
          }
        break;
      default: throw H2O.fail();
    }
  }
}
