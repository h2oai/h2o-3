package hex.pipeline.transformers;

import hex.Model;
import hex.ModelBuilder;
import hex.pipeline.PipelineContext;
import water.Futures;
import water.Key;
import water.Keyed;
import water.fvec.Frame;

public class ModelAsFeatureTransformer<T extends ModelAsFeatureTransformer, M extends Model<M, MP, ?>, MP extends Model.Parameters> extends FeatureTransformer<T> {
  
  protected final MP _params;
  private Key<M> _modelKey; 

  public ModelAsFeatureTransformer(MP params) {
    this(params, null);
  }

  public ModelAsFeatureTransformer(MP params, Key<M> modelKey) {
    _params = params;
    _modelKey = modelKey;
    if (_params != null) {
      excludeColumns(_params.getNonPredictors()); //FIXME: shouldn't it be in prepare?
    }
  }

  public M getModel() {
    return _modelKey == null ? null : _modelKey.get();
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    if (_modelKey == null) _modelKey = Key.make(id()+"_model");
    if (getModel() != null) return;;
    prepareModelParams(context);
    ModelBuilder<M, MP, ?> mb = ModelBuilder.make(_params, _modelKey);
    mb.trainModel().get();
  }
  
  protected void prepareModelParams(PipelineContext context) {
    assert context != null;
    // to train the model, we use the train frame from context by default

    if (_params._train == null) { // do not propagate params from context otherwise as they were defined for the default training frame
      _params._train = context.getTrain().getKey();
      if (_params._valid == null && context.getValid() != null) _params._valid = context.getValid().getKey();
      if (_params._response_column == null) _params._response_column = context._params._response_column;
      if (_params._weights_column == null) _params._weights_column = context._params._weights_column;
      if (_params._offset_column == null) _params._offset_column = context._params._offset_column;
      if (_params._ignored_columns == null) _params._ignored_columns = context._params._ignored_columns;
      if (isCVSensitive()) {
        MP defaults = ModelBuilder.makeParameters(_params.algoName());
        if (_params._fold_column == null) _params._fold_column = context._params._fold_column;
        if (_params._fold_assignment == defaults._fold_assignment)
          _params._fold_assignment = context._params._fold_assignment;
        if (_params._nfolds == defaults._nfolds) _params._nfolds = context._params._nfolds;
      }
    }
  }

  @Override
  protected void doCleanup(Futures fs) {
    Keyed.removeQuietly(_modelKey);
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
