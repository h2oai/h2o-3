package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import water.Futures;
import water.Key;
import water.Keyed;
import water.fvec.Frame;

public class ModelAsFeatureTransformer<T extends ModelAsFeatureTransformer, M extends Model<M, P, ?>, P extends Model.Parameters> extends FeatureTransformer<T> {
  
  protected final P _params;
  private final String _model_id;
  private Key<M> _model; // can be set once the delegate model has been trained

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
  protected void doPrepare(PipelineContext context) {
    if (_model != null) return;
    
    assert context != null;
    Key<Model> key = Key.make(_model_id == null ? "model_for_"+id() : _model_id); 
    // to train the model, we use the train frame from context by default
    if (_params._train == null) _params._train = context.getTrain().getKey();
    if (_params._valid == null && context.getValid() != null) _params._valid = context.getValid().getKey();
    if (_params._response_column == null) _params._response_column = context._params._response_column;
    if (_params._weights_column == null) _params._weights_column = context._params._weights_column;
    if (_params._offset_column == null) _params._offset_column = context._params._offset_column;
    
    ModelBuilder<M, P, ?> mb = ModelBuilder.make(_params, key);
    _model = (Key<M>) (Key) key; //one of the method used above is not using generics ideally, leading to this unorthodox double-cast.
    mb.trainModel().get();
  }

  @Override
  protected void doCleanup(Futures fs) {
    Keyed.removeQuietly(_model);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    validateTransform();
    return fr == null ? null : getModel().transform(fr);
  }
  
  protected void validateTransform() {
    assert getModel() != null;
  }
}
