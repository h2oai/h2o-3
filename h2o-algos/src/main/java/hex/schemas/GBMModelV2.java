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
    @API(help="Mean Square Errors for Training Frame from 1 to N trees")
    public double mse_train[];           // Total MSE, variance
    @API(help="Mean Square Errors for Validation Frame from 1 to N trees")
    public double mse_valid[];           // Total MSE, variance

    @Override
    public GBMModelOutputV2 fillFromImpl(GBMModel.GBMOutput impl) {
      GBMModelOutputV2 gbmmo = super.fillFromImpl(impl);
      gbmmo.mse = impl._mse_train[impl._mse_train.length-1];
      return gbmmo;
    }
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
