package water.util;

import java.util.AbstractMap.SimpleEntry;

/** Pair class with a clearer name than AbstractMap.SimpleEntry. */
public class Pair<K, V> extends SimpleEntry<K,V> {
  public Pair(K key, V value) {
    super(key, value);
  }
}
