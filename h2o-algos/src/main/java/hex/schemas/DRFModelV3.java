package hex.schemas;

import hex.tree.drf.DRFModel;

public class DRFModelV3 extends SharedTreeModelV3<DRFModel,
        DRFModelV3,
        DRFModel.DRFParameters,
        DRFV3.DRFParametersV3,
        DRFModel.DRFOutput,
        DRFModelV3.DRFModelOutputV3> {

  public static final class DRFModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<DRFModel.DRFOutput, DRFModelOutputV3> {}

  public DRFV3.DRFParametersV3 createParametersSchema() { return new DRFV3.DRFParametersV3(); }
  public DRFModelOutputV3 createOutputSchema() { return new DRFModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DRFModel createImpl() {
    DRFV3.DRFParametersV3 p = this.parameters;
    DRFModel.DRFParameters parms = p.createImpl();
    return new DRFModel( model_id.key(), parms, new DRFModel.DRFOutput(null, Double.NaN, Double.NaN) );
  }
}
