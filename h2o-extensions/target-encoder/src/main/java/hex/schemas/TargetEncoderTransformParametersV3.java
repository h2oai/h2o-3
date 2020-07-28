package hex.schemas;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.api.targetencoding.TargetEncoderHandler.TargetEncoderTransformParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TargetEncoderTransformParametersV3 extends SchemaV3<TargetEncoderTransformParameters, TargetEncoderTransformParametersV3> {

  @API(help = "Target Encoder model to use.")
  public KeyV3.ModelKeyV3<TargetEncoderModel> model;
  @API(help = "Frame to transform.")
  public KeyV3.FrameKeyV3 frame;
  @API(help = "Enables or disables blending. Defaults to the value assigned at model creation.")
  public boolean blending;
  @API(help = "Inflection point. Defaults to the value assigned at model creation.")
  public double inflection_point;
  @API(help = "Smoothing. Defaults to the value assigned at model creation.")
  public double smoothing;
  @API(help = "Noise. Defaults to the value assigned at model creation.")
  public double noise;

}
