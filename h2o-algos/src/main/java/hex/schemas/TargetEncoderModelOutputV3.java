package hex.schemas;

import hex.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;

public class TargetEncoderModelOutputV3 extends ModelOutputSchemaV3<TargetEncoderModel.TargetEncoderOutput, TargetEncoderModelOutputV3> {
  @API(help = "Prior mean value", direction = API.Direction.OUTPUT)
  public double prior_mean;

  public TargetEncoderModelOutputV3() {
  }
  
}
