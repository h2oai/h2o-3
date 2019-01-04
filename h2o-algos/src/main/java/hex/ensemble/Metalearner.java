package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import water.DKV;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;

public abstract class Metalearner<B extends ModelBuilder<M, P, ?>, M extends Model<M, P, ?>, P extends Model.Parameters> {

  public enum Algorithm {
    AUTO,
    deeplearning,
    drf,
    gbm,
    glm,
  }

  static Algorithm getActualMetalearnerAlgo(Algorithm algo) {
    switch (algo) {
      case AUTO:
        return Algorithm.glm;
      case gbm:
      case glm:
      case drf:
      case deeplearning:
        return algo;
      default:
        return null;
    }
  }
  
  static Model.Parameters createParameters(Algorithm algo) {
    switch (algo) {
      case deeplearning:
        return new DeepLearningModel.DeepLearningParameters();
      case drf:
        return  new DRFModel.DRFParameters();
      case gbm:
        return new GBMModel.GBMParameters();
      case glm:
      case AUTO:
      default:  
        return new GLMModel.GLMParameters();
    }
  }
  
  static Metalearner createInstance(Algorithm algo) {
    switch (algo) {
      case deeplearning:
        return new DLMetalearner();
      case drf:
        return new DRFMetalearner();
      case gbm:
        return new GBMMetalearner();
      case glm:
        return new GLMMetalearner();
      case AUTO:
      default:
        return new AUTOMetalearner();
    }
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

  void init(Frame levelOneTrainingFrame,
            Frame levelOneValidationFrame,
            P metalearner_parameters,
            StackedEnsembleModel model,
            Job StackedEnsembleJob,
            Key<Model> metalearnerKey,
            Job metalearnerJob,
            StackedEnsembleParameters parms,
            boolean hasMetalearnerParams,
            long metalearnerSeed) {

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

  }

  void compute() {
    try {
      B builder = createBuilder();

      if (_hasMetalearnerParams) {
        builder._parms = _metalearner_parameters;
      }
      setCommonParams(builder._parms);
      setCrossValidationParams(builder._parms);
      setCustomParams(builder._parms);

      builder.init(false);
      Job<M> j = builder.trainModel();
      while (j.isRunning()) {
        try {
          _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm + ")");
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
      }
      Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

      _model._output._metalearner = builder.get();
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

class GBMMetalearner extends Metalearner<GBM, GBMModel, GBMModel.GBMParameters> {
  @Override
  GBM createBuilder() {
    return ModelBuilder.make("GBM", _metalearnerJob, _metalearnerKey);
  }
}

class GLMMetalearner extends Metalearner<GLM, GLMModel, GLMModel.GLMParameters> {
  @Override
  GLM createBuilder() {
    return ModelBuilder.make("GLM", _metalearnerJob, _metalearnerKey);
  }

  @Override
  protected void setCustomParams(GLMModel.GLMParameters parms) {
    if (_model.modelCategory == ModelCategory.Regression) {
      parms._family = GLMModel.GLMParameters.Family.gaussian;
    } else if (_model.modelCategory == ModelCategory.Binomial) {
      parms._family = GLMModel.GLMParameters.Family.binomial;
    } else if (_model.modelCategory == ModelCategory.Multinomial) {
      parms._family = GLMModel.GLMParameters.Family.multinomial;
    } else {
      throw new H2OIllegalArgumentException("Family " + _model.modelCategory + "  is not supported.");
    }
  }
}

class DRFMetalearner extends Metalearner<DRF, DRFModel, DRFModel.DRFParameters> {
  @Override
  DRF createBuilder() {
    return ModelBuilder.make("DRF", _metalearnerJob, _metalearnerKey);
  }
}

class DLMetalearner extends Metalearner<DeepLearning, DeepLearningModel, DeepLearningModel.DeepLearningParameters> {
  @Override
  DeepLearning createBuilder() {
    return ModelBuilder.make("DeepLearning", _metalearnerJob, _metalearnerKey);
  }
}

class AUTOMetalearner extends GLMMetalearner {

  @Override
  protected void setCustomParams(GLMModel.GLMParameters parms) {
    //add GLM custom params
    super.setCustomParams(parms);
    
    //specific to AUTO mode
    parms._non_negative = true;
    //parms._alpha = new double[] {0.0, 0.25, 0.5, 0.75, 1.0};

    // Enable lambda search if a validation frame is passed in to get a better GLM fit.
    // Since we are also using non_negative to true, we should also set early_stopping = false.
    if (parms._valid != null) {
      parms._lambda_search = true;
      parms._early_stopping = false;
    }
  }
}

