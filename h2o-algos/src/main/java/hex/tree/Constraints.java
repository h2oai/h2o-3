package hex.tree;

import water.Iced;

public class Constraints extends Iced<Constraints> {

  private final int[] _cs;
  final double _min;
  final double _max;
  private final boolean _use_bounds;
  private final Constraints _old;

  public Constraints(int[] cs, boolean useBounds) {
    _cs = cs;
    _use_bounds = useBounds;
    _min = Double.NaN;
    _max = Double.NaN;
    _old = null;
  }

  private Constraints(Constraints old, double min, double max) {
    _cs = old._cs;
    _min = min;
    _max = max;
    _use_bounds = old._use_bounds;
    _old = old;
  }

  public int getColumnConstraint(int col) {
    return _cs[col];
  }

  Constraints withNewConstraint(int col, int way, double bound) {
    if (_cs[col] == 1) { // "increasing" constraint
      if (way == 0) { // left
        return new Constraints(this, _min, newMaxBound(_max, bound));
      } else { // right
        return new Constraints(this, newMinBound(_min, bound), _max);
      }
    } else { // "decreasing" constraint
      if (way == 0) { // left
        return new Constraints(this, newMinBound(_min, bound),  _max);
      } else { // right
        return new Constraints(this, _min, newMaxBound(_max, bound));
      }
    }
  }

  public boolean useBounds() {
    return _use_bounds;
  }

  private static double newMaxBound(double old_max, double proposed_max) {
    if (Double.isNaN(old_max))
      return proposed_max;
    assert !Double.isNaN(proposed_max);
    return Math.min(old_max, proposed_max);
  }

  private static double newMinBound(double old_min, double proposed_min) {
    if (Double.isNaN(old_min))
      return proposed_min;
    assert !Double.isNaN(proposed_min);
    return Math.max(old_min, proposed_min);
  }
  
}
