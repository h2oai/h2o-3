package hex.schemas;

import hex.adaboost.AdaBoostModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class AdaBoostModelV3 extends ModelSchemaV3<AdaBoostModel,
        AdaBoostModelV3,
        AdaBoostModel.AdaBoostParameters,
        AdaBoostV3.AdaBoostParametersV3,
        AdaBoostModel.AdaBoostOutput,
        AdaBoostModelV3.AdaBoostModelOutputV3> {

  public static final class AdaBoostModelOutputV3 extends ModelOutputSchemaV3<AdaBoostModel.AdaBoostOutput, AdaBoostModelOutputV3> {
      // nothing
  }

  public AdaBoostV3.AdaBoostParametersV3 createParametersSchema() { return new AdaBoostV3.AdaBoostParametersV3(); }
  public AdaBoostModelOutputV3 createOutputSchema() { return new AdaBoostModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public AdaBoostModel createImpl() {
    AdaBoostV3.AdaBoostParametersV3 p = this.parameters;
    AdaBoostModel.AdaBoostParameters parms = p.createImpl();
    return new AdaBoostModel( model_id.key(), parms, new AdaBoostModel.AdaBoostOutput(null) );
  }
}
