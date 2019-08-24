package hex.schemas;

import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderTransformParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class TargetEncoderTransformParametersV3 extends SchemaV3<TargetEncoderTransformParameters, TargetEncoderTransformParametersV3> {
  
  @API(help =  "")
  public KeyV3.ModelKeyV3<TargetEncoderModel> model;
  @API(help =  "")
  public long seed;
  @API(help = "Data leakage handling strategy. Default to None.", values = {"None", "KFold", "LeaveOneOut"})
  public TargetEncoder.DataLeakageHandlingStrategy data_leakage_handling;
  @API(help =  "")
  public double noise;
  @API(help =  "")
  public KeyV3.FrameKeyV3 frame;

  @Override
  public TargetEncoderTransformParameters fillImpl(TargetEncoderTransformParameters impl) {
    super.fillImpl(impl);
    return impl;
  }
}
