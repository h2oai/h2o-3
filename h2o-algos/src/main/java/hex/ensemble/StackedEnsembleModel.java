package hex.ensemble;

import hex.Distribution;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.Log;
import water.util.MRUtils;
import water.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import static hex.Model.Parameters.FoldAssignmentScheme.AUTO;
import static hex.Model.Parameters.FoldAssignmentScheme.Random;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsembleModel extends Model<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {

  // common parameters for the base models (keeping them public for backwards compatibility, although it's nonsense)
  public ModelCategory modelCategory;
  public long trainingFrameRows = -1;

  public String responseColumn = null;

  public enum StackingStrategy {
    cross_validation,
    blending
  }

  // TODO: add a separate holdout dataset for the ensemble
  // TODO: add a separate overall cross-validation for the ensemble, including _fold_column and FoldAssignmentScheme / _fold_assignment

  public StackedEnsembleModel(Key selfKey, StackedEnsembleParameters parms, StackedEnsembleOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    if (_parms._metalearner_fold_assignment == AUTO) {
      _parms._metalearner_fold_assignment = Random;
    }
  }
  
  public static class StackedEnsembleParameters extends Model.Parameters {
    public String algoName() { return "StackedEnsemble"; }
    public String fullName() { return "Stacked Ensemble"; }
    public String javaName() { return StackedEnsembleModel.class.getName(); }
    @Override public long progressUnits() { return 1; }  // TODO

    // base_models is a list of base model keys to ensemble (must have been cross-validated)
    public Key<Model> _base_models[] = new Key[0];
    // Should we keep the level-one frame of cv preds + response col?
    public boolean _keep_levelone_frame = false;
    // internal flag if we want to avoid having the base model predictions rescored multiple times, esp. for blending.
    public boolean _keep_base_model_predictions = false;

    // Metalearner params
    //for stacking using cross-validation
    public int _metalearner_nfolds;
    public Parameters.FoldAssignmentScheme _metalearner_fold_assignment;
    public String _metalearner_fold_column;
    //the training frame used for blending (from which predictions columns are computed)
    public Key<Frame> _blending;

    public Metalearner.Algorithm _metalearner_algorithm = Metalearner.Algorithm.AUTO;
    public String _metalearner_params = new String(); //used for clients code-gen only.
    public Model.Parameters _metalearner_parameters;
    public long _score_training_samples = 10_000;

    /**
     * initialize {@link #_metalearner_parameters} with default parameters for the current {@link #_metalearner_algorithm}.
     */
    public void initMetalearnerParams() {
      initMetalearnerParams(_metalearner_algorithm);
    }

    /**
     * initialize {@link #_metalearner_parameters} with default parameters for the given algorithm
     * @param algo the metalearner algorithm we want to use and for which parameters are initialized.
     */
    public void initMetalearnerParams(Metalearner.Algorithm algo) {
      _metalearner_algorithm = algo;
      _metalearner_parameters = Metalearners.createParameters(algo.name());
      if (Metalearners.getActualMetalearnerAlgo(algo) == Metalearner.Algorithm.glm){
        // FIXME: This is here because there is no Family.AUTO. It enables us to know if the user specified family or not.
        // FIXME: Family.AUTO will be implemented in https://0xdata.atlassian.net/projects/PUBDEV/issues/PUBDEV-7444
        ((GLMModel.GLMParameters) _metalearner_parameters)._family = null;
      }
    }
    
    public final Frame blending() { return _blending == null ? null : _blending.get(); }

  }

  public static class StackedEnsembleOutput extends Model.Output {
    public StackedEnsembleOutput() { super(); }
    public StackedEnsembleOutput(StackedEnsemble b) { super(b); }

    public StackedEnsembleOutput(Job job) { _job = job; }
    // The metalearner model (e.g., a GLM that has a coefficient for each of the base_learners).
    public Model _metalearner;
    public Frame _levelone_frame_id; //set only if StackedEnsembleParameters#_keep_levelone_frame=true
    public StackingStrategy _stacking_strategy;
    
    //Set of base model predictions that have been cached in DKV to avoid scoring the same model multiple times,
    // it is then the responsibility of the client code to delete those frames from DKV.
    //This especially useful when building SE models incrementally (e.g. in AutoML).
    //The Set is instantiated and filled only if StackedEnsembleParameters#_keep_base_model_predictions=true.
    public Key<Frame>[] _base_model_predictions_keys; 
  }

  /**
   * For StackedEnsemble we call score on all the base_models and then combine the results
   * with the metalearner to create the final predictions frame.
   *
   * @see Model#predictScoreImpl(Frame, Frame, String, Job, boolean, CFuncRef)
   * @param adaptFrm Already adapted frame
   * @param computeMetrics
   * @return A Frame containing the prediction column, and class distribution
   */
  @Override
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    final String seKey = this._key.toString();
    Frame levelOneFrame = new Frame(Key.<Frame>make("preds_levelone_" + seKey + fr._key));

    Model[] usefulBaseModels = Stream.of(_parms._base_models)
            .filter(this::isUsefulBaseModel)
            .map(Key::get)
            .toArray(Model[]::new);

    if (usefulBaseModels.length > 0) {
      Frame[] baseModelPredictions = new Frame[usefulBaseModels.length];

      // Run scoring of base models in parallel
      H2O.submitTask(new LocalMR(new MrFun() {
        @Override
        protected void map(int id) {
          baseModelPredictions[id] = usefulBaseModels[id].score(
                  fr,
                  "preds_base_" + seKey + usefulBaseModels[id]._key + fr._key,
                  j,
                  false
          );
        }
      }, usefulBaseModels.length)).join();

      for (int i = 0; i < usefulBaseModels.length; i++) {
        StackedEnsemble.addModelPredictionsToLevelOneFrame(usefulBaseModels[i], baseModelPredictions[i], levelOneFrame);
        DKV.remove(baseModelPredictions[i]._key); //Cleanup
        Frame.deleteTempFrameAndItsNonSharedVecs(baseModelPredictions[i], levelOneFrame);
      }
    }

    // Add response column, weights columns to level one frame
    StackedEnsemble.addMiscColumnsToLevelOneFrame(_parms, adaptFrm, levelOneFrame, false);
    // TODO: what if we're running multiple in parallel and have a name collision?
    Log.info("Finished creating \"level one\" frame for scoring: " + levelOneFrame.toString());

    // Score the dataset, building the class distribution & predictions

    Model metalearner = this._output._metalearner;
    Frame predictFr = metalearner.score(
        levelOneFrame, 
        destination_key, 
        j, 
        computeMetrics, 
        CFuncRef.from(_parms._custom_metric_func)
    );
    if (computeMetrics) {
      // #score has just stored a ModelMetrics object for the (metalearner, preds_levelone) Model/Frame pair.
      // We need to be able to look it up by the (this, fr) pair.
      // The ModelMetrics object for the metalearner will be removed when the metalearner is removed.
      Key<ModelMetrics>[] mms = metalearner._output.getModelMetrics();
      ModelMetrics lastComputedMetric = mms[mms.length - 1].get();
      ModelMetrics mmStackedEnsemble = lastComputedMetric.deepCloneWithDifferentModelAndFrame(this, fr);
      this.addModelMetrics(mmStackedEnsemble);
      //now that we have the metric set on the SE model, removing the one we just computed on metalearner (otherwise it leaks in client mode)
      for (Key<ModelMetrics> mm : metalearner._output.clearModelMetrics(true)) {
        DKV.remove(mm);
      }
    }
    Frame.deleteTempFrameAndItsNonSharedVecs(levelOneFrame, adaptFrm);
    return predictFr;
  }

  /**
   * Is the baseModel's prediction used in the metalearner?
   *
   * @param baseModelKey
   */
  boolean isUsefulBaseModel(Key<Model> baseModelKey) {
    Model metalearner = _output._metalearner;
    assert metalearner != null : "can't use isUsefulBaseModel during training";
    if (modelCategory == ModelCategory.Multinomial) {
      // Multinomial models output multiple columns and a base model
      // might be useful just for one category...
      for (String feature : metalearner._output._names) {
        if (feature.startsWith(baseModelKey.toString().concat("/"))){
          if (metalearner.isFeatureUsedInPredict(feature)) {
            return true;
          }
        }
      }
      return false;
    } else {
      return metalearner.isFeatureUsedInPredict(baseModelKey.toString());
    }
  }

  /**
   * Should never be called: the code paths that normally go here should call predictScoreImpl().
   * @see Model#score0(double[], double[])
   */
  @Override
  protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw new UnsupportedOperationException("StackedEnsembleModel.score0() should never be called: the code paths that normally go here should call predictScoreImpl().");
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw new UnsupportedOperationException("StackedEnsembleModel.makeMetricBuilder should never be called!");
  }

  private ModelMetrics doScoreTrainingMetrics(Frame frame, Job job) {
    Frame scoredFrame = (_parms._score_training_samples > 0 && _parms._score_training_samples < frame.numRows())
            ? MRUtils.sampleFrame(frame, _parms._score_training_samples, _parms._seed)
            : frame;
    try {
      Frame pred = this.predictScoreImpl(scoredFrame, new Frame(scoredFrame), null, job, true, CFuncRef.from(_parms._custom_metric_func));
      pred.delete();
      return ModelMetrics.getFromDKV(this, scoredFrame);
    } finally {
      if (scoredFrame != frame) scoredFrame.delete();
    }
  }

  void doScoreOrCopyMetrics(Job job) {
    // To get ensemble training metrics, the training frame needs to be re-scored since
    // training metrics from metalearner are not equal to ensemble training metrics.
    // The training metrics for the metalearner do not reflect true ensemble training metrics because
    // the metalearner was trained on cv preds, not training preds.  So, rather than clone the metalearner
    // training metrics, we have to re-score the training frame on all the base models, then send these
    // biased preds through to the metalearner, and then compute the metrics there.
    this._output._training_metrics = doScoreTrainingMetrics(this._parms.train(), job);
    
    // Validation metrics can be copied from metalearner (may be null).
    // Validation frame was already piped through so there's no need to re-do that to get the same results.
    this._output._validation_metrics = this._output._metalearner._output._validation_metrics;
    
    // Cross-validation metrics can be copied from metalearner (may be null).
    // For cross-validation metrics, we use metalearner cross-validation metrics as a proxy for the ensemble
    // cross-validation metrics -- the true k-fold cv metrics for the ensemble would require training k sets of
    // cross-validated base models (rather than a single set of cross-validated base models), which is extremely
    // computationally expensive and awkward from the standpoint of the existing Stacked Ensemble API.
    // More info: https://0xdata.atlassian.net/browse/PUBDEV-3971
    // Need to do DeepClone because otherwise framekey is incorrect (metalearner train is levelone not train)
    if (null != this._output._metalearner._output._cross_validation_metrics) {
      this._output._cross_validation_metrics = this._output._metalearner._output._cross_validation_metrics
              .deepCloneWithDifferentModelAndFrame(this, this._output._metalearner._parms.train());
    }
  }

  private DistributionFamily familyToDistribution(GLMModel.GLMParameters.Family aFamily) {
    if (aFamily == GLMModel.GLMParameters.Family.binomial) {
      return DistributionFamily.bernoulli;
    }
    try {
      return Enum.valueOf(DistributionFamily.class, aFamily.toString());
    }
    catch (IllegalArgumentException e) {
      throw new H2OIllegalArgumentException("Don't know how to find the right DistributionFamily for Family: " + aFamily);
    }
  }

  private DistributionFamily distributionFamily(Model aModel) {
    // TODO: hack alert: In DRF, _parms._distribution is always set to multinomial.  Yay.
    if (aModel instanceof DRFModel)
      if (aModel._output.isBinomialClassifier())
        return DistributionFamily.bernoulli;
      else if (aModel._output.isClassifier())
        return DistributionFamily.multinomial;
      else
        return DistributionFamily.gaussian;

    if (aModel instanceof StackedEnsembleModel) {
      StackedEnsembleModel seModel = (StackedEnsembleModel) aModel;
      if (Metalearners.getActualMetalearnerAlgo(seModel._parms._metalearner_algorithm) == Metalearner.Algorithm.glm) {
        return familyToDistribution(((GLMModel.GLMParameters) seModel._parms._metalearner_parameters)._family);
      }
      if (seModel._parms._metalearner_parameters._distribution != DistributionFamily.AUTO) {
        return seModel._parms._metalearner_parameters._distribution;
      }
    }

    try {
      Field familyField = ReflectionUtils.findNamedField(aModel._parms, "_family");
      Field distributionField = (familyField != null ? null : ReflectionUtils.findNamedField(aModel, "_dist"));
      if (null != familyField) {
        // GLM only, for now
        GLMModel.GLMParameters.Family thisFamily = (GLMModel.GLMParameters.Family) familyField.get(aModel._parms);
        return familyToDistribution(thisFamily);
      }

      if (null != distributionField) {
        Distribution distribution = ((Distribution)distributionField.get(aModel));
        DistributionFamily distributionFamily;
        if (null != distribution)
          distributionFamily = distribution._family;
        else
          distributionFamily = aModel._parms._distribution;

        // NOTE: If the algo does smart guessing of the distribution family we need to duplicate the logic here.
        if (distributionFamily == DistributionFamily.AUTO) {
          if (aModel._output.isBinomialClassifier())
            distributionFamily = DistributionFamily.bernoulli;
          else if (aModel._output.isClassifier())
            distributionFamily = DistributionFamily.multinomial;
          else
            distributionFamily = DistributionFamily.gaussian;
        } // DistributionFamily.AUTO

        return distributionFamily;
      }

      throw new H2OIllegalArgumentException("Don't know how to stack models that have neither a distribution hyperparameter nor a family hyperparameter.");
    }
    catch (Exception e) {
      throw new H2OIllegalArgumentException(e.toString(), e.toString());
    }
  }



  /**
   * Inherit distribution and its parameters
   * @param baseModelParms
   */
  private void inheritDistributionAndParms(Model.Parameters baseModelParms) {
    if (baseModelParms instanceof GLMModel.GLMParameters) {
      try {
        _parms._metalearner_parameters._distribution = familyToDistribution(((GLMModel.GLMParameters) baseModelParms)._family);
      } catch (IllegalArgumentException e) {
        Log.warn("Stacked Ensemble is not able to inherit distribution from GLM's family " + ((GLMModel.GLMParameters) baseModelParms)._family + ".");
      }
    } else if (baseModelParms instanceof DRFModel.DRFParameters) {
      inferBasicDistribution();
    } else {
      _parms._metalearner_parameters._distribution = baseModelParms._distribution;
    }
    // deal with parameterized distributions
    switch (baseModelParms._distribution) {
      case custom:
          _parms._metalearner_parameters._custom_distribution_func = baseModelParms._custom_distribution_func;
        break;
      case huber:
          _parms._metalearner_parameters._huber_alpha = baseModelParms._huber_alpha;
        break;
      case tweedie:
        _parms._metalearner_parameters._tweedie_power = baseModelParms._tweedie_power;
        break;
      case quantile:
        _parms._metalearner_parameters._quantile_alpha = baseModelParms._quantile_alpha;
        break;
    }
  }

  /**
   * Inherit family and its parameters
   * @param baseModelParms
   */
  private void inheritFamilyAndParms(Model.Parameters baseModelParms) {
    GLMModel.GLMParameters metaParams = (GLMModel.GLMParameters) _parms._metalearner_parameters;
    if (baseModelParms instanceof GLMModel.GLMParameters) {
      GLMModel.GLMParameters glmParams = (GLMModel.GLMParameters) baseModelParms;
      metaParams._family = glmParams._family;
      metaParams._link = glmParams._link;
    } else if (baseModelParms instanceof DRFModel.DRFParameters) {
      inferBasicDistribution();
    } else {
      try {
        metaParams._family = Enum.valueOf(GLMModel.GLMParameters.Family.class, baseModelParms._distribution.name());
      } catch (IllegalArgumentException e) {
        Log.warn("Stacked Ensemble is not able to inherit family from a distribution " + baseModelParms._distribution + ".");
        inferBasicDistribution();
      }
    }
    // deal with parameterized distributions
    if (metaParams._family == GLMModel.GLMParameters.Family.tweedie) {
      _parms._metalearner_parameters._tweedie_power = baseModelParms._tweedie_power;
    }
  }

  /**
   * Infers distribution/family from a model
   * @param aModel
   * @return True if the distribution or family was inferred from a model
   */
  boolean inferDistributionOrFamily(Model aModel) {
    if (Metalearners.getActualMetalearnerAlgo(_parms._metalearner_algorithm) == Metalearner.Algorithm.glm) { //use family
      // FIXME: This is here because there is no Family.AUTO. It enables us to know if the user specified family or not.
      // FIXME: Family.AUTO will be implemented in https://0xdata.atlassian.net/projects/PUBDEV/issues/PUBDEV-7444
      if (((GLMModel.GLMParameters)_parms._metalearner_parameters)._family != null) {
        return false; // User specified family - no need to infer one; Link will be also used properly if it is specified
      }
      inheritFamilyAndParms(aModel._parms);
    } else { // use distribution
      if (_parms._metalearner_parameters._distribution != DistributionFamily.AUTO) {
        return false; // User specified distribution; no need to infer one
      }
      inheritDistributionAndParms(aModel._parms);
    }
    return true;
  }

  void inferBasicDistribution() {
    if (Metalearners.getActualMetalearnerAlgo(_parms._metalearner_algorithm).equals(Metalearner.Algorithm.glm)) {
      GLMModel.GLMParameters parms = (GLMModel.GLMParameters)_parms._metalearner_parameters;
      parms._link = GLMModel.GLMParameters.Link.family_default;
      if (this._output.isBinomialClassifier()) {
        parms._family = GLMModel.GLMParameters.Family.binomial;
      } else if (this._output.isClassifier()) {
        parms._family = GLMModel.GLMParameters.Family.multinomial;
      } else {
        parms._family = GLMModel.GLMParameters.Family.gaussian;
      }
    } else {
      if (this._output.isBinomialClassifier()) {
        _parms._metalearner_parameters._distribution = DistributionFamily.bernoulli;
      } else if (this._output.isClassifier()) {
        _parms._metalearner_parameters._distribution = DistributionFamily.multinomial;
      } else {
        _parms._metalearner_parameters._distribution = DistributionFamily.gaussian;
      }
    }
  }

  void checkAndInheritModelProperties() {
    if (null == _parms._base_models || 0 == _parms._base_models.length)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; found 0.");

    if (null != _parms._metalearner_fold_column && 0 != _parms._metalearner_nfolds)
      throw new H2OIllegalArgumentException("Cannot specify fold_column and nfolds at the same time.");

    Model aModel = null;
    boolean retrievedFirstModelParams = false;
    boolean inferredDistributionFromFirstModel = false;
    GLMModel firstGLM = null;
    boolean blending_mode = _parms._blending != null;
    boolean cv_required_on_base_model = !blending_mode;
    boolean require_consistent_training_frames = !blending_mode && !_parms._is_cv_model;
    
    //following variables are collected from the 1st base model (should be identical across base models), i.e. when beenHere=false
    int basemodel_nfolds = -1;
    Parameters.FoldAssignmentScheme basemodel_fold_assignment = null;
    String basemodel_fold_column = null;
    long seed = -1;
    //end 1st model collected fields

    // Make sure we can set metalearner's family and link if needed
    if (_parms._metalearner_parameters == null) {
      _parms.initMetalearnerParams();
    }

    for (Key<Model> k : _parms._base_models) {
      aModel = DKV.getGet(k);
      if (null == aModel) {
        Log.warn("Failed to find base model; skipping: "+k);
        continue;
      }
      Log.debug("Checking properties for model "+k);
      if (!aModel.isSupervised()) {
        throw new H2OIllegalArgumentException("Base model is not supervised: "+aModel._key.toString());
      }

      if (retrievedFirstModelParams) {
        // check that the base models are all consistent with first based model

        if (modelCategory != aModel._output.getModelCategory())
          throw new H2OIllegalArgumentException("Base models are inconsistent: "
                  +"there is a mix of different categories of models among "+Arrays.toString(_parms._base_models));

        if (! responseColumn.equals(aModel._parms._response_column))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different response columns."
                  +" Found: "+responseColumn+" (StackedEnsemble) and "+aModel._parms._response_column+" (model "+k+").");

        if (require_consistent_training_frames) {
          if (trainingFrameRows < 0) trainingFrameRows = _parms.train().numRows();
          long numOfRowsUsedToTrain = aModel._parms.train() == null ?
                  aModel._output._cross_validation_holdout_predictions_frame_id.get().numRows() :
                  aModel._parms.train().numRows();
          if (trainingFrameRows != numOfRowsUsedToTrain)
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use different size (number of rows) training frames."
                    +" Found: "+trainingFrameRows+" (StackedEnsemble) and "+numOfRowsUsedToTrain+" (model "+k+").");
        }

        if (cv_required_on_base_model) {

          if (aModel._parms._fold_assignment != basemodel_fold_assignment
                  && !(aModel._parms._fold_assignment == AUTO && basemodel_fold_assignment == Random)
          ) {
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use different fold_assignments.");
          }

          if (aModel._parms._fold_column == null) {
            // If we don't have a fold_column require:
            // nfolds > 1
            // nfolds consistent across base models
            if (aModel._parms._nfolds < 2)
              throw new H2OIllegalArgumentException("Base model does not use cross-validation: "+aModel._parms._nfolds);
            if (basemodel_nfolds != aModel._parms._nfolds)
              throw new H2OIllegalArgumentException("Base models are inconsistent: they use different values for nfolds.");

            if (basemodel_fold_assignment == Random && aModel._parms._seed != seed)
              throw new H2OIllegalArgumentException("Base models are inconsistent: they use random-seeded k-fold cross-validation but have different seeds.");

          } else {
            if (!aModel._parms._fold_column.equals(basemodel_fold_column))
              throw new H2OIllegalArgumentException("Base models are inconsistent: they use different fold_columns.");
          }
          if (! aModel._parms._keep_cross_validation_predictions)
            throw new H2OIllegalArgumentException("Base model does not keep cross-validation predictions: "+aModel._parms._nfolds);
        }

        if (inferredDistributionFromFirstModel) {
          // Check inferred params and if they differ fallback to basic distribution of model category
          if (!(aModel instanceof DRFModel) && distributionFamily(aModel) == distributionFamily(this)) {
            boolean sameParams = true;
            switch (_parms._metalearner_parameters._distribution) {
              case custom:
                sameParams = _parms._metalearner_parameters._custom_distribution_func
                        .equals(aModel._parms._custom_distribution_func);
                break;
              case huber:
                sameParams = _parms._metalearner_parameters._huber_alpha == aModel._parms._huber_alpha;
                break;
              case tweedie:
                sameParams = _parms._metalearner_parameters._tweedie_power == aModel._parms._tweedie_power;
                break;
              case quantile:
                sameParams = _parms._metalearner_parameters._quantile_alpha == aModel._parms._quantile_alpha;
                break;
            }

            if ((aModel instanceof GLMModel) && (Metalearners.getActualMetalearnerAlgo(_parms._metalearner_algorithm) == Metalearner.Algorithm.glm)) {
              if (firstGLM == null) {
                firstGLM = (GLMModel) aModel;
                inheritFamilyAndParms(firstGLM._parms);
              } else {
                sameParams = ((GLMModel.GLMParameters) _parms._metalearner_parameters)._link.equals(((GLMModel) aModel)._parms._link);
              }
            }

            if (!sameParams) {
              Log.warn("Base models are inconsistent; they use same distribution but different parameters of " +
                      "the distribution. Reverting to default distribution.");
              inferBasicDistribution();
              inferredDistributionFromFirstModel = false;
            }
          } else {
            if (distributionFamily(aModel) != distributionFamily(this)) {
              // Distribution of base models differ
              Log.warn("Base models are inconsistent; they use different distributions: "
                      + distributionFamily(this) + " and: " + distributionFamily(aModel) +
                      ". Reverting to default distribution.");
            } // else the first model was DRF/XRT so we don't want to warn
            inferBasicDistribution();
            inferredDistributionFromFirstModel = false;
          }
        }
      } else {
        // !retrievedFirstModelParams: this is the first base_model
        this.modelCategory = aModel._output.getModelCategory();
        inferredDistributionFromFirstModel = inferDistributionOrFamily(aModel);
        firstGLM = aModel instanceof GLMModel && inferredDistributionFromFirstModel ? (GLMModel) aModel : null;
        responseColumn = aModel._parms._response_column;

        if (! _parms._response_column.equals(responseColumn))  // _params._response_column can't be null, validated by ModelBuilder
          throw new H2OIllegalArgumentException("StackedModel response_column must match the response_column of each base model."
                  +" Found: "+_parms._response_column+"(StackedEnsemble) and: "+responseColumn+" (model "+k+").");

        basemodel_nfolds = aModel._parms._nfolds;
        basemodel_fold_assignment = aModel._parms._fold_assignment;
        if (basemodel_fold_assignment == AUTO) basemodel_fold_assignment = Random;
        basemodel_fold_column = aModel._parms._fold_column;
        seed = aModel._parms._seed;
        retrievedFirstModelParams = true;
      }

    } // for all base_models

    if (null == aModel)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; "
              +_parms._base_models.length+" were specified but none of those were found: "+Arrays.toString(_parms._base_models));
  }
  
  void deleteBaseModelPredictions() {
    if (_output._base_model_predictions_keys != null) {
      for (Key<Frame> key : _output._base_model_predictions_keys) {
        if (_output._levelone_frame_id != null && key.get() != null)
          Frame.deleteTempFrameAndItsNonSharedVecs(key.get(), _output._levelone_frame_id);
        else
          Keyed.remove(key);
      }
      _output._base_model_predictions_keys = null;
    }
  }

  @Override protected Futures remove_impl(Futures fs, boolean cascade) {
    deleteBaseModelPredictions(); 
    if (_output._metalearner != null)
      _output._metalearner.remove(fs);
    if (_output._levelone_frame_id != null)
      _output._levelone_frame_id.remove(fs);

    return super.remove_impl(fs, cascade);
  }

  /** Write out models (base + metalearner) */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    //Metalearner
    ab.putKey(_output._metalearner._key);
    //Base Models
    for (Key<Model> ks : _parms._base_models)
        ab.putKey(ks);
    return super.writeAll_impl(ab);
  }

  /** Read in models (base + metalearner) */
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    //Metalearner
    ab.getKey(_output._metalearner._key,fs);
    //Base Models
    for (Key<Model> ks : _parms._base_models)
      ab.getKey(ks,fs);
    return super.readAll_impl(ab,fs);
  }

  @Override
  public StackedEnsembleMojoWriter getMojo() {
    return new StackedEnsembleMojoWriter(this);
  }

  @Override
  public void deleteCrossValidationModels() {
    _output._metalearner.deleteCrossValidationModels();
  }

  @Override
  public void deleteCrossValidationPreds() {
    _output._metalearner.deleteCrossValidationPreds();
  }

  @Override
  public void deleteCrossValidationFoldAssignment() {
    _output._metalearner.deleteCrossValidationFoldAssignment();
  }
}
