package hex.schemas;

import hex.gbm.GBM;
import hex.gbm.GBMModel.GBMParameters;
import water.H2O;
import water.api.Handler;

public class GBMHandler extends SharedTreeHandler<GBM, GBMV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GBMHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
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
