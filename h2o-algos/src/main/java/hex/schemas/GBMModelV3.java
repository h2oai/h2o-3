package hex.schemas;

import hex.tree.gbm.GBMModel;
import water.api.API;

public class GBMModelV3 extends SharedTreeModelV3<GBMModel,
                                                  GBMModelV3,
                                                  GBMModel.GBMParameters,
                                                  GBMV3.GBMParametersV3,
                                                  GBMModel.GBMOutput,
                                                  GBMModelV3.GBMModelOutputV3> {

  public static final class GBMModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<GBMModel.GBMOutput, GBMModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Errors for Training Frame from 1 to N trees")
    public double mse_train[];           // Total MSE, variance
    @API(help="Mean Square Errors for Validation Frame from 1 to N trees")
    public double mse_valid[];           // Total MSE, variance

    @Override
    public GBMModelOutputV3 fillFromImpl(GBMModel.GBMOutput impl) {
      GBMModelOutputV3 gbmmo = super.fillFromImpl(impl);
      gbmmo.mse = impl._mse_train[impl._mse_train.length-1];
      return gbmmo;
    }
  } // GBMModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GBMV3.GBMParametersV3 createParametersSchema() { return new GBMV3.GBMParametersV3(); }
  public GBMModelOutputV3 createOutputSchema() { return new GBMModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public GBMModel createImpl() {
    GBMV3.GBMParametersV3 p = this.parameters;
    GBMModel.GBMParameters parms = p.createImpl();
    return new GBMModel( key.key(), parms, new GBMModel.GBMOutput(null, Double.NaN, Double.NaN) );
  }
}
