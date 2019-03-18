package hex.tree;

import hex.genmodel.utils.DistributionFamily;
import water.Iced;

public class Constraints extends Iced<Constraints> {

  private final int[] _cs;
  final double _min;
  final double _max;
  final DistributionFamily _dist;
  private final boolean _use_bounds;

  public Constraints(int[] cs, DistributionFamily dist, boolean useBounds) {
    this(cs, dist, useBounds, Double.NaN, Double.NaN);
  }

  private Constraints(int[] cs, DistributionFamily dist, boolean useBounds, double min, double max) {
    _cs = cs;
    _min = min;
    _max = max;
    _dist = dist;
    _use_bounds = useBounds;
  }

  public int getColumnConstraint(int col) {
    return _cs[col];
  }

  Constraints withNewConstraint(int col, int way, double bound) {
    assert _cs[col] == 1 || _cs[col] == -1;
    if (_cs[col] == 1) { // "increasing" constraint
      if (way == 0) { // left
        return new Constraints(_cs, _dist, _use_bounds, _min, newMaxBound(_max, bound));
      } else { // right
        return new Constraints(_cs, _dist, _use_bounds, newMinBound(_min, bound), _max);
      }
    } else { // "decreasing" constraint
      if (way == 0) { // left
        return new Constraints(_cs, _dist, _use_bounds, newMinBound(_min, bound),  _max);
      } else { // right
        return new Constraints(_cs, _dist, _use_bounds, _min, newMaxBound(_max, bound));
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

  public boolean needsGammaDenum() {
    return !_dist.equals(DistributionFamily.gaussian);
  }

}
