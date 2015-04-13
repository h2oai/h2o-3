package hex.schemas;

import hex.tree.drf.DRFModel;
import water.api.API;

public class DRFModelV3 extends SharedTreeModelV3<DRFModel,
                                                  DRFModelV3,
                                                  DRFModel.DRFParameters,
                                                  DRFV3.DRFParametersV3,
                                                  DRFModel.DRFOutput,
                                                  DRFModelV3.DRFModelOutputV3> {

  public static final class DRFModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<DRFModel.DRFOutput, DRFModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Errors for Training Frame from 1 to N trees")
    public double mse_train[];           // Total MSE, variance
    @API(help="Mean Square Errors for Validation Frame from 1 to N trees")
    public double mse_valid[];           // Total MSE, variance

    @Override
    public DRFModelOutputV3 fillFromImpl(DRFModel.DRFOutput impl) {
      DRFModelOutputV3 drfmo = super.fillFromImpl(impl);
      drfmo.mse = impl._mse_train[impl._mse_train.length-1];
      return drfmo;
    }
  } // DRFModelOutputV2


  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public DRFV3.DRFParametersV3 createParametersSchema() { return new DRFV3.DRFParametersV3(); }
  public DRFModelOutputV3 createOutputSchema() { return new DRFModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DRFModel createImpl() {
    DRFV3.DRFParametersV3 p = this.parameters;
    DRFModel.DRFParameters parms = p.createImpl();
    return new DRFModel( key.key(), parms, new DRFModel.DRFOutput(null, Double.NaN, Double.NaN) );
  }
}
