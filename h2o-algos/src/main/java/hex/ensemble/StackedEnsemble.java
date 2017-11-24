package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.StackedEnsembleModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import water.DKV;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsemble extends ModelBuilder<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {
  StackedEnsembleDriver _driver;
  // The in-progress model being built
  protected StackedEnsembleModel _model;

  public StackedEnsemble(StackedEnsembleModel.StackedEnsembleParameters parms) { super(parms); init(false); }
  public StackedEnsemble(boolean startup_once) { super(new StackedEnsembleModel.StackedEnsembleParameters(),startup_once); }

  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial
    };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }

  @Override public boolean isSupervised() { return true; }

  @Override protected StackedEnsembleDriver trainModelImpl() { return _driver = new StackedEnsembleDriver(); }

  @Override public boolean haveMojo() { return true; }

  public static void addModelPredictionsToLevelOneFrame(Model aModel, Frame aModelsPredictions, Frame levelOneFrame) {
    if (aModel._output.isBinomialClassifier()) {
      // GLM uses a different column name than the other algos
      Vec preds = aModelsPredictions.vec(2); // Predictions column names have been changed. . .
      levelOneFrame.add(aModel._key.toString(), preds);
    } else if(aModel._output.isMultinomialClassifier()) { //Multinomial
      levelOneFrame.add(aModelsPredictions);
    } else if (aModel._output.isAutoencoder()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack autoencoders: " + aModel._key);
    } else if (!aModel._output.isSupervised()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack unsupervised models: " + aModel._key);
    } else {
      levelOneFrame.add(aModel._key.toString(), aModelsPredictions.vec("predict"));
    }
  }

  private class StackedEnsembleDriver extends Driver {

    /**
     * Prepare a "level one" frame for a given set of models, predictions-frames and actuals.  Used for preparing
     * training and validation frames for the metalearning step, and could also be used for bulk predictions for
     * a StackedEnsemble.
     */
    private Frame prepareLevelOneFrame(String levelOneKey, Model[] baseModels, Frame[] baseModelPredictions, Frame actuals) {
      if (null == baseModels) throw new H2OIllegalArgumentException("Base models array is null.");
      if (null == baseModelPredictions) throw new H2OIllegalArgumentException("Base model predictions array is null.");
      if (baseModels.length == 0) throw new H2OIllegalArgumentException("Base models array is empty.");
      if (baseModelPredictions.length == 0) throw new H2OIllegalArgumentException("Base model predictions array is empty.");
      if (baseModels.length != baseModelPredictions.length) throw new H2OIllegalArgumentException("Base models and prediction arrays are different lengths.");

      if (null == levelOneKey) levelOneKey = "levelone_" + _model._key.toString();
      Frame levelOneFrame = new Frame(Key.<Frame>make(levelOneKey));

      for (int i = 0; i < baseModels.length; i++) {
        Model baseModel = baseModels[i];
        Frame baseModelPreds = baseModelPredictions[i];

        if (null == baseModel) {
          Log.warn("Failed to find base model; skipping: " + baseModels[i]);
          continue;
        }
        if (null == baseModelPreds) {
          Log.warn("Failed to find base model " + baseModel + " predictions; skipping: " + baseModelPreds._key);
          continue;
        }
        StackedEnsemble.addModelPredictionsToLevelOneFrame(baseModel, baseModelPreds, levelOneFrame);
      }
      // Add metalearner_fold_column to level one frame if it exists
      if (_model._parms._metalearner_fold_column != null) {
        levelOneFrame.add(_model._parms._metalearner_fold_column, actuals.vec(_model._parms._metalearner_fold_column));
      }
      // Add response column to level one frame
      levelOneFrame.add(_model.responseColumn, actuals.vec(_model.responseColumn));

      // TODO: what if we're running multiple in parallel and have a name collision?

      Frame old = DKV.getGet(levelOneFrame._key);
      if (old != null && old instanceof Frame) {
        Frame oldFrame = (Frame)old;
        // Remove ALL the columns so we don't delete them in remove_impl.  Their
        // lifetime is controlled by their model.
        oldFrame.removeAll();
        oldFrame.write_lock(_job);
        oldFrame.update(_job);
        oldFrame.unlock(_job);
      }

      levelOneFrame.delete_and_lock(_job);
      levelOneFrame.unlock(_job);
      Log.info("Finished creating \"level one\" frame for stacking: " + levelOneFrame.toString());
      DKV.put(levelOneFrame);
      return levelOneFrame;
    }

    /**
     * Prepare the "level one" frame for training the metalearner on a list of cross-validated models
     * which were trained with _keep_cross_validation_predictions = true.
     */
    private Frame prepareTrainingLevelOneFrame(StackedEnsembleModel.StackedEnsembleParameters parms) {
      // TODO: allow the user to name the level one frame
      String levelOneKey = "levelone_training_" + _model._key.toString();

      List<Model> baseModels = new ArrayList<>();
      List<Frame> baseModelPredictions = new ArrayList<>();
      for (Key<Model> k : parms._base_models) {
        Model aModel = DKV.getGet(k);
        if (null == aModel)
          throw new H2OIllegalArgumentException("Failed to find base model: " + k);

        if (null == aModel._output._cross_validation_holdout_predictions_frame_id)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame id. . .  Looks like keep_cross_validation_predictions wasn't set when building the models.");

        Frame aFrame = DKV.getGet(aModel._output._cross_validation_holdout_predictions_frame_id);

        if (null == aFrame)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame. . .  Looks like keep_cross_validation_predictions wasn't set when building the models, or the frame was deleted.");

        baseModels.add(aModel);
        if (!aModel._output.isMultinomialClassifier()) {
            baseModelPredictions.add(aFrame);
        } else {
            List<String> predColNames= new ArrayList<>(Arrays.asList(aFrame.names()));
            predColNames.remove("predict");
            String[] multClassNames  = predColNames.toArray(new String[0]);
            baseModelPredictions.add(aFrame.subframe(multClassNames));
        }
      }

      return prepareLevelOneFrame(levelOneKey, baseModels.toArray(new Model[0]), baseModelPredictions.toArray(new Frame[0]), _model._parms.train());
    }

    private Key<Frame> buildPredsKey(Key model_key, long model_checksum, Key frame_key, long frame_checksum) {
      return Key.make("preds_" + model_checksum + "_on_" + frame_checksum);
    }

    private Key<Frame> buildPredsKey(Model model, Frame frame) {
      return frame==null || model == null ? null : buildPredsKey(model._key, model.checksum(), frame._key, frame.checksum());
    }

    /**
     * Prepare a "level one" frame for a given set of models and actuals.  Used for preparing validation frames
     * for the metalearning step, and could also be used for bulk predictions for a StackedEnsemble.
     */
    private Frame prepareValidationLevelOneFrame(String levelOneKey, Key<Model>[] baseModelKeys, Frame actuals) {
      List<Model> baseModels = new ArrayList<>();
      List<Frame> baseModelPredictions = new ArrayList<>();

      for (Key<Model> k : baseModelKeys) {
        Model aModel = DKV.getGet(k);
        if (null == aModel)
          throw new H2OIllegalArgumentException("Failed to find base model: " + k);

        Key<Frame> predsKey = buildPredsKey(aModel, actuals);
        Frame aPred = aModel.score(actuals, predsKey.toString()); // TODO: cache predictions

        baseModels.add(aModel);
        if (!aModel._output.isMultinomialClassifier()) {
          baseModelPredictions.add(aPred);
        } else {
          List<String> predColNames= new ArrayList<>(Arrays.asList(aPred.names()));
          predColNames.remove("predict");
          String[] multClassNames  = predColNames.toArray(new String[0]);
          baseModelPredictions.add(aPred.subframe(multClassNames));
        }
      }

      Frame levelOne = prepareLevelOneFrame(levelOneKey, baseModels.toArray(new Model[0]), baseModelPredictions.toArray(new Frame[0]), actuals);

      // remove baseModelPredictions frames and all the non-preds vecs from the DKV
      for (Frame aPred : baseModelPredictions)
        Frame.deleteTempFrameAndItsNonSharedVecs(aPred, levelOne);

      return levelOne;
    }

    public void computeImpl() {
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(StackedEnsemble.this);

      _model = new StackedEnsembleModel(dest(), _parms, new StackedEnsembleModel.StackedEnsembleOutput(StackedEnsemble.this));
      _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)

      _model.checkAndInheritModelProperties();

      Frame levelOneTrainingFrame = prepareTrainingLevelOneFrame(_parms);
      Frame levelOneValidationFrame = null;
      if (_model._parms.valid() != null) {
        String levelOneKey = "levelone_validation_" + _model._key.toString();
        levelOneValidationFrame =
                prepareValidationLevelOneFrame(levelOneKey,
                                     _model._parms._base_models,
                                     _model._parms.valid());
      }

      //Compute metalearner
      computeMetaLearner(levelOneTrainingFrame, levelOneValidationFrame, _model._parms._metalearner_algorithm);


    } // computeImpl

    private void computeMetaLearner(Frame levelOneTrainingFrame, Frame levelOneValidationFrame, StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm metalearner_algo) {
        // Train the metalearner model
        // Default Job for just this training

        Key<Model> metalearnerKey = Key.<Model>make("metalearner_" + _model._parms._metalearner_algorithm + "_" + _model._key);
        Job job = new Job<>(metalearnerKey, _model._parms._metalearner_algorithm.toString(),
                "StackingEnsemble metalearner (" + _model._parms._metalearner_algorithm + ")");

        GLM metaGLMBuilder;
        GBM metaGBMBuilder;
        DRF metaDRFBuilder;
        DeepLearning metaDeepLearningBuilder;

        if (metalearner_algo.equals(StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm.AUTO)) {

          //GLM Metalearner
          metaGLMBuilder = ModelBuilder.make("GLM", job, metalearnerKey);
          metaGLMBuilder._parms._non_negative = true;
          //metaGLMBuilder._parms._alpha = new double[] {0.0, 0.25, 0.5, 0.75, 1.0};
          metaGLMBuilder._parms._train = levelOneTrainingFrame._key;
          metaGLMBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
          metaGLMBuilder._parms._response_column = _model.responseColumn;
          if (_model._parms._metalearner_fold_column == null) {
            metaGLMBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
            if (_model._parms._metalearner_nfolds > 1) {
              if (_model._parms._metalearner_fold_assignment == null) {
                metaGLMBuilder._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
              } else {
                metaGLMBuilder._parms._fold_assignment = _model._parms._metalearner_fold_assignment;  //cross-validation of the metalearner
              }
            }
          } else {
            metaGLMBuilder._parms._fold_column = _model._parms._metalearner_fold_column;  //cross-validation of the metalearner
          }

          // Enable lambda search if a validation frame is passed in to get a better GLM fit.
          // Since we are also using non_negative to true, we should also set early_stopping = false.
          if (metaGLMBuilder._parms._valid != null) {
            metaGLMBuilder._parms._lambda_search = true;
            metaGLMBuilder._parms._early_stopping = false;
          }
          if (_model.modelCategory == ModelCategory.Regression) {
            metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.gaussian;
          } else if (_model.modelCategory == ModelCategory.Binomial) {
            metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.binomial;
          } else if (_model.modelCategory == ModelCategory.Multinomial) {
            metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.multinomial;
          } else {
            throw new H2OIllegalArgumentException("Family " + _model.modelCategory + "  is not supported.");
          }

          metaGLMBuilder.init(false);

          Job<GLMModel> j = metaGLMBuilder.trainModel();

          while (j.isRunning()) {
            try {
              _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm +")");
              Thread.sleep(100);
            }
            catch (InterruptedException e) {}
          }

          Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

          _model._output._metalearner = metaGLMBuilder.get();
          _model.doScoreOrCopyMetrics(_job);
          if (_parms._keep_levelone_frame) {
            _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
          } else{
            DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
          }
          if (null != levelOneValidationFrame) {
            DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
          }
          _model.update(_job);
          _model.unlock(_job);

        } else if (metalearner_algo.equals(StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm.glm)) {

        //GLM Metalearner
        metaGLMBuilder = ModelBuilder.make("GLM", job, metalearnerKey);
        metaGLMBuilder._parms._train = levelOneTrainingFrame._key;
        metaGLMBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
        metaGLMBuilder._parms._response_column = _model.responseColumn;
        metaGLMBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
          if (_model._parms._metalearner_fold_column == null) {
            metaGLMBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
            if (_model._parms._metalearner_nfolds > 1) {
              if (_model._parms._metalearner_fold_assignment == null) {
                metaGLMBuilder._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
              } else {
                metaGLMBuilder._parms._fold_assignment = _model._parms._metalearner_fold_assignment;  //cross-validation of the metalearner
              }
            }
          } else {
            metaGLMBuilder._parms._fold_column = _model._parms._metalearner_fold_column;  //cross-validation of the metalearner
          }
        // TODO: Possibly add this back in and turn on early stopping in all metalearners when a validation frame is present
        // Enable lambda search if a validation frame is passed in to get a better GLM fit.
         /*
          // if (metaGLMBuilder._parms._valid != null) {
          metaGLMBuilder._parms._lambda_search = true;
        }
        */
        if (_model.modelCategory == ModelCategory.Regression) {
          metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.gaussian;
        } else if (_model.modelCategory == ModelCategory.Binomial) {
          metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.binomial;
        } else if (_model.modelCategory == ModelCategory.Multinomial) {
          metaGLMBuilder._parms._family = GLMModel.GLMParameters.Family.multinomial;
        } else {
          throw new H2OIllegalArgumentException("Family " + _model.modelCategory + "  is not supported.");
        }

        metaGLMBuilder.init(false);

        Job<GLMModel> j = metaGLMBuilder.trainModel();

        while (j.isRunning()) {
          try {
            _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm + ")");
            Thread.sleep(100);
          } catch (InterruptedException e) {
          }
        }

        Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

        _model._output._metalearner = metaGLMBuilder.get();
        _model.doScoreOrCopyMetrics(_job);
        if (_parms._keep_levelone_frame) {
          _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
        } else {
          DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
        }
        if (null != levelOneValidationFrame) {
          DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
        }
        _model.update(_job);
        _model.unlock(_job);

      } else if (metalearner_algo.equals(StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm.gbm)) {

          //GBM Metalearner
          metaGBMBuilder = ModelBuilder.make("GBM", job, metalearnerKey);
          metaGBMBuilder._parms._train = levelOneTrainingFrame._key;
          metaGBMBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
          metaGBMBuilder._parms._response_column = _model.responseColumn;
          metaGBMBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
          if (_model._parms._metalearner_fold_column == null) {
            metaGBMBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
            if (_model._parms._metalearner_nfolds > 1) {
              if (_model._parms._metalearner_fold_assignment == null) {
                metaGBMBuilder._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
              } else {
                metaGBMBuilder._parms._fold_assignment = _model._parms._metalearner_fold_assignment;  //cross-validation of the metalearner
              }
            }
          } else {
            metaGBMBuilder._parms._fold_column = _model._parms._metalearner_fold_column;  //cross-validation of the metalearner
          }

          metaGBMBuilder.init(false);

          Job<GBMModel> j = metaGBMBuilder.trainModel();

          while (j.isRunning()) {
            try {
              _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm +")");
              Thread.sleep(100);
            }
            catch (InterruptedException e) {}
          }

          Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

          _model._output._metalearner = metaGBMBuilder.get();
          _model.doScoreOrCopyMetrics(_job);
          if (_parms._keep_levelone_frame) {
            _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
          } else{
            DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
          }
          if (null != levelOneValidationFrame) {
            DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
          }
          _model.update(_job);
          _model.unlock(_job);

        } else if (metalearner_algo.equals(StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm.drf)) {

          //DRF Metalearner
          metaDRFBuilder = ModelBuilder.make("DRF", job, metalearnerKey);
          metaDRFBuilder._parms._train = levelOneTrainingFrame._key;
          metaDRFBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
          metaDRFBuilder._parms._response_column = _model.responseColumn;
          metaDRFBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
          if (_model._parms._metalearner_fold_column == null) {
            metaDRFBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
            if (_model._parms._metalearner_nfolds > 1) {
              if (_model._parms._metalearner_fold_assignment == null) {
                metaDRFBuilder._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
              } else {
                metaDRFBuilder._parms._fold_assignment = _model._parms._metalearner_fold_assignment;  //cross-validation of the metalearner
              }
            }
          } else {
            metaDRFBuilder._parms._fold_column = _model._parms._metalearner_fold_column;  //cross-validation of the metalearner
          }

          metaDRFBuilder.init(false);

          Job<DRFModel> j = metaDRFBuilder.trainModel();

          while (j.isRunning()) {
            try {
              _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm +")");
              Thread.sleep(100);
            }
            catch (InterruptedException e) {}
          }

          Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

          _model._output._metalearner = metaDRFBuilder.get();
          _model.doScoreOrCopyMetrics(_job);
          if (_parms._keep_levelone_frame) {
            _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
          } else{
            DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
          }
          if (null != levelOneValidationFrame) {
            DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
          }
          _model.update(_job);
          _model.unlock(_job);

        } else if (metalearner_algo.equals(StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm.deeplearning)) {

          //DeepLearning Metalearner
          metaDeepLearningBuilder = ModelBuilder.make("DeepLearning", job, metalearnerKey);
          metaDeepLearningBuilder._parms._train = levelOneTrainingFrame._key;
          metaDeepLearningBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
          metaDeepLearningBuilder._parms._response_column = _model.responseColumn;
          metaDeepLearningBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
          if (_model._parms._metalearner_fold_column == null) {
            metaDeepLearningBuilder._parms._nfolds = _model._parms._metalearner_nfolds;  //cross-validation of the metalearner
            if (_model._parms._metalearner_nfolds > 1) {
              if (_model._parms._metalearner_fold_assignment == null) {
                metaDeepLearningBuilder._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
              } else {
                metaDeepLearningBuilder._parms._fold_assignment = _model._parms._metalearner_fold_assignment;  //cross-validation of the metalearner
              }
            }
          } else {
            metaDeepLearningBuilder._parms._fold_column = _model._parms._metalearner_fold_column;  //cross-validation of the metalearner
          }

          metaDeepLearningBuilder.init(false);

          Job<DeepLearningModel> j = metaDeepLearningBuilder.trainModel();

          while (j.isRunning()) {
            try {
              _job.update(j._work, "training metalearner(" + _model._parms._metalearner_algorithm +")");
              Thread.sleep(100);
            }
            catch (InterruptedException e) {}
          }

          Log.info("Finished training metalearner model(" + _model._parms._metalearner_algorithm + ").");

          _model._output._metalearner = metaDeepLearningBuilder.get();
          _model.doScoreOrCopyMetrics(_job);
          if (_parms._keep_levelone_frame) {
            _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
          } else{
            DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
          }
          if (null != levelOneValidationFrame) {
            DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
          }
          _model.update(_job);
          _model.unlock(_job);
        } else {
          throw new H2OIllegalArgumentException("Invalid `metalearner_algorithm`. Passed in " + _model._parms._metalearner_algorithm + " " +
                  "but must be one of 'glm', 'gbm', 'randomForest', or 'deeplearning'.");
        }
      }
    }
  }
