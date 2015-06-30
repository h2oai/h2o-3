package hex.schemas;

import hex.tree.gbm.GBMModel;

public class GBMModelV3 extends SharedTreeModelV3<GBMModel,
        GBMModelV3,
        GBMModel.GBMParameters,
        GBMV3.GBMParametersV3,
        GBMModel.GBMOutput,
        GBMModelV3.GBMModelOutputV3> {

  public static final class GBMModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<GBMModel.GBMOutput, GBMModelOutputV3> {}

  public GBMV3.GBMParametersV3 createParametersSchema() { return new GBMV3.GBMParametersV3(); }
  public GBMModelOutputV3 createOutputSchema() { return new GBMModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public GBMModel createImpl() {
    GBMV3.GBMParametersV3 p = this.parameters;
    GBMModel.GBMParameters parms = p.createImpl();
    return new GBMModel( model_id.key(), parms, new GBMModel.GBMOutput(null, Double.NaN, Double.NaN) );
  }
}
