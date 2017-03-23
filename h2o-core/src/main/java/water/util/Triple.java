package water.util;

import java.util.ArrayList;
import java.util.List;
import water.util.Java7.Objects;

/** Pair class with a clearer name than AbstractMap.SimpleEntry. */
// TODO(vlad): add proper comment, have three params
public class Triple<V> {
  public V v1;
  public V v2;
  public V v3;
  public Triple(V v1, V v2, V v3) { this.v1=v1; this.v2=v2; this.v3=v3; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Triple)) return false;
    Triple<?> triple = (Triple<?>) o;
    return Objects.equals(v1, triple.v1) &&
           Objects.equals(v2, triple.v2) &&
           Objects.equals(v3, triple.v3);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(v1)*2017+Objects.hashCode(v2)*79+Objects.hashCode(v3);
  }


  @Override
  public String toString() {
    return "Triple(" + v1 +", " + v2 + ", " + v3 + ')';
  }

  static public <V> List<Triple<V>> product(V[] v1s, V[] v2s, V[] v3s) {
    List<Triple<V>> out = new ArrayList<>(v1s.length*v2s.length*v3s.length);
    for (V v1 : v1s) for (V v2 : v2s) for (V v3 : v3s) out.add(new Triple<>(v1,v2,v3));
    
    return out;
  }
}
