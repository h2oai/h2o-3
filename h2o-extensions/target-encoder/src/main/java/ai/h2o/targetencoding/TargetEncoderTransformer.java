package ai.h2o.targetencoding;

import hex.Model;
import hex.DataTransformer;
import water.DKV;
import water.Futures;
import water.Key;
import water.fvec.Frame;

import java.util.Objects;

import static ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy.*;

public class TargetEncoderTransformer extends DataTransformer<TargetEncoderTransformer> {
    
  private TargetEncoderModel _targetEncoder;

  public TargetEncoderTransformer(TargetEncoderModel targetEncoder) {
    super(Key.make(Objects.toString(targetEncoder._key)+"_transformer"));
    this._targetEncoder = targetEncoder;
    DKV.put(this);
  }

  @Override
  public Frame transform(Frame fr, Model.Parameters params, Stage stage) {
    switch (stage) {
      case Training:
        if (useFoldTransform(params)) {
          return _targetEncoder.transformTraining(fr, params._cv_fold);
        } else {
          return _targetEncoder.transformTraining(fr);
        }
      case Validation:
        if (useFoldTransform(params)) {
          return _targetEncoder.transformTraining(fr);
        } else {
          return _targetEncoder.transform(fr);
        }
      case Scoring:
      default:  
        return _targetEncoder.transform(fr);
    }
  }

  @Override
  public Model asModel() {
    return _targetEncoder;
  }
    
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (cascade && _targetEncoder != null) _targetEncoder.remove();
    return super.remove_impl(fs, cascade);
  }

  private boolean useFoldTransform(Model.Parameters params) {
    return params._is_cv_model && _targetEncoder._parms._data_leakage_handling == KFold;
  }
}
