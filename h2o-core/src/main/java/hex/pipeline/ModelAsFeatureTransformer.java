package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import water.Key;
import water.fvec.Frame;

public class ModelAsFeatureTransformer<T extends ModelAsFeatureTransformer, M extends Model<M, P, ?>, P extends Model.Parameters> extends FeatureTransformer<T> {
  
  protected final P _params;
  protected final String _model_id;
  protected Key<M> _model; // can be set once the delegate model has been trained

  public ModelAsFeatureTransformer(P params) {
    this(params, null);
  }

  public ModelAsFeatureTransformer(P params, String modelId) {
    _params = params;
    _model_id = modelId;
    if (_params != null) {
      excludeColumns(_params.getNonPredictors()); //FIXME: shouldn't it be in prepare?
    }
  }

  public ModelAsFeatureTransformer(Key<M> model) {
    this(null, null);
    _model = model;
  }

  public M getModel() {
    return _model == null ? null : _model.get();
  }

  @Override
  public void prepare(PipelineContext context) {
    if (_model != null) return;
    
    _model = Key.make(_model_id); //todo: better default name with algo, pipeline index... although maybe should be set in Pipeline
    ModelBuilder<M, P, ?> mb = ModelBuilder.make(_params, (Key<Model>)_model);
    mb.trainModel().get();
  }

  @Override
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    validateTransform();
    return fr == null ? null : getModel().transform(fr);
  }
  
  protected void validateTransform() {
    assert getModel() != null;
  }
}
