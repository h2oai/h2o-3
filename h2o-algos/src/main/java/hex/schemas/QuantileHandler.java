package hex.schemas;

import hex.quantile.Quantile;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class QuantileHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public QuantileHandler() {}
  public QuantileV2 train(int version, QuantileV2 s) {
    Quantile e = s.createAndFillImpl();
    assert e._parms != null;
    e.trainModel();
    return s.fillFromImpl(e);
  }
}
