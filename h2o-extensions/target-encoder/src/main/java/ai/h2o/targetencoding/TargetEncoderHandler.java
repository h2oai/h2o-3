package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy;
import hex.schemas.TargetEncoderTransformParametersV3;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

public class TargetEncoderHandler extends Handler {
  @SuppressWarnings("unused")
  public KeyV3.FrameKeyV3 transform(final int version, final TargetEncoderTransformParametersV3 parametersV3) {
    final TargetEncoderTransformParameters parameters = new TargetEncoderTransformParameters();
    parametersV3.fillImpl(parameters);
    final TargetEncoderModel model = parameters._model.get();

    DataLeakageHandlingStrategy dataLeakageHandlingStrategy = 
            parameters._data_leakage_handling != null ? parameters._data_leakage_handling 
            : model._parms._data_leakage_handling != null ? model._parms._data_leakage_handling
            : DataLeakageHandlingStrategy.None;
    
    final BlendingParams blendingParams = parameters._blending 
            ? new BlendingParams(parameters._inflection_point, parameters._smoothing)
            : null;
    
    final Frame transformedFrame = model.transform(
            parameters._frame.get(),
            dataLeakageHandlingStrategy,
            blendingParams,
            parameters._noise,
            parameters._seed);

    return new KeyV3.FrameKeyV3(transformedFrame._key);
  }
}
