package water.util;

/** Pair class with a clearer name than AbstractMap.SimpleEntry. */
public class Triple<V> {
  public V v1;
  public V v2;
  public V v3;
  public Triple(V v1, V v2, V v3) { this.v1=v1; this.v2=v2; this.v3=v3; }
}
