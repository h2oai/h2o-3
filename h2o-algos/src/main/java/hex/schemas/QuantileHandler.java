package hex.schemas;

import hex.quantile.Quantile;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class QuantileHandler extends Handler<Quantile,QuantileV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public QuantileHandler() {}
  public QuantileV2 train(int version, Quantile e) {
    assert e._parms != null;
    e.trainModel();
    return schema(version).fillFromImpl(e);
  }
  @Override protected QuantileV2 schema(int version) { QuantileV2 schema = new QuantileV2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}
