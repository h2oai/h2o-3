package ai.h2o.targetencoding.pipeline;

import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import hex.pipeline.ModelAsFeatureTransformer;
import hex.pipeline.PipelineContext;
import water.fvec.Frame;

import static ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy.KFold;

public class TargetEncoderFeatureTransformer extends ModelAsFeatureTransformer<TargetEncoderFeatureTransformer, TargetEncoderModel, TargetEncoderParameters> {

  public TargetEncoderFeatureTransformer(TargetEncoderParameters params) {
    super(params);
  }

  public TargetEncoderFeatureTransformer(TargetEncoderParameters params, String modelId) {
    super(params, modelId);
  }

  @Override
  public void prepare(PipelineContext context) {
    if (context != null) {
      // to train the TE model, we use the train frame from context
      if (_params._train == null) {
        _params._train = context._params._train;
      }
      if (_params._data_leakage_handling == KFold && context._params._fold_column != null) {
        _params._fold_column = context._params._fold_column;
      }
    }
    super.prepare(context);
  }
  
  @Override
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    assert type != null;
    assert context != null || type == FrameType.Scoring;
    validateTransform();
    switch (type) {
      case Training:
        if (useFoldTransform(context._params)) {
          return getModel().transformTraining(fr, context._params._cv_fold);
        } else {
          return getModel().transformTraining(fr);
        }
      case Validation:
        if (useFoldTransform(context._params)) {
          return getModel().transformTraining(fr);
        } else {
          return getModel().transform(fr);
        }
      case Scoring:
      default:
        return getModel().transform(fr);
    }
  }

  private boolean useFoldTransform(Model.Parameters params) {
    return params._is_cv_model && getModel()._parms._data_leakage_handling == KFold;
  }
  
}
