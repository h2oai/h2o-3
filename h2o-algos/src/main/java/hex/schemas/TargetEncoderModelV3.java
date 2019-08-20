package hex.schemas;


import hex.targetencoding.TargetEncoderModel;
import water.api.schemas3.ModelSchemaV3;

public class TargetEncoderModelV3 extends ModelSchemaV3<TargetEncoderModel, TargetEncoderModelV3, TargetEncoderModel.TargetEncoderParameters, TargetEncoderV3.TargetEncoderParametersV3,
        TargetEncoderModel.TargetEncoderOutput, TargetEncoderModelOutputV3> {

  @Override
  public TargetEncoderModelOutputV3 createOutputSchema() {
    return new TargetEncoderModelOutputV3();
  }

  @Override
  public TargetEncoderV3.TargetEncoderParametersV3 createParametersSchema() {
    return new TargetEncoderV3.TargetEncoderParametersV3();
  }
}
