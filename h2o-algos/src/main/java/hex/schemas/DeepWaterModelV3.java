package hex.schemas;

import hex.deepwater.DeepWaterModel;
import hex.deepwater.DeepWaterModelOutput;
import hex.deepwater.DeepWaterParameters;
import water.Key;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class DeepWaterModelV3 extends ModelSchemaV3<DeepWaterModel, DeepWaterModelV3, DeepWaterParameters, DeepWaterV3.DeepWaterParametersV3, DeepWaterModelOutput, DeepWaterModelV3.DeepWaterModelOutputV3> {

  public static final class DeepWaterModelOutputV3 extends ModelOutputSchemaV3<DeepWaterModelOutput, DeepWaterModelOutputV3> {
  }

  public DeepWaterV3.DeepWaterParametersV3 createParametersSchema() { return new DeepWaterV3.DeepWaterParametersV3(); }
  public DeepWaterModelOutputV3 createOutputSchema() { return new DeepWaterModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public DeepWaterModel createImpl() {
    DeepWaterParameters parms = parameters.createImpl();
    return new DeepWaterModel(Key.<DeepWaterModel>make() /*dest*/, parms, new DeepWaterModelOutput(null), null, null, 0);
  }
}
