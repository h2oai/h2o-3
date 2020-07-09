package ai.h2o.targetencoding;

import hex.schemas.TargetEncoderTransformParametersV3;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

public class TargetEncoderHandler extends Handler {
  private static final double NOISE_LEVEL_UNDEFINED = -1;
  
  @SuppressWarnings("unused")
  public KeyV3.FrameKeyV3 transform(final int version, final TargetEncoderTransformParametersV3 parametersV3) {
    final TargetEncoderTransformParameters parameters = new TargetEncoderTransformParameters();
    parametersV3.fillImpl(parameters);
    final TargetEncoderModel model = parameters._model.get();

    TargetEncoder.DataLeakageHandlingStrategy dataLeakageHandlingStrategy = 
            parameters._data_leakage_handling == null ? model._parms._data_leakage_handling : parameters._data_leakage_handling;
    if(dataLeakageHandlingStrategy == null) dataLeakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.None;
    
    final BlendingParams blendingParams = new BlendingParams(parameters._inflection_point, parameters._smoothing);
    final Frame transformedFrame;
    if (NOISE_LEVEL_UNDEFINED == parameters._noise) {
      transformedFrame = model.transform(parameters._frame.get(), dataLeakageHandlingStrategy,
              parameters._blending, blendingParams, parameters._seed);
    } else {
      transformedFrame = model.transform(parameters._frame.get(), dataLeakageHandlingStrategy,
              parameters._noise,parameters._blending, blendingParams, parameters._seed);
    }

    return new KeyV3.FrameKeyV3(transformedFrame._key);
  }
}
