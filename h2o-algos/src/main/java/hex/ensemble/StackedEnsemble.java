package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.StackedEnsembleModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
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

  /*
  public StackedEnsemble(Key selfKey, StackedEnsembleModel.StackedEnsembleParameters parms, StackedEnsemble job) {
    super(selfKey, parms, job == null ?
            new StackedEnsembleModel.StackedEnsembleOutput():
            new StackedEnsembleModel.StackedEnsembleOutput(job));
  }
  */

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
      // GLM uses a different column name than the other algos, yay!
      Vec preds = aModelsPredictions.vec(2); // Predictions column names have been changed. . .
      levelOneFrame.add(aModel._key.toString(), preds);
    } else if(aModel._output.isMultinomialClassifier()){ //Multinomial
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
        if(!aModel._output.isMultinomialClassifier()){
            baseModelPredictions.add(aFrame);
        }else {
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
        if(!aModel._output.isMultinomialClassifier()){
          baseModelPredictions.add(aPred);
        }else {
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

      // train the metalearner model
      // TODO: allow types other than GLM
      // Default Job for just this training
      Key<Model> metalearnerKey = Key.<Model>make("metalearner_" + _model._key);
      Job job = new Job<>(metalearnerKey, ModelBuilder.javaName("glm"), "StackingEnsemble metalearner (GLM)");
      GLM metaBuilder = ModelBuilder.make("GLM", job, metalearnerKey);
      metaBuilder._parms._non_negative = true;
      metaBuilder._parms._train = levelOneTrainingFrame._key;
      metaBuilder._parms._valid = (levelOneValidationFrame == null ? null : levelOneValidationFrame._key);
      metaBuilder._parms._response_column = _model.responseColumn;
      //Enable lambda search if a validation frame is passed in to get a better GLM fit.
      //Since we are also using non_negative to true, we should also set early_stopping = false.
      if(metaBuilder._parms._valid != null){
        metaBuilder._parms._lambda_search = true;
        metaBuilder._parms._early_stopping = false;
      }

      if(_model.modelCategory == ModelCategory.Regression){
          metaBuilder._parms._family = GLMModel.GLMParameters.Family.gaussian;
      }else if(_model.modelCategory == ModelCategory.Binomial){
          metaBuilder._parms._family = GLMModel.GLMParameters.Family.binomial;
      }else if(_model.modelCategory == ModelCategory.Multinomial){
          metaBuilder._parms._family = GLMModel.GLMParameters.Family.multinomial;
      }else{
          throw new H2OIllegalArgumentException("Family " + _model.modelCategory + "  is not supported.");
      }

      metaBuilder.init(false);

      Job<GLMModel> j = metaBuilder.trainModel();

      while (j.isRunning()) {
        try {
          _job.update(j._work, "training metalearner");
          Thread.sleep(100);
        }
        catch (InterruptedException e) {}
      }

      Log.info("Finished training metalearner model.");

      _model._output._metalearner = metaBuilder.get();
      _model.doScoreMetrics(_job);
      // _model._output._model_summary = createModelSummaryTable(model._output);
      if(_parms._keep_levelone_frame) {
        _model._output._levelone_frame_id = levelOneTrainingFrame; //Keep Level One Training Frame in Stacked Ensemble model object
      }else{
        DKV.remove(levelOneTrainingFrame._key); //Remove Level One Training Frame from DKV
      }
      if (null != levelOneValidationFrame) {
        DKV.remove(levelOneValidationFrame._key); //Remove Level One Validation Frame from DKV
      }
      _model.update(_job);
      _model.unlock(_job);
      }
    } // computeImpl
  }
