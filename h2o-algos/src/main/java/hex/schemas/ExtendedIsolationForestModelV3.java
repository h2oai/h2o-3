package hex.schemas;

import hex.tree.isoforextended.ExtendedIsolationForestModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class ExtendedIsolationForestModelV3 extends ModelSchemaV3<ExtendedIsolationForestModel,
        ExtendedIsolationForestModelV3,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput,
        ExtendedIsolationForestModelV3.ExtendedIsolationForestModelOutputV3> {

  public static final class ExtendedIsolationForestModelOutputV3 extends ModelOutputSchemaV3<ExtendedIsolationForestModel.ExtendedIsolationForestOutput, ExtendedIsolationForestModelOutputV3> {
      // nothing
  }

  public ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3 createParametersSchema() { return new ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3(); }
  public ExtendedIsolationForestModelOutputV3 createOutputSchema() { return new ExtendedIsolationForestModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public ExtendedIsolationForestModel createImpl() {
    ExtendedIsolationForestV3.ExtendedIsolationForestParametersV3 p = this.parameters;
    ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms = p.createImpl();
    return new ExtendedIsolationForestModel( model_id.key(), parms, new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(null) );
  }
}
