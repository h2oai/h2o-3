package hex.ensemble;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.grid.Grid;
import jsr166y.CountedCompleter;
import water.DKV;
import water.Job;
import water.Key;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;
import java.util.stream.Stream;

import water.util.ArrayUtils;
import water.util.Log;

import static hex.genmodel.utils.DistributionFamily.*;
import static hex.util.DistributionUtils.familyToDistribution;


/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsemble extends ModelBuilder<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {
  StackedEnsembleDriver _driver;
  // The in-progress model being built
  protected StackedEnsembleModel _model;

  public StackedEnsemble(StackedEnsembleModel.StackedEnsembleParameters parms) {
    super(parms);
    init(false);
  }

  public StackedEnsemble(boolean startup_once) {
    super(new StackedEnsembleModel.StackedEnsembleParameters(), startup_once);
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial
    };
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Stable;
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  @Override
  protected void ignoreBadColumns(int npredictors, boolean expensive){
    HashSet usedColumns = new HashSet();

    for(Key k: _parms._base_models) {
      Model model = (Model) DKV.getGet(k);
      usedColumns.add(model._parms._response_column);
      usedColumns.addAll(Arrays.asList(model._parms.getNonPredictors()));
      if (model._output._origNames != null)
        usedColumns.addAll(Arrays.asList(model._output._origNames));
      else
        usedColumns.addAll(Arrays.asList(model._output._names));
    }

    usedColumns.addAll(Arrays.asList(_parms.getNonPredictors()));

    // FilterCols(n=0) because there is no guarantee that non-predictors are
    // at the end of the frame, e.g., `metalearner_fold` column can be anywhere,
    // and `usedColumns` contain all used columns even the non-predictor ones
    new FilterCols(0) {
      @Override protected boolean filter(Vec v, String name) {
        return !usedColumns.contains(name);
      }
    }.doIt(_train,"Dropping unused columns: ",expensive);
  }

  @Override
  protected StackedEnsembleDriver trainModelImpl() {
    return _driver = _parms._blending == null ? new StackedEnsembleCVStackingDriver() : new StackedEnsembleBlendingDriver();
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

  @Override
  public int nclasses() {
    DistributionFamily distribution;
    if (_parms._metalearner_parameters != null) {
      if (_parms._metalearner_parameters instanceof GLMModel.GLMParameters)
        distribution = familyToDistribution(((GLMModel.GLMParameters) _parms._metalearner_parameters)._family);
      else
        distribution = _parms._metalearner_parameters._distribution; // quasibinomial

      if (multinomial.equals(distribution) || ordinal.equals(distribution) || AUTO.equals(distribution))
        return _nclass;
      if (bernoulli.equals(distribution) || quasibinomial.equals(distribution)
              || fractionalbinomial.equals(distribution))
        return 2;
      return 1;
    }
    return super.nclasses();
  }

  @Override
  public void init(boolean expensive) {
    expandBaseModels();
    super.init(expensive);

    if (_parms._distribution != DistributionFamily.AUTO) {
      throw new H2OIllegalArgumentException("Setting \"distribution\" to StackedEnsemble is unsupported. Please set it in \"metalearner_parameters\".");
    }

    checkColumnPresent("fold", _parms._metalearner_fold_column, train(), valid(), _parms.blending());
    checkColumnPresent("weights", _parms._weights_column, train(), valid(), _parms.blending());
    checkColumnPresent("offset", _parms._offset_column, train(), valid(), _parms.blending());
    validateBaseModels();
  }

  /**
   * Expand base models - if a grid is provided instead of a model it gets expanded in to individual models.
   */
  private void expandBaseModels() {
    // H2O Flow initializes SE with no base_models
    if (_parms._base_models == null) return;

    List<Key> baseModels = new ArrayList<Key>();
    for (Key baseModelKey : _parms._base_models) {
      Object retrievedObject = DKV.getGet(baseModelKey);
      if (retrievedObject instanceof Model) {
        baseModels.add(baseModelKey);
      } else if (retrievedObject instanceof Grid) {
        Grid grid = (Grid) retrievedObject;
        Collections.addAll(baseModels, grid.getModelKeys());
      } else if (retrievedObject == null) {
        throw new IllegalArgumentException(String.format("Specified id \"%s\" does not exist.", baseModelKey));
      } else {
        throw new IllegalArgumentException(String.format("Unsupported type \"%s\" as a base model.", retrievedObject.getClass().toString()));
      }
    }
    _parms._base_models = baseModels.toArray(new Key[0]);
  }

  /**
   * Validates base models.
   */
  private void validateBaseModels() {
    // H2O Flow initializes SE with no base_models
    if (_parms._base_models == null) return;

    boolean warnSameWeightsColumns = true;
    String referenceWeightsColumn = null;
    for (int i = 0; i < _parms._base_models.length; i++) {
      Model baseModel = DKV.getGet(_parms._base_models[i]);

      if (i == 0) {
        if ((_parms._offset_column == null))
          _parms._offset_column = baseModel._parms._offset_column;
        referenceWeightsColumn = baseModel._parms._weights_column;
        warnSameWeightsColumns = referenceWeightsColumn != null; // We don't want to warn if no weights are set
      }

      if (!Objects.equals(referenceWeightsColumn, baseModel._parms._weights_column)) {
        warnSameWeightsColumns = false;
      }

      if (!Objects.equals(_parms._offset_column, baseModel._parms._offset_column))
        throw new IllegalArgumentException("All base models must have the same offset_column!");
    }

    if (_parms._weights_column == null && warnSameWeightsColumns && _parms._base_models.length > 0) {
      warn("_weights_column", "All base models use weights_column=\"" + referenceWeightsColumn +
              "\" but Stacked Ensemble does not. If you want to use the same " +
              "weights_column for the meta learner, please specify it as an argument " +
              "in the h2o.stackedEnsemble call.");
    }
  }


  /**
   * Checks for presence of a column in given {@link Frame}s. Null column means no checks are done.
   *
   * @param columnName     Name of the column, such as fold, weight, etc.
   * @param columnId       Actual column name in the frame. Null means no column has been specified.
   * @param frames         A list of frames to check the presence of fold column in
   */
  private static void checkColumnPresent(final String columnName, final String columnId, final Frame... frames) {
    if (columnId == null) return; // Unspecified column implies no checks are needs on provided frames

    for (Frame frame : frames) {
      if (frame == null) continue; // No frame provided, no checks required
      if (frame.vec(columnId) == null) {
        throw new IllegalArgumentException(String.format("Specified %s column '%s' not found in one of the supplied data frames. Available column names are: %s",
                columnName, columnId, Arrays.toString(frame.names())));
      }
    }
  }

  static void addModelPredictionsToLevelOneFrame(Model aModel, Frame aModelsPredictions, Frame levelOneFrame) {
    if (aModel._output.isBinomialClassifier()) {
      // GLM uses a different column name than the other algos
      Vec preds = aModelsPredictions.vec(2); // Predictions column names have been changed...
      levelOneFrame.add(aModel._key.toString(), preds);
    } else if (aModel._output.isMultinomialClassifier()) { //Multinomial
      //Need to remove 'predict' column from multinomial since it contains outcome
      Frame probabilities = aModelsPredictions.subframe(ArrayUtils.remove(aModelsPredictions.names(), "predict"));
      probabilities.setNames(
              Stream.of(probabilities.names())
                      .map((name) -> aModel._key.toString().concat("/").concat(name))
                      .toArray(String[]::new)
      );
      levelOneFrame.add(probabilities);
    } else if (aModel._output.isAutoencoder()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack autoencoders: " + aModel._key);
    } else if (!aModel._output.isSupervised()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack unsupervised models: " + aModel._key);
    } else {
      Vec preds = aModelsPredictions.vec("predict");
      levelOneFrame.add(aModel._key.toString(), preds);
    }
  }

  /**
   * Add non predictor columns to levelOneFrame, i.e., all but those generated by base models. For example:
   * response_column, metalearner_fold_column, weights_column
   *
   * @param parms           StackedEnsembleParameters
   * @param fr
   * @param levelOneFrame
   * @param training        Used to determine which columns are necessary to add
   */
  static void addNonPredictorsToLevelOneFrame(final StackedEnsembleModel.StackedEnsembleParameters parms, Frame fr, Frame levelOneFrame, boolean training) {
    if (training) {
      if (parms._metalearner_fold_column != null)
        levelOneFrame.add(parms._metalearner_fold_column, fr.vec(parms._metalearner_fold_column));
    }

    if (parms._weights_column != null)
      levelOneFrame.add(parms._weights_column, fr.vec(parms._weights_column));

    if (parms._offset_column != null)
      levelOneFrame.add(parms._offset_column, fr.vec(parms._offset_column));

    levelOneFrame.add(parms._response_column, fr.vec(parms._response_column));
  }

  private abstract class StackedEnsembleDriver extends Driver {

    /**
     * Prepare a "level one" frame for a given set of models, predictions-frames and actuals.  Used for preparing
     * training and validation frames for the metalearning step, and could also be used for bulk predictions for
     * a StackedEnsemble.
     */
    private Frame prepareLevelOneFrame(String levelOneKey, Model[] baseModels, Frame[] baseModelPredictions, Frame actuals) {
      if (null == baseModels) throw new H2OIllegalArgumentException("Base models array is null.");
      if (null == baseModelPredictions) throw new H2OIllegalArgumentException("Base model predictions array is null.");
      if (baseModels.length == 0) throw new H2OIllegalArgumentException("Base models array is empty.");
      if (baseModelPredictions.length == 0)
        throw new H2OIllegalArgumentException("Base model predictions array is empty.");
      if (baseModels.length != baseModelPredictions.length)
        throw new H2OIllegalArgumentException("Base models and prediction arrays are different lengths.");
      final StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform transform;
      if (_parms._metalearner_transform != null && _parms._metalearner_transform != StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform.NONE) {
        if (!(_model._output.isBinomialClassifier() || _model._output.isMultinomialClassifier()))
          throw new H2OIllegalArgumentException("Metalearner transform is supported only for classification!");
        transform = _parms._metalearner_transform;
      } else {
        transform = null;
      }

      if (null == levelOneKey) levelOneKey = "levelone_" + _model._key.toString() + "_" + _parms._metalearner_transform.toString();

      // TODO: what if we're running multiple in parallel and have a name collision?
      Frame old = DKV.getGet(levelOneKey);
      if (old != null && old instanceof Frame) {
        Frame oldFrame = (Frame) old;
        oldFrame.write_lock(_job);
        // Remove ALL the columns, so we don't delete them in remove_impl.  Their
        // lifetime is controlled by their model.
        oldFrame.removeAll();
        oldFrame.update(_job);
        oldFrame.unlock(_job);
      }

      Frame levelOneFrame = transform == null ?
              new Frame(Key.make(levelOneKey))  // no tranform -> this will be the final frame 
              :
              new Frame();                      // tranform -> this is only an intermediate result

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
        Scope.untrack(baseModelPredictions);
      }

      if (transform != null) {
        levelOneFrame = _parms._metalearner_transform.transform(_model, levelOneFrame, Key.make(levelOneKey));
      }

      // Add metalearner fold column, weights column to level one frame if it exists
      addNonPredictorsToLevelOneFrame(_model._parms, actuals, levelOneFrame, true);

      Log.info("Finished creating \"level one\" frame for stacking: " + levelOneFrame.toString());
      DKV.put(levelOneFrame);
      return levelOneFrame;
    }

    /**
     * Prepare a "level one" frame for a given set of models and actuals.
     * Used for preparing validation frames for the metalearning step, and could also be used for bulk predictions for a StackedEnsemble.
     */
    private Frame prepareLevelOneFrame(String levelOneKey, Key<Model>[] baseModelKeys, Frame actuals, boolean isTraining) {
      List<Model> baseModels = new ArrayList<>();
      List<Frame> baseModelPredictions = new ArrayList<>();

      for (Key<Model> k : baseModelKeys) {
        if (_model._output._metalearner == null || _model.isUsefulBaseModel(k)) {
          Model aModel = DKV.getGet(k);
          if (null == aModel)
            throw new H2OIllegalArgumentException("Failed to find base model: " + k);

          Frame predictions = getPredictionsForBaseModel(aModel, actuals, isTraining);
          baseModels.add(aModel);
          baseModelPredictions.add(predictions);
        }
      }
      boolean keepLevelOneFrame = isTraining && _parms._keep_levelone_frame;
      Frame levelOneFrame = prepareLevelOneFrame(levelOneKey, baseModels.toArray(new Model[0]), baseModelPredictions.toArray(new Frame[0]), actuals);
      if (keepLevelOneFrame) {
        levelOneFrame = levelOneFrame.deepCopy(levelOneFrame._key.toString());
        levelOneFrame.write_lock(_job);
        levelOneFrame.update(_job);
        levelOneFrame.unlock(_job);
        Scope.untrack(levelOneFrame.keysList());
      }
      return levelOneFrame;
    }

    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      if (_model != null) _model.delete();
      return super.onExceptionalCompletion(ex, caller);
    }

    protected Frame buildPredictionsForBaseModel(Model model, Frame frame) {
      Key<Frame> predsKey = buildPredsKey(model, frame);
      Frame preds = DKV.getGet(predsKey);
      if (preds == null) {
        preds =  model.score(frame, predsKey.toString(), null, false);  // no need for metrics here (leaks in client mode)
        Scope.untrack(preds.keysList());
      }
      if (_model._output._base_model_predictions_keys == null)
        _model._output._base_model_predictions_keys = new Key[0];

      if (!ArrayUtils.contains(_model._output._base_model_predictions_keys, predsKey)){
        _model._output._base_model_predictions_keys = ArrayUtils.append(_model._output._base_model_predictions_keys, predsKey);
      }
      //predictions are cleaned up by metalearner if necessary
      return preds;
    }

    protected abstract StackedEnsembleModel.StackingStrategy strategy();

    /**
     * @RETURN THE FRAME THAT IS USED TO COMPUTE THE PREDICTIONS FOR THE LEVEL-ONE TRAINING FRAME.
     */
    protected abstract Frame getActualTrainingFrame();

    protected abstract Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTrainingFrame);

    private Key<Frame> buildPredsKey(Key model_key, long model_checksum, Key frame_key, long frame_checksum) {
      return Key.make("preds_" + model_checksum + "_on_" + frame_checksum);
    }

    protected Key<Frame> buildPredsKey(Model model, Frame frame) {
      return frame == null || model == null ? null : buildPredsKey(model._key, model.checksum(), frame._key, frame.checksum());
    }

    public void computeImpl() {
      init(true);
      if (error_count() > 0) throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(StackedEnsemble.this);

      _model = new StackedEnsembleModel(dest(), _parms, new StackedEnsembleModel.StackedEnsembleOutput(StackedEnsemble.this));
      _model._output._stacking_strategy = strategy();
      try {
        _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)
        _model.checkAndInheritModelProperties();
        _model.update(_job);
      } finally {
        _model.unlock(_job);
      }

      String levelOneTrainKey = "levelone_training_" + _model._key.toString();
      Frame levelOneTrainingFrame = prepareLevelOneFrame(levelOneTrainKey, _model._parms._base_models, getActualTrainingFrame(), true);
      Frame levelOneValidationFrame = null;
      if (_model._parms.valid() != null) {
        String levelOneValidKey = "levelone_validation_" + _model._key.toString();
        levelOneValidationFrame = prepareLevelOneFrame(levelOneValidKey, _model._parms._base_models, _model._parms.valid(), false);
      }

      Metalearner.Algorithm metalearnerAlgoSpec = _model._parms._metalearner_algorithm;
      Metalearner.Algorithm metalearnerAlgoImpl = Metalearners.getActualMetalearnerAlgo(metalearnerAlgoSpec);

      // Compute metalearner
      if(metalearnerAlgoImpl != null) {
        Key<Model> metalearnerKey = Key.<Model>make("metalearner_" + metalearnerAlgoSpec + "_" + _model._key);

        Job metalearnerJob = new Job<>(metalearnerKey, ModelBuilder.javaName(metalearnerAlgoImpl.toString()),
                "StackingEnsemble metalearner (" + metalearnerAlgoSpec + ")");
        //Check if metalearner_params are passed in
        boolean hasMetaLearnerParams = _model._parms._metalearner_parameters != null;
        long metalearnerSeed = _model._parms._seed;

        Metalearner metalearner = Metalearners.createInstance(metalearnerAlgoSpec.name());
        metalearner.init(
                levelOneTrainingFrame,
                levelOneValidationFrame,
                _model._parms._metalearner_parameters,
                _model,
                _job,
                metalearnerKey,
                metalearnerJob,
                _parms,
                hasMetaLearnerParams,
                metalearnerSeed,
                _parms._max_runtime_secs == 0 ? 0 : Math.max(remainingTimeSecs(), 1)
        );
        metalearner.compute();
      } else {
        throw new H2OIllegalArgumentException("Invalid `metalearner_algorithm`. Passed in " + metalearnerAlgoSpec +
                " but must be one of " + Arrays.toString(Metalearner.Algorithm.values()));
      }
      if (_model.evalAutoParamsEnabled && _model._parms._metalearner_algorithm == Metalearner.Algorithm.AUTO)
        _model._parms._metalearner_algorithm = metalearnerAlgoImpl;
    } // computeImpl
  }

  private class StackedEnsembleCVStackingDriver extends StackedEnsembleDriver {

    @Override
    protected StackedEnsembleModel.StackingStrategy strategy() {
      return StackedEnsembleModel.StackingStrategy.cross_validation;
    }

    @Override
    protected Frame getActualTrainingFrame() {
      return _model._parms.train();
    }

    @Override
    protected Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTraining) {
      Frame fr;
      if (isTraining) {
        // for training, retrieve predictions from cv holdout predictions frame as all base models are required to get built with keep_cross_validation_frame=true
        if (null == model._output._cross_validation_holdout_predictions_frame_id)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame id. . .  Looks like keep_cross_validation_predictions wasn't set when building the models.");

        fr = DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id);

        if (null == fr)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame. . .  Looks like keep_cross_validation_predictions wasn't set when building the models, or the frame was deleted.");

      } else {
        fr = buildPredictionsForBaseModel(model, actualsFrame);
      }
      return fr;
    }

  }

  private class StackedEnsembleBlendingDriver extends StackedEnsembleDriver {

    @Override
    protected StackedEnsembleModel.StackingStrategy strategy() {
      return StackedEnsembleModel.StackingStrategy.blending;
    }

    @Override
    protected Frame getActualTrainingFrame() {
      return _model._parms.blending();
    }

    @Override
    protected Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTrainingFrame) {
      // if training we can stop prematurely due to a timeout but computing validation scores should be allowed to finish
      if (stop_requested() && isTrainingFrame) {
        throw new Job.JobCancelledException();
      }
      return buildPredictionsForBaseModel(model, actualsFrame);
    }
  }

}
