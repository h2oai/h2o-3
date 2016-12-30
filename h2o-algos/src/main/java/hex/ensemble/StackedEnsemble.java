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
import water.util.Log;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsemble extends ModelBuilder<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {
  StackedEnsembleDriver _driver;
  // The in-progress model being built
  protected StackedEnsembleModel _model;


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
         //   ModelCategory.Multinomial, // TODO
    };
  }

  @Override protected StackedEnsembleDriver trainModelImpl() { return _driver = new StackedEnsembleDriver(); }

  public static void addModelPredictionsToLevelOneFrame(Model aModel, Frame aModelsPredictions, Frame levelOneFrame) {
    if (aModel._output.isBinomialClassifier())
      levelOneFrame.add(aModel._key.toString(), aModelsPredictions.vec("YES"));
    else if (aModel._output.isClassifier())
      throw new H2OIllegalArgumentException("Don't yet know how to stack multinomial classifiers: " + aModel._key);
    else if (aModel._output.isAutoencoder())
      throw new H2OIllegalArgumentException("Don't yet know how to stack autoencoders: " + aModel._key);
    else if (!aModel._output.isSupervised())
      throw new H2OIllegalArgumentException("Don't yet know how to stack unsupervised models: " + aModel._key);
    else
      levelOneFrame.add(aModel._key.toString(), aModelsPredictions.vec("predict"));
  }

  private class StackedEnsembleDriver extends Driver {

    private Frame prepareLevelOneFrame(StackedEnsembleModel.StackedEnsembleParameters parms) {
      // TODO: allow the user to name the level one frame
      Frame levelOneFrame = new Frame(Key.<Frame>make("levelone_" + _model._key.toString()));
      for (Key<Model> k : _parms._base_models) {
        Model aModel = DKV.getGet(k);
        if (null == aModel) {
          Log.warn("Failed to find base model; skipping: " + k);
          continue;
        }

        if (null == aModel._output._cross_validation_holdout_predictions_frame_id)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame id. . .  Looks like keep_cross_validation_predictions wasn't set when building the models.");

        // add the predictions for aModel to levelOneFrame
        // TODO: multinomial classification:
        Frame aModelsPredictions = aModel._output._cross_validation_holdout_predictions_frame_id.get();
        StackedEnsemble.addModelPredictionsToLevelOneFrame(aModel, aModelsPredictions, levelOneFrame);
      } // for all base_models

      levelOneFrame.add(_model.responseColumn, _model.commonTrainingFrame.vec(_model.responseColumn));

      // TODO: what if we're running multiple in parallel and have a name collision?
      levelOneFrame.delete_and_lock(_job);  // delete preexisting and write lock
      levelOneFrame.unlock(_job);
      Log.info("Finished creating \"level one\" frame for stacking: " + levelOneFrame.toString());
      return levelOneFrame;
    }

    public void computeImpl() {
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(StackedEnsemble.this);
      _model = new StackedEnsembleModel(dest(), _parms, new StackedEnsembleModel.StackedEnsembleOutput(StackedEnsemble.this));
      _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)

      _model.checkAndInheritModelProperties();

      Frame levelOneFrame = prepareLevelOneFrame(_parms);

      // train the metalearner model
      // TODO: allow types other than GLM
      // Default Job for just this training
      Key<Model> metalearnerKey = Key.<Model>make("metalearner_" + _model._key);
      Job job = new Job<>(metalearnerKey, ModelBuilder.javaName("glm"), "StackingEnsemble metalearner (GLM)");
      GLM metaBuilder = ModelBuilder.make("GLM", job, metalearnerKey);
      metaBuilder._parms._non_negative = true;
      metaBuilder._parms._train = levelOneFrame._key;
      metaBuilder._parms._response_column = _model.responseColumn;
      // TODO: multinomial
      // TODO: support other families for regression
      metaBuilder._parms._family = _model.modelCategory == ModelCategory.Regression ? GLMModel.GLMParameters.Family.gaussian : GLMModel.GLMParameters.Family.binomial;

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

      _model._output._meta_model = metaBuilder.get();
      _model.doScoreMetrics(_job);
      // _model._output._model_summary = createModelSummaryTable(model._output);
      _model.update(_job);
      _model.unlock(_job);

    } // computeImpl
  }
}
