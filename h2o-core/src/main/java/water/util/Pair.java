package water.util;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pair)) return false;
    Pair<?, ?> pair = (Pair<?, ?>) o;
    return Objects.equals(x, pair.x) &&
           Objects.equals(y, pair.y);
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y);
  }

  @Override
  public String toString() {
    return "Pair(" + x + ", " + y + ')';
  }
  
  static public <X,Y> List<Pair<X,Y>> product(X[] xs, Y[] ys) {
    List<Pair<X,Y>> out = new ArrayList<>(xs.length*ys.length);
    for (X x : xs) for (Y y : ys) out.add(new Pair<X,Y>(x,y));

    return out;
  }
}
