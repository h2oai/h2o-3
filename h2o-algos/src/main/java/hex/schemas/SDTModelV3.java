package hex.schemas;

import hex.tree.sdt.SDTModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class SDTModelV3 extends ModelSchemaV3<SDTModel,
        SDTModelV3,
        SDTModel.SDTParameters,
        SDTV3.SDTParametersV3,
        SDTModel.SDTOutput,
        SDTModelV3.SDTModelOutputV3> {

  public static final class SDTModelOutputV3 extends ModelOutputSchemaV3<SDTModel.SDTOutput, SDTModelOutputV3> {
      // nothing
  }

  public SDTV3.SDTParametersV3 createParametersSchema() { return new SDTV3.SDTParametersV3(); }
  public SDTModelOutputV3 createOutputSchema() { return new SDTModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public SDTModel createImpl() {
    SDTV3.SDTParametersV3 p = this.parameters;
    SDTModel.SDTParameters parms = p.createImpl();
    return new SDTModel( model_id.key(), parms, new SDTModel.SDTOutput(null) );
  }
}
