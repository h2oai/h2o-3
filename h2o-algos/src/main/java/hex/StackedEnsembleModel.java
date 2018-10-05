package hex;

import hex.deeplearning.DeepLearningModel;
import hex.ensemble.StackedEnsemble;
import hex.ensemble.StackedEnsembleMojoWriter;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashSet;
import water.udf.CFuncRef;
import water.util.IcedHashMap;
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
  //public int nfolds = -1; //From 1st base model

  // Get from 1st base model (should be identical across base models)
  public int basemodel_nfolds = -1;  //From 1st base model
  public Parameters.FoldAssignmentScheme basemodel_fold_assignment;  //From 1st base model
  public String basemodel_fold_column;  //From 1st base model

  public long seed = -1; //From 1st base model


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
    // Should we keep the level-one frame of cv preds + repsonse col?
    public boolean _keep_levelone_frame = false;

    // Metalearner params
    public int _metalearner_nfolds;
    public Parameters.FoldAssignmentScheme _metalearner_fold_assignment;
    public String _metalearner_fold_column;

    //What to use as a metalearner (GLM, GBM, DRF, or DeepLearning)
    public enum MetalearnerAlgorithm {
      AUTO,
      glm,
      gbm,
      drf,
      deeplearning
    }
    public MetalearnerAlgorithm _metalearner_algorithm = MetalearnerAlgorithm.AUTO;
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
    public void initMetalearnerParams(MetalearnerAlgorithm algo) {
      _metalearner_algorithm = algo;
      switch (algo) {
        case AUTO:
        case glm:
          _metalearner_parameters = new GLMModel.GLMParameters();
          break;
        case gbm:
          _metalearner_parameters = new GBMModel.GBMParameters();
          break;
        case drf:
          _metalearner_parameters = new DRFModel.DRFParameters();
          break;
        case deeplearning:
          _metalearner_parameters = new DeepLearningModel.DeepLearningParameters();
          break;
      }
    }

  }

  public static class StackedEnsembleOutput extends Model.Output {
    public StackedEnsembleOutput() { super(); }
    public StackedEnsembleOutput(StackedEnsemble b) { super(b); }

    public StackedEnsembleOutput(Job job) { _job = job; }
    // The metalearner model (e.g., a GLM that has a coefficient for each of the base_learners).
    public Model _metalearner;
    public Frame _levelone_frame_id;
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
    // Build up the names & domains.
    String[] names = makeScoringNames();
    String[][] domains = new String[names.length][];
    domains[0] = names.length == 1 ? null : computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();

    // TODO: optimize these DKV lookups:
    Frame levelOneFrame = new Frame(Key.<Frame>make("preds_levelone_" + this._key.toString() + fr._key));
    int baseIdx = 0;
    Frame[] base_prediction_frames = new Frame[this._parms._base_models.length];

    // TODO: don't score models that have 0 coefficients / aren't used by the metalearner.
    for (Key<Model> baseKey : this._parms._base_models) {
      Model base = baseKey.get();  // TODO: cacheme!

      // adapt fr for each base_model

      // TODO: cache: don't need to call base.adaptTestForTrain() if the
      // base_model has the same names and domains as one we've seen before.
      // Such base_models can share their adapted frame.
      Frame adaptedFrame = new Frame(fr);
      base.adaptTestForTrain(adaptedFrame, true, computeMetrics);

      // TODO: parallel scoring for the base_models
      BigScore baseBs = (BigScore) base.makeBigScoreTask(domains, names, adaptedFrame,
                                                         computeMetrics, true,
                                                         j, customMetricFunc).doAll(names.length, Vec.T_NUM, adaptedFrame);
      Frame basePreds = baseBs.outputFrame(Key.<Frame>make("preds_base_" + this._key.toString() + fr._key), names, domains);
      //Need to remove 'predict' column from multinomial since it contains outcome
      if (base._output.isMultinomialClassifier()) {
        basePreds.remove("predict");
      }
      base_prediction_frames[baseIdx] = basePreds;
      StackedEnsemble.addModelPredictionsToLevelOneFrame(base, basePreds, levelOneFrame);
      DKV.remove(basePreds._key); //Cleanup

      Frame.deleteTempFrameAndItsNonSharedVecs(adaptedFrame, fr);

      baseIdx++;
    }

    // Add response column to level one frame
    levelOneFrame.add(this.responseColumn, adaptFrm.vec(this.responseColumn));
    
    // TODO: what if we're running multiple in parallel and have a name collision?
    Log.info("Finished creating \"level one\" frame for scoring: " + levelOneFrame.toString());

    // Score the dataset, building the class distribution & predictions

    Model metalearner = this._output._metalearner;
    Frame levelOneAdapted = new Frame(levelOneFrame);
    metalearner.adaptTestForTrain(levelOneAdapted, true, computeMetrics);

    String[] metaNames = metalearner.makeScoringNames();
    String[][] metaDomains = new String[metaNames.length][];
    metaDomains[0] = metaNames.length == 1 ? null : !computeMetrics ? metalearner._output._domains[metalearner._output._domains.length-1] : levelOneAdapted.lastVec().domain();

    BigScore metaBs = (BigScore) metalearner.makeBigScoreTask(metaDomains,
                                                              metaNames,
                                                              levelOneAdapted,
                                                              computeMetrics,
                                                              true,
                                                              j,
                                                              CFuncRef.from(_parms._custom_metric_func)).
            doAll(metaNames.length, Vec.T_NUM, levelOneAdapted);

    if (computeMetrics) {
      ModelMetrics mmMetalearner = metaBs._mb.makeModelMetrics(metalearner, levelOneFrame, levelOneAdapted, metaBs.outputFrame());
      // This has just stored a ModelMetrics object for the (metalearner, preds_levelone) Model/Frame pair.
      // We need to be able to look it up by the (this, fr) pair.
      // The ModelMetrics object for the metalearner will be removed when the metalearner is removed.
      ModelMetrics mmStackedEnsemble = mmMetalearner.deepCloneWithDifferentModelAndFrame(this, fr);
      this.addModelMetrics(mmStackedEnsemble);
    }

    Frame.deleteTempFrameAndItsNonSharedVecs(levelOneAdapted, levelOneFrame);
    return metaBs.outputFrame(Key.<Frame>make(destination_key), metaNames, metaDomains);
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

  public ModelMetrics doScoreMetricsOneFrame(Frame frame, Job job) {
      Frame pred = this.predictScoreImpl(frame, new Frame(frame), null, job, true, CFuncRef.from(_parms._custom_metric_func));
      pred.remove();
      return ModelMetrics.getFromDKV(this, frame);
  }

  public void doScoreOrCopyMetrics(Job job) {
    // To get ensemble training metrics, the training frame needs to be re-scored since
    // training metrics from metalearner are not equal to ensemble training metrics.
    // The training metrics for the metalearner do not reflect true ensemble training metrics because
    // the metalearner was trained on cv preds, not training preds.  So, rather than clone the metalearner
    // training metrics, we have to re-score the training frame on all the base models, then send these
    // biased preds through to the metalearner, and then compute the metrics there.
    this._output._training_metrics = doScoreMetricsOneFrame(this._parms.train(), job);
    // Validation metrics can be copied from metalearner (may be null).
    // Validation frame was already piped through so there's no need to re-do that to get the same results.
    // TODO: Look into whether we should deepClone validation metrics instead
    //this._output._validation_metrics = this._output._metalearner._output._validation_metrics.deepCloneWithDifferentModelAndFrame(this, this._output._metalearner._parms.valid());  #valid or train?
    this._output._validation_metrics = this._output._metalearner._output._validation_metrics;
    // Cross-validation metrics can be copied from metalearner (may be null).
    // For cross-validation metrics, we use metalearner cross-validation metrics as a proxy for the ensemble
    // cross-validation metrics -- the true k-fold cv metrics for the ensemble would require training k sets of
    // cross-validated base models (rather than a single set of cross-validated base models), which is extremely
    // computationally expensive and awkward from the standpoint of the existing Stacked Ensemble API.
    // More info: https://0xdata.atlassian.net/browse/PUBDEV-3971
    // Need to do DeepClone because otherwise framekey is incorrect (metalearner train is levelone not train)
    if (null != this._output._metalearner._output._cross_validation_metrics)
      this._output._cross_validation_metrics = this._output._metalearner._output._cross_validation_metrics.deepCloneWithDifferentModelAndFrame(this, this._output._metalearner._parms.train());
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

  public void checkAndInheritModelProperties() {
    if (null == _parms._base_models || 0 == _parms._base_models.length)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; found 0.");

    if (null != _parms._metalearner_fold_column && 0 != _parms._metalearner_nfolds)
      throw new H2OIllegalArgumentException("Cannot specify fold_column and nfolds at the same time.");

    Model aModel = null;
    boolean beenHere = false;
    trainingFrameRows = _parms.train().numRows();

    for (Key<Model> k : _parms._base_models) {
      aModel = DKV.getGet(k);
      if (null == aModel) {
        Log.warn("Failed to find base model; skipping: " + k);
        continue;
      }
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
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different size(number of rows) training frames.  Found number of rows: " + trainingFrameRows + " and: " + aTrainingFrame.numRows() + ".");

        if (! responseColumn.equals(aModel._parms._response_column))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different response columns.  Found: " + responseColumn + " and: " + aModel._parms._response_column + ".");

        // TODO: we currently require xval; loosen this iff we add a separate holdout dataset for the ensemble

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
        this._dist = new Distribution(distributionFamily(aModel));
        _output._domains = Arrays.copyOf(aModel._output._domains, aModel._output._domains.length);

        // TODO: set _parms._train to aModel._parms.train()

        _output.setNames(aModel._output._names);
        this.names = new NonBlockingHashSet<>();
        this.names.addAll(Arrays.asList(aModel._output._names));

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

  @Override protected Futures remove_impl(Futures fs ) {
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

}

