package hex.tree;

import water.Iced;

public class Constraints extends Iced<Constraints> {

  private final int[] _cs;
  final double _min;
  final double _max;
  private final boolean _use_bounds;

  public Constraints(int[] cs, boolean useBounds) {
    this(cs, useBounds, Double.NaN, Double.NaN);
  }

  private Constraints(int[] cs, boolean useBounds, double min, double max) {
    _cs = cs;
    _min = min;
    _max = max;
    _use_bounds = useBounds;
  }

  int getColumnConstraint(int col) {
    return _cs[col];
  }

  Constraints withNewConstraint(int way, double bound) {
    if (way == 0) { // left
      return new Constraints(_cs, _use_bounds, _min, bound);
    } else {
      return new Constraints(_cs, _use_bounds, bound, _max);
    }
  }

  public boolean useBounds() {
    return _use_bounds;
  }

}
