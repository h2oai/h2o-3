package hex.schemas;

import hex.tree.drf.DRFModel;
import water.api.API;

public class DRFModelV2 extends SharedTreeModelV2<DRFModel,
                                                  DRFModelV2,
                                                  DRFModel.DRFParameters,
                                                  DRFV2.DRFParametersV2,
                                                  DRFModel.DRFOutput,
                                                  DRFModelV2.DRFModelOutputV2> {

  public static final class DRFModelOutputV2 extends SharedTreeModelV2.SharedTreeModelOutputV2<DRFModel.DRFOutput, DRFModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error for Train Frame")
    public double mse_train[];           // Total MSE, variance
    @API(help="Mean Square Error for Validation Frame")
    public double mse_valid[];           // Total MSE, variance
  } // DRFModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public DRFV2.DRFParametersV2 createParametersSchema() { return new DRFV2.DRFParametersV2(); }
  public DRFModelOutputV2 createOutputSchema() { return new DRFModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DRFModel createImpl() {
    DRFV2.DRFParametersV2 p = this.parameters;
    DRFModel.DRFParameters parms = p.createImpl();
    return new DRFModel( key.key(), parms, new DRFModel.DRFOutput(null, Double.NaN, Double.NaN) );
  }
}
