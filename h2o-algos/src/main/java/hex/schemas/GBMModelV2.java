package hex.schemas;

import hex.tree.gbm.GBMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
//import water.util.DocGen.HTML;

public class GBMModelV2 extends ModelSchema<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput, GBMModelV2> {

  public static final class GBMModelOutputV2 extends ModelOutputSchema<GBMModel.GBMOutput, GBMModelOutputV2> {
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
    GBMV2.GBMParametersV2 p = ((GBMV2.GBMParametersV2)this.parameters);
    GBMModel.GBMParameters parms = p.createImpl();
    return new GBMModel( key, parms, new GBMModel.GBMOutput(null, Double.NaN, Double.NaN) );
  }
}
