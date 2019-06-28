package hex.ensemble;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashSet;
import water.udf.CFuncRef;
import water.util.Log;
import water.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;

import static hex.Model.Parameters.FoldAssignmentScheme.AUTO;
import static hex.Model.Parameters.FoldAssignmentScheme.Random;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsembleModel extends Model<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {

  // common parameters for the base models:
  public ModelCategory modelCategory;
  public long trainingFrameRows = -1;

  public String responseColumn = null;
  private NonBlockingHashSet<String> names = null;  // keep columns as a set for easier comparison
  
  public enum StackingStrategy {
    cross_validation,
    blending
  }

  // TODO: add a separate holdout dataset for the ensemble
  // TODO: add a separate overall cross-validation for the ensemble, including _fold_column and FoldAssignmentScheme / _fold_assignment

  public StackedEnsembleModel(Key selfKey, StackedEnsembleParameters parms, StackedEnsembleOutput output) {
    super(selfKey, parms, output);
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
    public long _seed;

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
      _metalearner_parameters = Metalearner.createParameters(algo);
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
    Frame levelOneFrame = new Frame(Key.<Frame>make("preds_levelone_" + this._key.toString() + fr._key));

    // TODO: don't score models that have 0 coefficients / aren't used by the metalearner.
    //   also we should be able to parallelize scoring of base models
    for (Key<Model> baseKey : this._parms._base_models) {
      Model base = baseKey.get(); 

      Frame basePreds = base.score(
          fr,
          "preds_base_" + this._key.toString() + fr._key,
          j,
          false
      );
      
      StackedEnsemble.addModelPredictionsToLevelOneFrame(base, basePreds, levelOneFrame);
      DKV.remove(basePreds._key); //Cleanup
      Frame.deleteTempFrameAndItsNonSharedVecs(basePreds, levelOneFrame);
    }

    // Add response column to level one frame
    levelOneFrame.add(this.responseColumn, adaptFrm.vec(this.responseColumn));
    
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
    }
    Frame.deleteTempFrameAndItsNonSharedVecs(levelOneFrame, adaptFrm);
    return predictFr;
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

  ModelMetrics doScoreMetricsOneFrame(Frame frame, Job job) {
      Frame pred = this.predictScoreImpl(frame, new Frame(frame), null, job, true, CFuncRef.from(_parms._custom_metric_func));
//      Frame pred = this.score(frame, null, job, true,  CFuncRef.from(_parms._custom_metric_func));
      pred.delete();
      return ModelMetrics.getFromDKV(this, frame);
  }

  void doScoreOrCopyMetrics(Job job) {
    // To get ensemble training metrics, the training frame needs to be re-scored since
    // training metrics from metalearner are not equal to ensemble training metrics.
    // The training metrics for the metalearner do not reflect true ensemble training metrics because
    // the metalearner was trained on cv preds, not training preds.  So, rather than clone the metalearner
    // training metrics, we have to re-score the training frame on all the base models, then send these
    // biased preds through to the metalearner, and then compute the metrics there.
    this._output._training_metrics = doScoreMetricsOneFrame(this._parms.train(), job);
    
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

  private DistributionFamily distributionFamily(Model aModel) {
    // TODO: hack alert: In DRF, _parms._distribution is always set to multinomial.  Yay.
    if (aModel instanceof DRFModel)
      if (aModel._output.isBinomialClassifier())
        return DistributionFamily.bernoulli;
      else if (aModel._output.isClassifier())
        return DistributionFamily.multinomial;
      else
        return DistributionFamily.gaussian;

    try {
      Field familyField = ReflectionUtils.findNamedField(aModel._parms, "_family");
      Field distributionField = (familyField != null ? null : ReflectionUtils.findNamedField(aModel, "_dist"));
      if (null != familyField) {
        // GLM only, for now
        GLMModel.GLMParameters.Family thisFamily = (GLMModel.GLMParameters.Family) familyField.get(aModel._parms);
        if (thisFamily == GLMModel.GLMParameters.Family.binomial) {
          return DistributionFamily.bernoulli;
        }

        try {
          return Enum.valueOf(DistributionFamily.class, thisFamily.toString());
        }
        catch (IllegalArgumentException e) {
          throw new H2OIllegalArgumentException("Don't know how to find the right DistributionFamily for Family: " + thisFamily);
        }
      }

      if (null != distributionField) {
        Distribution distribution = ((Distribution)distributionField.get(aModel));
        DistributionFamily distributionFamily;
        if (null != distribution)
          distributionFamily = distribution.distribution;
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

  void checkAndInheritModelProperties() {
    if (null == _parms._base_models || 0 == _parms._base_models.length)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; found 0.");

    if (null != _parms._metalearner_fold_column && 0 != _parms._metalearner_nfolds)
      throw new H2OIllegalArgumentException("Cannot specify fold_column and nfolds at the same time.");

    Model aModel = null;
    boolean beenHere = false;
    boolean blending_mode = _parms._blending != null;
    boolean cv_required_on_base_model = !blending_mode;
    
    //following variables are collected from the 1st base model (should be identical across base models), i.e. when beenHere=false
    int basemodel_nfolds = -1;
    Parameters.FoldAssignmentScheme basemodel_fold_assignment = null;
    String basemodel_fold_column = null;
    long seed = -1;
    //end 1st model collected fields
    
    trainingFrameRows = _parms.train().numRows();

    for (Key<Model> k : _parms._base_models) {
      aModel = DKV.getGet(k);
      if (null == aModel) {
        Log.warn("Failed to find base model; skipping: " + k);
        continue;
      }
      Log.debug("Checking properties for model " + k);
      if (!aModel.isSupervised()) {
        throw new H2OIllegalArgumentException("Base model is not supervised: " + aModel._key.toString());
      }

      if (beenHere) {
        // check that the base models are all consistent with first based model

        if (modelCategory != aModel._output.getModelCategory())
          throw new H2OIllegalArgumentException("Base models are inconsistent: there is a mix of different categories of models: " + Arrays.toString(_parms._base_models));

        // NOTE: if we loosen this restriction and fold_column is set add a check below.
        Frame aTrainingFrame = aModel._parms.train();
        if (trainingFrameRows != aTrainingFrame.numRows() && !this._parms._is_cv_model)
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different size (number of rows) training frames." +
              " Found: "+trainingFrameRows+" (StackedEnsemble) and "+aTrainingFrame.numRows()+" (model "+k+").");

        if (! responseColumn.equals(aModel._parms._response_column))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different response columns." +
              " Found: "+responseColumn+"(StackedEnsemble) and "+aModel._parms._response_column+" (model "+k+").");
        
//        if (blending_mode && _parms._blending.equals(aModel._parms._train)) {
//          throw new H2OIllegalArgumentException("Base model `"+k+"` was trained with the StackedEnsemble blending frame.");
//        }

        // TODO: we currently require xval; loosen this iff we add a separate holdout dataset for the ensemble
        if (cv_required_on_base_model) {
          if (aModel._parms._fold_assignment != basemodel_fold_assignment) {
            if ((aModel._parms._fold_assignment == AUTO && basemodel_fold_assignment == Random) ||
                (aModel._parms._fold_assignment == Random && basemodel_fold_assignment == AUTO)) {
              // A-ok
            } else {
              throw new H2OIllegalArgumentException("Base models are inconsistent: they use different fold_assignments.");
            }
          }

          // If we have a fold_column make sure nfolds from base models are consistent
          if (aModel._parms._fold_column == null && basemodel_nfolds != aModel._parms._nfolds)
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use different values for nfolds.");

          // If we don't have a fold_column require nfolds > 1
          if (aModel._parms._fold_column == null && aModel._parms._nfolds < 2)
            throw new H2OIllegalArgumentException("Base model does not use cross-validation: " + aModel._parms._nfolds);

          // NOTE: we already check that the training_frame checksums are the same, so
          // we don't need to check the Vec checksums here:
          if (aModel._parms._fold_column != null &&
              ! aModel._parms._fold_column.equals(basemodel_fold_column))
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use different fold_columns.");

          if (aModel._parms._fold_column == null &&
              basemodel_fold_assignment == Random &&
              aModel._parms._seed != seed)
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use random-seeded k-fold cross-validation but have different seeds.");

          if (! aModel._parms._keep_cross_validation_predictions)
            throw new H2OIllegalArgumentException("Base model does not keep cross-validation predictions: " + aModel._parms._nfolds);
        }

        // In GLM, we get _family instead of _distribution.
        // Further, we have Family.binomial instead of DistributionFamily.bernoulli.
        // We also handle DistributionFamily.AUTO in distributionFamily()
        //
        // Hack alert: DRF only does Bernoulli and Gaussian, so only compare _domains.length above.
        if (! (aModel instanceof DRFModel) && distributionFamily(aModel) != distributionFamily(this))
          Log.warn("Base models are inconsistent; they use different distributions: " + distributionFamily(this) + " and: " + distributionFamily(aModel) + ". Is this intentional?");

        // TODO: If we're set to DistributionFamily.AUTO then GLM might auto-conform the response column
        // giving us inconsistencies.
      } else {
        // !beenHere: this is the first base_model
        this.modelCategory = aModel._output.getModelCategory();
        this._dist = DistributionFactory.getDistribution(distributionFamily(aModel));
        responseColumn = aModel._parms._response_column;

        if (! responseColumn.equals(_parms._response_column))
          throw  new H2OIllegalArgumentException("StackedModel response_column must match the response_column of each base model.  Found: " + responseColumn + " and: " + _parms._response_column);

        basemodel_nfolds = aModel._parms._nfolds;
        basemodel_fold_assignment = aModel._parms._fold_assignment;
        if (basemodel_fold_assignment == AUTO) basemodel_fold_assignment = Random;
        basemodel_fold_column = aModel._parms._fold_column;
        seed = aModel._parms._seed;
        _parms._distribution = aModel._parms._distribution;
        beenHere = true;
      }

    } // for all base_models

    if (null == aModel)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; " + _parms._base_models.length + " were specified but none of those were found: " + Arrays.toString(_parms._base_models));

  }
  
  void deleteBaseModelPredictions() {
    if (_output._base_model_predictions_keys != null) {
      for (Key<Frame> key : _output._base_model_predictions_keys) {
        if (_output._levelone_frame_id != null && key.get() != null)
          Frame.deleteTempFrameAndItsNonSharedVecs(key.get(), _output._levelone_frame_id);
        else
          key.remove();
      }
      _output._base_model_predictions_keys = null;
    }
  }

  @Override protected Futures remove_impl(Futures fs ) {
    deleteBaseModelPredictions(); 
    if (_output._metalearner != null)
      _output._metalearner.remove(fs);
    if (_output._levelone_frame_id != null)
      _output._levelone_frame_id.remove(fs);

    return super.remove_impl(fs);
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

