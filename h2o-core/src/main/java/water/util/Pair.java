package water.util;

import java.util.ArrayList;
import java.util.List;
import water.util.Java7.Objects;

/** Pair class with a clearer name than AbstractMap.SimpleEntry. */
public class Pair<X, Y> {
  private X x;
  private Y y;
  
  public Pair(X x, Y y) {
    this.x = x;
    this.y = y;
  }
  
  public X _1() { return x; }
  public Y _2() { return y; }
  
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pair)) return false;
    Pair<X, Y> q = (Pair<X,Y>) o;
    return Objects.equals(x, q._1()) && Objects.equals(y, q._2());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(x)*67 + Objects.hashCode(y);
  }

  @Override
  public String toString() {
    return "Pair(" + x + ", " + y + ')';
  }
  
  static public <X,Y> List<Pair<X,Y>> product(X[] xs, Y[] ys) {
    List<Pair<X,Y>> out = new ArrayList<>(xs.length*ys.length);
    for (X x : xs) for (Y y : ys) out.add(new Pair<>(x,y));

    return out;
  }
}
