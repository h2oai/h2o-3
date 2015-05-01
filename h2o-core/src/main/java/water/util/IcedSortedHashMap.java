package water.util;

import water.AutoBuffer;
import java.util.TreeMap;

/** Iced / Freezable Sorted HashMap.  Delegates to a TreeMap for
 *  all its operations.
 */
public class IcedSortedHashMap<K, V> extends IcedHashMapBase<K,V> {
  public IcedSortedHashMap() { _map = new TreeMap<>(); }

  @Override public IcedSortedHashMap read_impl( AutoBuffer ab ) {
    assert _map == null; // Fresh from serializer, no constructor has run
    _map = new TreeMap<>();
    fillFrom(ab);
    return this;
  }
}
