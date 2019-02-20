package hex.tree;

import water.Iced;

public class Constraints extends Iced<Constraints> {

  private final int[] _cs;
  final double _min;
  final double _max;

  public Constraints(int[] cs) {
    this(cs, Double.NaN, Double.NaN);
  }

  private Constraints(int[] cs, double min, double max) {
    _cs = cs;
    _min = min;
    _max = max;
  }

  int getColumnConstraint(int col) {
    return _cs[col];
  }

  Constraints withNewConstraint(int way, double bound) {
    if (way == 0) { // left
      return new Constraints(_cs, _min, bound);
    } else {
      return new Constraints(_cs, bound, _max);
    }
  }

}
