package hex.schemas;

import hex.tree.gbm.GBMModel;
import water.api.API;

public class GBMModelV2 extends SharedTreeModelV2<GBMModel,
                                                  GBMModelV2,
                                                  GBMModel.GBMParameters,
                                                  GBMV2.GBMParametersV2,
                                                  GBMModel.GBMOutput,
                                                  GBMModelV2.GBMModelOutputV2> {

  public static final class GBMModelOutputV2 extends SharedTreeModelV2.SharedTreeModelOutputV2<GBMModel.GBMOutput, GBMModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error for Train Frame")
    public double mse_train[];           // Total MSE, variance
    @API(help="Mean Square Error for Validation Frame")
    public double mse_valid[];           // Total MSE, variance
  } // GBMModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GBMV2.GBMParametersV2 createParametersSchema() { return new GBMV2.GBMParametersV2(); }
  public GBMModelOutputV2 createOutputSchema() { return new GBMModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public GBMModel createImpl() {
    GBMV2.GBMParametersV2 p = this.parameters;
    GBMModel.GBMParameters parms = p.createImpl();
    return new GBMModel( key.key(), parms, new GBMModel.GBMOutput(null, Double.NaN, Double.NaN) );
  }
}
