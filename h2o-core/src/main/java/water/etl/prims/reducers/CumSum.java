package water.etl.prims.reducers;

import water.fvec.Frame;

/**
 * Created by markc on 2/27/17.
 */
public final class CumSum extends Cumu {
  private CumSum() {}
  @Override
  public double op(double l, double r) {
    return l + r;
  }

  @Override
  public double init() { return 0; }

  public static Frame get(Frame fr, double axis) {
    CumSum c = new CumSum();
    return c.run(fr,axis);
  }

}
