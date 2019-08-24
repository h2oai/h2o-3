package ai.h2o.targetencoding;

import hex.schemas.TargetEncoderTransformParametersV3;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

public class TargetEncoderHandler extends Handler {
  private static final double NOISE_LEVEL_UNDEFINED = -1;

  public KeyV3.FrameKeyV3 transform(final int version, final TargetEncoderTransformParametersV3 parametersV3) {
    final TargetEncoderTransformParameters parameters = new TargetEncoderTransformParameters();
    parametersV3.fillImpl(parameters);

    final TargetEncoderModel model = parameters._model.get();
    final Frame transformedFrame;
    if (NOISE_LEVEL_UNDEFINED == parameters._noise) {
      transformedFrame = model.transform(parameters._frame.get(), parameters._data_leakage_handling.getVal(), parameters._seed);
    } else {
      transformedFrame = model.transform(parameters._frame.get(), parameters._data_leakage_handling.getVal(), parameters._noise, parameters._seed);
    }

    return new KeyV3.FrameKeyV3(transformedFrame._key);
  }
}
