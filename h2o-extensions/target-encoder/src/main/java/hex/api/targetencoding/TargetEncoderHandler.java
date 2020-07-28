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

//    final boolean useBlending = parameters._blending == null ? model._parms._blending : parameters._blending;
//    final double noise = parameters._noise == null ? model._parms._noise : parameters._noise;
//    final double inflectionPoint = parameters._inflection_point == null ? model._parms._inflection_point : parameters._inflection_point;
//    final double smoothing = parameters._smoothing == null ? model._parms._smoothing : parameters._smoothing;
    final boolean useBlending = parameters._blending;
    final double noise = parameters._noise < -1  ? model._parms._noise : parameters._noise;
    final double inflectionPoint = parameters._inflection_point < 0 ? model._parms._inflection_point : parameters._inflection_point;
    final double smoothing = parameters._smoothing < 0 ? model._parms._smoothing : parameters._smoothing;
    
    final BlendingParams blendingParams = useBlending
            ? new BlendingParams(inflectionPoint, smoothing)
            : null;
    
    final Frame transformedFrame = model.transform(
            parameters._frame.get(),
            blendingParams,
            noise
    );

    return new KeyV3.FrameKeyV3(transformedFrame._key);
  }

  public static class TargetEncoderTransformParameters extends Iced<TargetEncoderTransformParameters> {
    public Key<TargetEncoderModel> _model;
    public Key<Frame> _frame;
    public boolean _blending;
    public double _inflection_point = -1;
    public double _smoothing = -1;
    public double _noise = -2; // use -2 for not-provided (-1 already means AUTO, and 0 means disabled). 
  }

}
