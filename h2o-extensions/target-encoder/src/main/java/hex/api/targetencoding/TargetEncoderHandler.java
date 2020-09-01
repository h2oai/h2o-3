package hex.api.targetencoding;

import ai.h2o.targetencoding.BlendingParams;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.schemas.TargetEncoderTransformParametersV3;
import water.Iced;
import water.Key;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

public class TargetEncoderHandler extends Handler {
  @SuppressWarnings("unused")
  public KeyV3.FrameKeyV3 transform(final int version, final TargetEncoderTransformParametersV3 parametersV3) {
    final TargetEncoderTransformParameters parameters = new TargetEncoderTransformParameters();
    parametersV3.fillImpl(parameters);
    final TargetEncoderModel model = parameters._model.get();

    final boolean asTraining = parameters._as_training;
    final double noise = parameters._noise < -1  ? model._parms._noise : parameters._noise;
    final BlendingParams blendingParams = parameters._blending
            ? new BlendingParams(
                parameters._inflection_point < 0 ? model._parms._inflection_point : parameters._inflection_point,
                parameters._smoothing < 0 ? model._parms._smoothing : parameters._smoothing
              )
            : null;
    
    final Frame transformedFrame = model.transform(
            parameters._frame.get(),
            asTraining,
            TargetEncoderModel.NO_FOLD, blendingParams,
            noise
    );

    return new KeyV3.FrameKeyV3(transformedFrame._key);
  }

  public static class TargetEncoderTransformParameters extends Iced<TargetEncoderTransformParameters> {
    public Key<TargetEncoderModel> _model;
    public Key<Frame> _frame;
    public boolean _as_training;
    public boolean _blending;
    public double _inflection_point = -1;
    public double _smoothing = -1;
    public double _noise = -2; // use -2 for not-provided (-1 already means AUTO, and 0 means disabled). 
  }

}
