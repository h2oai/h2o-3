package hex.tree;

import water.Iced;

public class Constraint extends Iced<Constraint> {

  public Constraint(int direction) {
    _direction = direction;
    _min = Double.NaN;
    _max = Double.NaN;
  }

  public Constraint(Constraint old, int way, double bound) {
    _direction = old._direction;
    if (way == 0) {
      _min = old._min;
      _max = bound;
    } else {
      assert way == 1;
      _min = bound;
      _max = old._max;
    }
  }

  int _direction;
  double _min;
  double _max;
}
