package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.H2O;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GBMHandler extends SharedTreeHandler<GBM, GBMV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GBMHandler() {}

  public GBMV2 train(int version, GBM builder) {
    GBMParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.train();
    GBMV2 schema = schema(version); // TODO: superclass!
    schema.parameters = new GBMV2.GBMParametersV2();
    schema.job = builder._key;
    return schema;
  }
  @Override protected GBMV2 schema(int version) { return new GBMV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
