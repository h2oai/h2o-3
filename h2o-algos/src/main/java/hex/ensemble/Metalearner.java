package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;

import water.DKV;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.util.Log;

public abstract class Metalearner<B extends ModelBuilder<M, P, ?>, M extends Model<M, P, ?>, P extends Model.Parameters> {

  /**
   * Using an enum to list possible algos is not the greatest idea here
   * as it forces us to hardcode supported algos and creates a dependency to metalearners provided in extensions (XGBoost).
   * Also, it prevents us from loading custom metalearners.
   */
  public enum Algorithm {
    AUTO,
    deeplearning,
    drf,
    gbm,
    glm,
    naivebayes,
    xgboost,
  }

  protected Frame _levelOneTrainingFrame;
  protected Frame _levelOneValidationFrame;
  protected StackedEnsembleModel _model;
  protected StackedEnsembleParameters _parms;
  protected Job _job;
  protected Key<Model> _metalearnerKey;
  protected Job _metalearnerJob;
  protected P _metalearner_parameters;
  protected boolean _hasMetalearnerParams;
  protected long _metalearnerSeed;
  protected long _maxRuntimeSecs;

  void init(Frame levelOneTrainingFrame,
            Frame levelOneValidationFrame,
            P metalearner_parameters,
            StackedEnsembleModel model,
            Job StackedEnsembleJob,
            Key<Model> metalearnerKey,
            Job metalearnerJob,
            StackedEnsembleParameters parms,
            boolean hasMetalearnerParams,
            long metalearnerSeed,
            long maxRuntimeSecs) {

    _levelOneTrainingFrame = levelOneTrainingFrame;
    _levelOneValidationFrame = levelOneValidationFrame;
    _metalearner_parameters = metalearner_parameters;
    _model = model;
    _job = StackedEnsembleJob;
    _metalearnerKey = metalearnerKey;
    _metalearnerJob = metalearnerJob;
    _parms = parms;
    _hasMetalearnerParams = hasMetalearnerParams;
    _metalearnerSeed = metalearnerSeed;
    _maxRuntimeSecs = maxRuntimeSecs;
  }

  void compute() {
    try {
      _model.write_lock(_job);
      B builder = createBuilder();

      if (_hasMetalearnerParams) {
        builder._parms = _metalearner_parameters;
      }
      setCommonParams(builder._parms);
      setCrossValidationParams(builder._parms);
      setCustomParams(builder._parms);

      validateParams(builder._parms);

      builder.setParams(builder._parms);
      builder.init(false);
      Job<M> j = builder.trainModel();
      while (j.isRunning()) {
        try {
          _job.update(j.getWork(), "training metalearner(" + _model._parms._metalearner_algorithm + ")");
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
      }
      Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

      _model._output._metalearner = builder.get();
      _model._dist = _model._output._metalearner._dist;

      _model.doScoreOrCopyMetrics(_job);

      if (_parms._keep_levelone_frame) {
        _model._output._levelone_frame_id = _levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
      }
    } finally {
      cleanup();
      _model.update(_job);
      _model.unlock(_job);
    }
  }

  abstract B createBuilder();

  protected void setCommonParams(P parms) {
    if (parms._seed == -1) { //use _metalearnerSeed only as legacy fallback if not set on metalearner_parameters 
      parms._seed = _metalearnerSeed;
    }
    parms._train = _levelOneTrainingFrame._key;
    parms._valid = (_levelOneValidationFrame == null ? null : _levelOneValidationFrame._key);
    parms._response_column = _model.responseColumn;
    parms._max_runtime_secs = _maxRuntimeSecs;
    parms._weights_column = _model._parms._weights_column;
    parms._offset_column = _model._parms._offset_column;
    parms._main_model_time_budget_factor = _model._parms._main_model_time_budget_factor;
    parms._custom_metric_func = _model._parms._custom_metric_func;
    parms._gainslift_bins = _model._parms._gainslift_bins;
  }

  protected void setCrossValidationParams(P parms) {
    if (_model._parms._metalearner_fold_column == null) {
      parms._nfolds = _model._parms._metalearner_nfolds;
      if (_model._parms._metalearner_nfolds > 1) {
        if (_model._parms._metalearner_fold_assignment == null) {
          parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
        } else {
          parms._fold_assignment = _model._parms._metalearner_fold_assignment;
        }
      }
    } else {
      parms._fold_column = _model._parms._metalearner_fold_column;
    }
  }

  protected void setCustomParams(P parms) { }

  protected void validateParams(P parms) { }

  protected void cleanup() {
    if (!_parms._keep_base_model_predictions) {
      _model.deleteBaseModelPredictions();
    }
    if (!_parms._keep_levelone_frame) {
      DKV.remove(_levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
    }
    if (null != _levelOneValidationFrame) {
      DKV.remove(_levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
    }
  }
}

