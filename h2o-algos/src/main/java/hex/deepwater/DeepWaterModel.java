package hex.deepwater;

import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.DeepWaterModelV3;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.api.schemas3.ModelSchemaV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.FrameUtils;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.RandomUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static hex.ModelMetrics.calcVarImp;
import static water.H2O.technote;

/**
 * The Deep Learning model
 * It contains a DeepWaterModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */
public class DeepWaterModel extends Model<DeepWaterModel,DeepWaterParameters,DeepWaterModelOutput> implements Model.DeepFeatures {

  @Override public DeepwaterMojoWriter getMojo() { return new DeepwaterMojoWriter(this); }

  // Default publicly visible Schema is V2
  public ModelSchemaV3 schema() { return new DeepWaterModelV3(); }

  void set_model_info(DeepWaterModelInfo mi) {
    model_info = mi;
  }

  final public DeepWaterModelInfo model_info() { return model_info; }

  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }

//  final public VarImp varImp() { return _output.errors.variable_importances; }

  private volatile DeepWaterModelInfo model_info;

  // timing
  private long total_checkpointed_run_time_ms; //time spent in previous models
  private long total_training_time_ms; //total time spent running (training+scoring, including all previous models)
  private long total_scoring_time_ms; //total time spent scoring (including all previous models)
  long total_setup_time_ms; //total time spent setting up (including all previous models)
  private long time_of_start_ms; //start time for this model (this cp restart)

  // auto-tuning
  long actual_train_samples_per_iteration;
  long time_for_iteration_overhead_ms; //helper for auto-tuning: time in microseconds for collective bcast/reduce of the model

  // helpers for diagnostics
  double epoch_counter;
  int iterations;
  private boolean stopped_early;
  long training_rows;
  long validation_rows;

  // Keep the best model so far, based on a single criterion (overall class. error or MSE)
  private float _bestLoss = Float.POSITIVE_INFINITY;

  Key actual_best_model_key;

  static final String unstable_msg = technote(4,
      "\n\nTrying to predict with an unstable model." +
          "\nJob was aborted due to observed numerical instability (exponential growth)."
          + "\nEither the weights or the bias values are unreasonably large or lead to large activation values."
          + "\nTry a different network architecture, a bounded activation function (tanh), adding regularization"
          + "\n(via dropout) or use a smaller learning rate and/or momentum.");

  public DeepWaterScoringInfo last_scored() { return (DeepWaterScoringInfo) super.last_scored(); }


  /**
   * Get the parameters actually used for model building, not the user-given ones (_parms)
   * They might differ since some defaults are filled in, and some invalid combinations are auto-disabled in modifyParams
   * @return actually used parameters
   */
  public final DeepWaterParameters get_params() { return model_info.get_params(); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      case AutoEncoder: return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
      default: throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
    }
  }

  static DataInfo makeDataInfo(Frame train, Frame valid, DeepWaterParameters parms) {
    double x = 0.782347234;
    boolean identityLink = new Distribution(parms).link(x) == x;
    return new DataInfo(
        train,
        valid,
        parms._autoencoder ? 0 : 1, //nResponses
        parms._autoencoder || parms._use_all_factor_levels, //use all FactorLevels for auto-encoder
        parms._standardize ? (parms._autoencoder ? DataInfo.TransformType.NORMALIZE : parms._sparse ? DataInfo.TransformType.DESCALE : DataInfo.TransformType.STANDARDIZE) : DataInfo.TransformType.NONE, //transform predictors
        !parms._standardize || train.lastVec().isCategorical() ? DataInfo.TransformType.NONE : identityLink ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, //transform response for regression with identity link
        parms._missing_values_handling == DeepWaterParameters.MissingValuesHandling.Skip, //whether to skip missing
        false, // do not replace NAs in numeric cols with mean
        true,  // always add a bucket for missing values
        parms._weights_column != null, // observation weights
        parms._offset_column != null,
        parms._fold_column != null
    );
  }

  /** Constructor to restart from a checkpointed model
   *  @param destKey New destination key for the model
   *  @param parms User-given parameters for checkpoint restart
   *  @param cp Checkpoint to restart from
   */
  public DeepWaterModel(final Key<DeepWaterModel> destKey, final DeepWaterParameters parms, final DeepWaterModel cp, final DataInfo dataInfo) {
    super(destKey, parms == null ? (DeepWaterParameters)cp._parms.clone() : IcedUtils.deepCopy(parms), (DeepWaterModelOutput)cp._output.clone());
    DeepWaterParameters.Sanity.modifyParms(_parms, _parms, cp._output.nclasses()); //sanitize the model_info's parameters
    assert(_parms != cp._parms); //make sure we have a clone
    assert (_parms._checkpoint == cp._key);
    model_info = IcedUtils.deepCopy(cp.model_info);
    model_info._dataInfo = dataInfo;
    assert(model_info._network != null);
    assert(model_info._modelparams != null);
    model_info.javaToNative();
    _dist = new Distribution(get_params());
    assert(_dist.distribution != DistributionFamily.AUTO); // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    actual_best_model_key = cp.actual_best_model_key;
    if (actual_best_model_key.get() == null) {
      DeepWaterModel best = IcedUtils.deepCopy(cp);
      //best.model_info.data_info = model_info.data_info; // Note: we currently DO NOT use the checkpoint's data info - as data may change during checkpoint restarts
      actual_best_model_key = Key.<DeepWaterModel>make(H2O.SELF);
      DKV.put(actual_best_model_key, best);
    }
    time_of_start_ms = cp.time_of_start_ms;
    total_training_time_ms = cp.total_training_time_ms;
    total_checkpointed_run_time_ms = cp.total_training_time_ms;
    total_scoring_time_ms = cp.total_scoring_time_ms;
    total_setup_time_ms = cp.total_setup_time_ms;
    training_rows = cp.training_rows; //copy the value to display the right number on the model page before training has started
    validation_rows = cp.validation_rows; //copy the value to display the right number on the model page before training has started
    _bestLoss = cp._bestLoss;
    epoch_counter = cp.epoch_counter;
    iterations = cp.iterations;

    // deep clone scoring history
    scoringInfo = cp.scoringInfo.clone();
    for (int i=0; i< scoringInfo.length;++i)
      scoringInfo[i] = IcedUtils.deepCopy(cp.scoringInfo[i]);
    _output.errors = last_scored();
    _output._scoring_history = DeepWaterScoringInfo.createScoringHistoryTable(scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
    _output._variable_importances = calcVarImp(last_scored().variable_importances);
    if (dataInfo!=null) {
      _output.setNames(dataInfo._adaptedFrame.names());
      _output._domains = dataInfo._adaptedFrame.domains();
    }
    assert(_key.equals(destKey));
  }

  private void setDataInfoToOutput(DataInfo dinfo) {
    if (dinfo == null) return;
    // update the model's expected frame format - needed for train/test adaptation
    _output.setNames(dinfo._adaptedFrame.names());
    _output._domains = dinfo._adaptedFrame.domains();
    _output._nums = dinfo._nums;
    _output._cats = dinfo._cats;
    _output._catOffsets = dinfo._catOffsets;
    _output._normMul = dinfo._normMul;
    _output._normSub = dinfo._normSub;
    _output._normRespMul = dinfo._normRespMul;
    _output._normRespSub = dinfo._normRespSub;
    _output._useAllFactorLevels = dinfo._useAllFactorLevels;
  }

  /**
   * Regular constructor (from scratch)
   * @param destKey destination key
   * @param params DL parameters
   * @param output DL model output
   * @param nClasses Number of classes (1 for regression or autoencoder)
   */
  public DeepWaterModel(final Key<DeepWaterModel> destKey, final DeepWaterParameters params, final DeepWaterModelOutput output, Frame train, Frame valid, int nClasses) {
    super(destKey, params, output);
    if (H2O.getCloudSize() != 1)
      throw new IllegalArgumentException("Deep Water currently only supports execution of 1 node.");
    _output._origNames = params._train.get().names();
    _output._origDomains = params._train.get().domains();

    DeepWaterParameters parms = (DeepWaterParameters) params.clone(); //make a copy, don't change model's parameters
    DeepWaterParameters.Sanity.modifyParms(parms, parms, nClasses); //sanitize the model_info's parameters
    DataInfo dinfo = null;
    if (parms._problem_type == DeepWaterParameters.ProblemType.dataset) {
      dinfo = makeDataInfo(train, valid, parms);
      DKV.put(dinfo);
      setDataInfoToOutput(dinfo);
      // either provide no image_shape (i.e., (0,0)), or provide both values and channels >= 1 (to turn it into an image problem)
      if (parms._image_shape != null && parms._image_shape[0] != 0) {
        if (parms._image_shape[0] < 0) {
          throw new IllegalArgumentException("image_shape must either have both values == 0 or both values >= 1 for " + parms._problem_type.getClass().toString() + "=" + parms._problem_type.toString());
        }
        if (parms._image_shape[1] <= 0) {
          throw new IllegalArgumentException("image_shape must either have both values == 0 or both values >= 1 for " + parms._problem_type.getClass().toString() + "=" + parms._problem_type.toString());
        }
        if (parms._channels <= 0) {
          throw new IllegalArgumentException("channels must be >= 1 when image_shape is provided for " + parms._problem_type.getClass().toString() + "=" + parms._problem_type.toString());
        }
        if (dinfo.fullN() != parms._image_shape[0] * parms._image_shape[1] * parms._channels) {
          throw new IllegalArgumentException("Data input size mismatch: Expect image_shape[0] x image_shape[1] x channels == #cols(H2OFrame), but got: "
              + parms._image_shape[0] + " x " + parms._image_shape[1] + " x " + parms._channels + " != " + dinfo.fullN() + ". Check these parameters, or disable ignore_const_cols.");
        }
      }
    }
    model_info = new DeepWaterModelInfo(parms, nClasses, dinfo != null ? dinfo.fullN() : -1);
    model_info._dataInfo = dinfo;
    if (dinfo!=null) {
      FrameUtils.printTopCategoricalLevels(dinfo._adaptedFrame, dinfo.fullN() > 10000, 10);
      Log.info("Building the model on " + dinfo.numNums() + " numeric features and " + dinfo.numCats() + " (one-hot encoded) categorical features.");
    }

    // now, parms is get_params();
    _dist = new Distribution(get_params());
    assert(_dist.distribution != DistributionFamily.AUTO); // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    actual_best_model_key = Key.make(H2O.SELF);
    if (get_params()._nfolds != 0) actual_best_model_key = null;
    if (!get_params()._autoencoder) {
      scoringInfo = new DeepWaterScoringInfo[1];
      scoringInfo[0] = new DeepWaterScoringInfo();
      scoringInfo[0].validation = (get_params()._valid != null);
      scoringInfo[0].time_stamp_ms = System.currentTimeMillis();
      _output.errors = last_scored();
      _output._scoring_history = DeepWaterScoringInfo.createScoringHistoryTable(scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
    }
    time_of_start_ms = System.currentTimeMillis();
    assert _key.equals(destKey);
    boolean fail = false;
    long byte_size = 0;
    try {
      byte_size = new AutoBuffer().put(this).buf().length;
    } catch(Throwable t) {
      fail = true;
    }
    if (byte_size > Value.MAX || fail)
      throw new IllegalArgumentException(technote(5, "Model is too large to fit into the DKV (larger than " + PrettyPrint.bytes(Value.MAX) + ")."));
  }

  long _timeLastIterationEnter;
  private long _timeLastScoreStart; //start actual scoring
  private long _timeLastScoreEnd;  //finished actual scoring
  private long _timeLastPrintStart;

  private void checkTimingConsistency() {
    assert(total_scoring_time_ms <= total_training_time_ms);
    assert(total_setup_time_ms <= total_training_time_ms);
    assert(total_setup_time_ms+total_scoring_time_ms <= total_training_time_ms);
    assert(total_training_time_ms >= total_checkpointed_run_time_ms);
    assert(total_checkpointed_run_time_ms >= 0);
    assert(total_training_time_ms >= 0);
    assert(total_scoring_time_ms >= 0);
  }

  private void updateTiming(Key<Job> job_key) {
    final long now = System.currentTimeMillis();
    long start_time_current_model = job_key.get().start_time();
    total_training_time_ms = total_checkpointed_run_time_ms + (now - start_time_current_model);
    checkTimingConsistency();
  }

  /**
   * Score this DeepWater model
   * @param fTrain potentially downsampled training data for scoring
   * @param fValid  potentially downsampled validation data for scoring
   * @param jobKey key of the owning job
   * @param iteration Map/Reduce iteration count
   * @return true if model building is ongoing
   */
  boolean doScoring(Frame fTrain, Frame fValid, Key<Job> jobKey, int iteration, boolean finalScoring) {
    final long now = System.currentTimeMillis();
    final double time_since_last_iter = now - _timeLastIterationEnter;
    updateTiming(jobKey);
    _timeLastIterationEnter = now;
    epoch_counter = (double)model_info().get_processed_total()/training_rows;

    boolean keep_running;
    // Auto-tuning
    // if multi-node and auto-tuning and at least 10 ms for communication and per-iteration overhead (to avoid doing thins on multi-JVM on same node),
    // then adjust the auto-tuning parameter 'actual_train_samples_per_iteration' such that the targeted ratio of comm to comp is achieved
    if (get_params()._train_samples_per_iteration == -2 && iteration > 1) {
      Log.debug("Auto-tuning train_samples_per_iteration.");
      if (time_for_iteration_overhead_ms > 10) {
        Log.debug("  Time taken for per-iteration comm overhead: " + PrettyPrint.msecs(time_for_iteration_overhead_ms, true));
        Log.debug("  Time taken for Map/Reduce iteration: " + PrettyPrint.msecs((long) time_since_last_iter, true));
        final double comm_to_work_ratio = time_for_iteration_overhead_ms / time_since_last_iter;
        Log.debug("  Ratio of per-iteration comm overhead to computation: " + String.format("%.5f", comm_to_work_ratio));
        Log.debug("  target_comm_to_work: " + get_params()._target_ratio_comm_to_comp);
        Log.debug("Old value of train_samples_per_iteration: " + actual_train_samples_per_iteration);
        double correction = get_params()._target_ratio_comm_to_comp / comm_to_work_ratio;
        correction = Math.max(0.5,Math.min(2, correction)); //it's ok to train up to 2x more training rows per iteration, but not fewer than half.
        if (Math.abs(correction) < 0.8 || Math.abs(correction) > 1.2) { //don't correct unless it's significant (avoid slow drift)
          actual_train_samples_per_iteration /= correction;
          actual_train_samples_per_iteration = Math.max(1, actual_train_samples_per_iteration);
          Log.debug("New value of train_samples_per_iteration: " + actual_train_samples_per_iteration);
        } else {
          Log.debug("Keeping value of train_samples_per_iteration the same (would deviate too little from previous value): " + actual_train_samples_per_iteration);
        }
      } else {
        Log.debug("Iteration overhead is faster than 10 ms. Not modifying train_samples_per_iteration: " + actual_train_samples_per_iteration);
      }
    }

    keep_running = (epoch_counter < get_params()._epochs) && !stopped_early;
    final long sinceLastScore = now -_timeLastScoreStart;

    // this is potentially slow - only do every so often
    if( !keep_running || get_params()._score_each_iteration ||
        (sinceLastScore > get_params()._score_interval *1000 //don't score too often
            &&(double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < get_params()._score_duty_cycle) ) { //duty cycle
      Log.info(logNvidiaStats());
      jobKey.get().update(0,"Scoring on " + fTrain.numRows() + " training samples" +(fValid != null ? (", " + fValid.numRows() + " validation samples") : ""));
      final boolean printme = !get_params()._quiet_mode;
      _timeLastScoreStart = System.currentTimeMillis();
      DeepWaterScoringInfo scoringInfo = new DeepWaterScoringInfo();
      scoringInfo.time_stamp_ms = _timeLastScoreStart;
      updateTiming(jobKey);
      scoringInfo.total_training_time_ms = total_training_time_ms;
      scoringInfo.total_scoring_time_ms = total_scoring_time_ms;
      scoringInfo.total_setup_time_ms = total_setup_time_ms;
      scoringInfo.epoch_counter = epoch_counter;
      scoringInfo.iterations = iterations;
      scoringInfo.training_samples = (double)model_info().get_processed_total();
      scoringInfo.validation = fValid != null;
      scoringInfo.score_training_samples = fTrain.numRows();
      scoringInfo.score_validation_samples = get_params()._score_validation_samples;
      scoringInfo.is_classification = _output.isClassifier();
      scoringInfo.is_autoencoder = _output.isAutoencoder();

      if (printme) Log.info("Scoring the model.");
      // compute errors
      final String m = model_info().toString();
      if (m.length() > 0) Log.info(m);

      // For GainsLift and Huber, we need the full predictions to compute the model metrics
      boolean needPreds = _output.nclasses() == 2 /* gains/lift table requires predictions */ || get_params()._distribution==DistributionFamily.huber;

      // Scoring on training data
      ModelMetrics mtrain;
      Frame preds = null;
      if (needPreds) {
        // allocate predictions since they are needed
        preds = score(fTrain);
        mtrain = ModelMetrics.getFromDKV(this, fTrain);
      } else {
        // no need to allocate predictions
        ModelMetrics.MetricBuilder mb = scoreMetrics(fTrain);
        mtrain = mb.makeModelMetrics(this,fTrain,fTrain,null);
      }
      if (preds!=null) preds.remove();
      _output._training_metrics = mtrain;
      scoringInfo.scored_train = new ScoreKeeper(mtrain);
      ModelMetricsSupervised mm1 = (ModelMetricsSupervised)mtrain;
      if (mm1 instanceof ModelMetricsBinomial) {
        ModelMetricsBinomial mm = (ModelMetricsBinomial)(mm1);
        scoringInfo.training_AUC = mm._auc;
      }
      if (fTrain.numRows() != training_rows) {
        _output._training_metrics._description = "Metrics reported on temporary training frame with " + fTrain.numRows() + " samples";
      } else if (fTrain._key != null && fTrain._key.toString().contains("chunks")){
        _output._training_metrics._description = "Metrics reported on temporary (load-balanced) training frame";
      } else {
        _output._training_metrics._description = "Metrics reported on full training frame";
      }

      // Scoring on validation data
      ModelMetrics mvalid;
      if (fValid != null) {
        preds = null;
        if (needPreds) {
          // allocate predictions since they are needed
          preds = score(fValid);
          mvalid = ModelMetrics.getFromDKV(this, fValid);
        } else {
          // no need to allocate predictions
          ModelMetrics.MetricBuilder mb = scoreMetrics(fValid);
          mvalid = mb.makeModelMetrics(this, fValid, fValid,null);
        }
        if (preds!=null) preds.remove();
        _output._validation_metrics = mvalid;
        scoringInfo.scored_valid = new ScoreKeeper(mvalid);
        if (mvalid != null) {
          if (mvalid instanceof ModelMetricsBinomial) {
            ModelMetricsBinomial mm = (ModelMetricsBinomial) mvalid;
            scoringInfo.validation_AUC = mm._auc;
          }
          if (fValid.numRows() != validation_rows) {
            _output._validation_metrics._description = "Metrics reported on temporary validation frame with " + fValid.numRows() + " samples";
          } else if (fValid._key != null && fValid._key.toString().contains("chunks")){
            _output._validation_metrics._description = "Metrics reported on temporary (load-balanced) validation frame";
          } else {
            _output._validation_metrics._description = "Metrics reported on full validation frame";
          }
        }
      }
//      if (get_params()._variable_importances) {
//        if (!get_params()._quiet_mode) Log.info("Computing variable importances.");
//        throw H2O.unimpl();
//        final float[] vi = model_info().computeVariableImportances();
//        scoringInfo.variable_importances = new VarImp(vi, Arrays.copyOfRange(model_info().data_info().coefNames(), 0, vi.length));
//      }

      _timeLastScoreEnd = System.currentTimeMillis();
      long scoringTime = _timeLastScoreEnd - _timeLastScoreStart;
      total_scoring_time_ms += scoringTime;
      updateTiming(jobKey);
      // update the scoringInfo object to report proper speed
      scoringInfo.total_training_time_ms = total_training_time_ms;
      scoringInfo.total_scoring_time_ms = total_scoring_time_ms;
      scoringInfo.this_scoring_time_ms = scoringTime;
      // enlarge the error array by one, push latest score back
      if (this.scoringInfo == null) {
        this.scoringInfo = new DeepWaterScoringInfo[]{scoringInfo};
      } else {
        DeepWaterScoringInfo[] err2 = new DeepWaterScoringInfo[this.scoringInfo.length + 1];
        System.arraycopy(this.scoringInfo, 0, err2, 0, this.scoringInfo.length);
        err2[err2.length - 1] = scoringInfo;
        this.scoringInfo = err2;
      }
      _output.errors = last_scored();
      _output._scoring_history = DeepWaterScoringInfo.createScoringHistoryTable(this.scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
      _output._model_summary = model_info.createSummaryTable();

      // always keep a copy of the best model so far (based on the following criterion)
      if (!finalScoring) {
        if (actual_best_model_key != null && get_params()._overwrite_with_best_model && (
                // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                (DKV.get(actual_best_model_key) != null && !(loss() >= DKV.get(actual_best_model_key).<DeepWaterModel>get().loss() ) )
                        ||
                        // otherwise, compare against our own _bestError
                        (DKV.get(actual_best_model_key) == null && loss() < _bestLoss)
        ) ) {
          _bestLoss = loss();
          model_info.nativeToJava();
          putMeAsBestModel(actual_best_model_key);
        }
        // print the freshly scored model to ASCII
        if (keep_running && printme)
          Log.info(toString());
        if (ScoreKeeper.stopEarly(ScoringInfo.scoreKeepers(scoring_history()),
                get_params()._stopping_rounds, _output.isClassifier(), get_params()._stopping_metric, get_params()._stopping_tolerance, "model's last", true
        )) {
          Log.info("Convergence detected based on simple moving average of the loss function for the past " + get_params()._stopping_rounds + " scoring events. Model building completed.");
          stopped_early = true;
        }
        if (printme) Log.info("Time taken for scoring and diagnostics: " + PrettyPrint.msecs(scoringInfo.this_scoring_time_ms, true));
      }
    }
    if (stopped_early) {
      // pretend as if we finished all epochs to get the progress bar pretty (especially for N-fold and grid-search)
      ((Job) DKV.getGet(jobKey)).update((long) (get_params()._epochs * training_rows));
      update(jobKey);
      return false;
    }
    progressUpdate(jobKey, keep_running);
    //update(jobKey);
    return keep_running;
  }

  private void putMeAsBestModel(Key bestModelKey) {
    DKV.put(bestModelKey, IcedUtils.deepCopy(this));
    assert DKV.get(bestModelKey) != null;
    assert ((DeepWaterModel)DKV.getGet(bestModelKey)).compareTo(this) <= 0;
  }

  private void progressUpdate(Key<Job> job_key, boolean keep_running) {
    updateTiming(job_key);
    Job job = job_key.get();
    double progress = job.progress();
//    Log.info("2nd speed: (samples: " + model_info().get_processed_total() + ", total_run_time: " + total_training_time_ms + ", total_scoring_time: " + total_scoring_time_ms + ", total_setup_time: " + total_setup_time_ms + ")");
    float speed = (float)(model_info().get_processed_total() * 1000. / (total_training_time_ms -total_scoring_time_ms-total_setup_time_ms));
    assert(speed >= 0) : "negative speed computed! (total_run_time: " + total_training_time_ms + ", total_scoring_time: " + total_scoring_time_ms + ", total_setup_time: " + total_setup_time_ms + ")";
    String msg =
            "Iterations: " + String.format("%,d", iterations)
            + ". Epochs: " + String.format("%g", epoch_counter)
            + ". Speed: " + (speed>10 ? String.format("%d", (int)speed) : String.format("%g", speed)) + " samples/sec."
            + (progress == 0 ? "" : " Estimated time left: " + PrettyPrint.msecs((long) (total_training_time_ms * (1. - progress) / progress), true));
    job.update(actual_train_samples_per_iteration,msg); //mark the amount of work done for the progress bar
    long now = System.currentTimeMillis();
    long sinceLastPrint = now -_timeLastPrintStart;
    if (!keep_running || sinceLastPrint > get_params()._score_interval * 1000) { //print this after every score_interval, not considering duty cycle
      _timeLastPrintStart = now;
      if (!get_params()._quiet_mode) {
        Log.info(
                "Training time: " + PrettyPrint.msecs(total_training_time_ms, true) + " (scoring: " + PrettyPrint.msecs(total_scoring_time_ms, true) + "). "
                + "Processed " + String.format("%,d", model_info().get_processed_total()) + " samples" + " (" + String.format("%.3f", epoch_counter) + " epochs).\n");
        Log.info(msg);
      }
    }
  }

  final private AtomicBoolean nukeBackend = new AtomicBoolean(false);

  @Override
  protected void setupBigScoreLocal() {
    nukeBackend.set(null == model_info()._backend);
    if(nukeBackend.get()) {
      synchronized (nukeBackend) {
        if(null == model_info()._backend){
          model_info().javaToNative();
        }
      }
    }
  }

  @Override
  protected void closeBigScoreLocal() {
    if(nukeBackend.get()) {
      synchronized (nukeBackend) {
        if(nukeBackend.get()){
          model_info().nukeBackend();
          nukeBackend.set(false);
        }
      }
    }
  }

  /**
   * Single-instance scoring - slow, not optimized for mini-batches - do not use unless you know what you're doing
   * @param data One single observation unrolled into a double[], with a length equal to the number of input neurons
   * @param preds Array to store the predictions in (nclasses+1)
   * @return vector of [0, p0, p1, p2, etc.]
   */
  @Override protected double[] score0(double[] data, double[] preds) {
    //allocate a big enough array for the model to be able to score with mini_batch
    float[] f = new float[_parms._mini_batch_size * data.length];
    for (int i=0; i<data.length; ++i) f[i] = (float)data[i]; //only fill the first observation
    //float[] predFloats = model_info().predict(f);
    float[] predFloats = model_info._backend.predict(model_info._model, f);
    if (_output.nclasses()>=2) {
      for (int i = 1; i < _output.nclasses()+1; ++i) preds[i] = predFloats[i];
    } else {
      preds[0] = predFloats[0];
    }
    return preds;
  }

  @Override public double[] score0(double[] data, double[] preds, double weight, double offset) {
    assert(weight==1);
    assert(offset==0);
    return score0(data, preds);
  }

  @Override protected long checksum_impl() {
    return super.checksum_impl() * _output._run_time + model_info().hashCode();
  }

  @Override
  public Frame scoreAutoEncoder(Frame frame, Key destination_key, boolean reconstruction_error_per_feature) {
    throw H2O.unimpl();
  }

  @Override
  public Frame scoreDeepFeatures(Frame frame, int layer) {
    throw H2O.unimpl();
  }

  @Override
  public Frame scoreDeepFeatures(Frame frame, int layer, Job j) {
    throw H2O.unimpl();
  }

  @Override
  public Frame scoreDeepFeatures(Frame frame, String layer, Job job) {
    if (layer == null)
      throw new H2OIllegalArgumentException("must give hidden layer (symbol) name to extract - cannot be null");
    if (isSupervised()) {
      int ridx = frame.find(_output.responseName());
      if (ridx != -1) { // drop the response for scoring!
        frame = new Frame(frame);
        frame.remove(ridx);
      }
    }
    Frame adaptFrm = new Frame(frame);
    Scope.enter();
    adaptTestForTrain(adaptFrm, true, false);
    Frame _fr = adaptFrm;

    DataInfo di = model_info()._dataInfo;
    if (di != null) {
      di = IcedUtils.deepCopy(di);
      di._adaptedFrame = _fr; //dinfo logic on _adaptedFrame is what we'll need for extracting standardized features from the data for scoring
    }
    final int dataIdx = 0; //FIXME
    final int weightIdx =_fr.find(get_params()._weights_column);
    final int batch_size = get_params()._mini_batch_size;

    ArrayList score_data = new ArrayList(); //for binary data (path to data)
    ArrayList<Integer> skipped = new ArrayList();

    // randomly add more rows to fill up to a multiple of batch_size
    long seed = 0xDECAF + 0xD00D * model_info().get_processed_global();
    Random rng = RandomUtils.getRNG(seed);

    //make predictions for all rows - even those with weights 0 for now (easier to deal with minibatch)
    BufferedString bs = new BufferedString();
    if ((int)_fr.numRows() != _fr.numRows()) {
      throw new IllegalArgumentException("Cannot handle datasets with more than 2 billion rows.");
    }
    for (int i=0; i<_fr.numRows(); ++i) {
      double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
      if (weight == 0) { //don't send observations with weight 0 to the GPU
        skipped.add(i);
        continue;
      }
      if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.image
          || model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
        BufferedString file = _fr.vec(dataIdx).atStr(bs, i);
        if (file!=null)
          score_data.add(file.toString());
      } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
        score_data.add(i);
      } else throw H2O.unimpl();
    }

    while (score_data.size() % batch_size != 0) {
      int pick = rng.nextInt(score_data.size());
      score_data.add(score_data.get(pick));
    }

    assert(isSupervised()); //not yet implemented for autoencoder
    final boolean makeNative = model_info()._backend ==null;
    if (makeNative) model_info().javaToNative();

    Frame _predFrame = null;
    DeepWaterIterator iter;
    try {
      // first, figure out hidden layer dimensionality - do this the hard way
      int cols;
      {
        if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.image) {
          int width = model_info()._width;
          int height = model_info()._height;
          int channels = model_info()._channels;
          iter = new DeepWaterImageIterator(score_data, null /*no labels*/, model_info()._meanData, batch_size, width, height, channels, model_info().get_params()._cache_data);
        } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
          iter = new DeepWaterDatasetIterator(score_data, null /*no labels*/, di, batch_size, model_info().get_params()._cache_data);
        } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
          iter = new DeepWaterTextIterator(score_data, null /*no labels*/, batch_size, 56 /*FIXME*/, model_info().get_params()._cache_data);
        } else {
          throw H2O.unimpl();
        }
        float[] data = iter.getData();
        float[] predFloats = model_info().extractLayer(layer, data); //just to see how big this gets
        if (predFloats.length == 0) {
          throw new IllegalArgumentException(model_info().listAllLayers());
        }
        cols = predFloats.length;
        assert (cols % batch_size == 0);
        cols /= batch_size;
      }

      // allocate the predictions Vec/Frame
      Vec[] predVecs = new Vec[cols];
      for (int i = 0; i < cols; ++i)
        predVecs[i] = _fr.anyVec().makeZero();
      _predFrame = new Frame(predVecs);
      String[] names = new String[cols];
      for (int j=0; j<cols; ++j) {
        names[j]= "DF."+layer+".C" + (j+1);
      }
      _predFrame.setNames(names);

      Vec.Writer[] vw = new Vec.Writer[cols];
      // prep predictions vec for writing
      for (int i = 0; i < vw.length; ++i)
        vw[i] = _predFrame.vec(i).open();

      // re-create the iterators
      long row=0;
      int skippedIdx=0;
      int skippedRow=skipped.isEmpty()?-1:skipped.get(skippedIdx);
      if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.image) {
        int width = model_info()._width;
        int height = model_info()._height;
        int channels = model_info()._channels;
        iter = new DeepWaterImageIterator(score_data, null /*no labels*/, model_info()._meanData, batch_size, width, height, channels, model_info().get_params()._cache_data);
      } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
        iter = new DeepWaterDatasetIterator(score_data, null /*no labels*/, di, batch_size, model_info().get_params()._cache_data);
      } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
        iter = new DeepWaterTextIterator(score_data, null /*no labels*/, batch_size, 56 /*FIXME*/, model_info().get_params()._cache_data);
      } else {
        throw H2O.unimpl();
      }

      // extract actual hidden layer data
      Futures fs=new Futures();
      while(iter.Next(fs)) {
        float[] data = iter.getData();
        float[] predFloats = model_info().extractLayer(layer, data);
//        System.err.println("preds: " + Arrays.toString(predFloats));

        // fill the pre-created output Frame
        for (int j = 0; j < batch_size; ++j) {
          while (row==skippedRow) {
            assert(weightIdx == -1 ||_fr.vec(weightIdx).at(row)==0);
            if (skipped.size()>skippedIdx+1) {
              skippedRow = skipped.get(++skippedIdx);
            }
            row++;
          }
          if (row >= _fr.numRows()) break;
          for (int i = 0; i < cols; ++i)
            vw[i].set(row, predFloats[j*cols + i]);
          row++;
        }
        for (Vec.Writer aVw : vw) aVw.close(fs);
        fs.blockForPending();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (makeNative) model_info().nukeBackend();
      return _predFrame;
    }
  }

  class DeepWaterBigScore extends BigScore {
    Frame _predFrame; //OUTPUT
    @Override public Frame outputFrame(Key<Frame> key, String [] names, String [][] domains){
      _predFrame = new Frame(key, names, _predFrame.vecs());
      if (domains!=null)
        _predFrame.vec(0).setDomain(domains[0]); //only the label is ever categorical
      if (_predFrame._key!=null)
        DKV.put(_predFrame);
      return _predFrame;
    }
    @Override public void map(Chunk[] chks, NewChunk[] cpreds) { }
    @Override public void reduce( BigScore bs ) { }
    @Override protected void setupLocal() {
      if (model_info._unstable) {
        Log.err("Cannot score with an _unstable model.");
        Log.err(unstable_msg);
        throw new UnsupportedOperationException(unstable_msg);
      }

      DataInfo di = model_info()._dataInfo;
      if (di != null) {
        di = IcedUtils.deepCopy(di);
        di._adaptedFrame = _fr; //dinfo logic on _adaptedFrame is what we'll need for extracting standardized features from the data for scoring
      }
      final int dataIdx = 0; //FIXME
      final int weightIdx =_fr.find(get_params()._weights_column);
      final int respIdx =_fr.find(get_params()._response_column);
      final int batch_size = get_params()._mini_batch_size;
      final int classes = _output.nclasses();

      ArrayList score_data = new ArrayList(); //for binary data (path to data)
      ArrayList<Integer> skipped = new ArrayList();

      // randomly add more rows to fill up to a multiple of batch_size
      long seed = 0xDECAF + 0xD00D * model_info().get_processed_global();
      Random rng = RandomUtils.getRNG(seed);

      //make predictions for all rows - even those with weights 0 for now (easier to deal with minibatch)
      BufferedString bs = new BufferedString();
      if ((int)_fr.numRows() != _fr.numRows()) {
        throw new IllegalArgumentException("Cannot handle datasets with more than 2 billion rows.");
      }
      for (int i=0; i<_fr.numRows(); ++i) {
        if (isCancelled() || _j != null && _j.stop_requested()) return;
        double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
        if (weight == 0) { //don't send observations with weight 0 to the GPU
          skipped.add(i);
          continue;
        }
        if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.image
            || model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
          BufferedString file = _fr.vec(dataIdx).atStr(bs, i);
          if (file!=null)
            score_data.add(file.toString());
        } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
          score_data.add(i);
        } else throw H2O.unimpl();
      }

      while (score_data.size() % batch_size != 0) {
        int pick = rng.nextInt(score_data.size());
        score_data.add(score_data.get(pick));
      }

      _mb = makeMetricBuilder(_domain);
      assert(isSupervised()); //not yet implemented for autoencoder
      int cols = _output.nclasses() + (_output.isClassifier()?1:0);
      if (_makePreds) {
        Vec[] predVecs = new Vec[cols];
        for (int i = 0; i < cols; ++i)
          predVecs[i] = _fr.anyVec().makeZero();
        _predFrame = new Frame(predVecs);
      }

      DeepWaterIterator iter;
      try {
        Vec.Writer[] vw = new Vec.Writer[cols];
        if (_makePreds) {
          // prep predictions vec for writing
          for (int i = 0; i < vw.length; ++i)
            vw[i] = _predFrame.vec(i).open();
        }

        long row=0;
        int skippedIdx=0;
        int skippedRow=skipped.isEmpty()?-1:skipped.get(skippedIdx);
        double mul = 1;
        double sub = 0;
        if (_output._normRespMul!=null && _output._normRespSub!=null) {
          mul = _output._normRespMul[0];
          sub = _output._normRespSub[0];
        }
        if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.image) {
          int width = model_info()._width;
          int height = model_info()._height;
          int channels = model_info()._channels;
          iter = new DeepWaterImageIterator(score_data, null /*no labels*/, model_info()._meanData, batch_size, width, height, channels, model_info().get_params()._cache_data);
        } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.dataset) {
          iter = new DeepWaterDatasetIterator(score_data, null /*no labels*/, di, batch_size, model_info().get_params()._cache_data);
        } else if (model_info().get_params()._problem_type == DeepWaterParameters.ProblemType.text) {
          iter = new DeepWaterTextIterator(score_data, null /*no labels*/, batch_size, 56 /*FIXME*/, model_info().get_params()._cache_data);
        } else {
          throw H2O.unimpl();
        }
        Futures fs=new Futures();
        while(iter.Next(fs)) {
          if (isCancelled() || _j != null && _j.stop_requested()) return;
          float[] data = iter.getData();
          float[] predFloats = model_info().predict(data);
//          System.err.println("preds: " + Arrays.toString(predFloats));
//          Log.info("Scoring on " + batch_size + " samples (rows " + row + " and up): " + Arrays.toString(((DeepWaterImageIterator)iter).getFiles()));

            // fill the pre-created output Frame
          boolean unstable = false;
          for (int j = 0; j < batch_size; ++j) {
            while (row==skippedRow) {
              assert(weightIdx == -1 ||_fr.vec(weightIdx).at(row)==0);
              if (skipped.size()>skippedIdx+1) {
                skippedRow = skipped.get(++skippedIdx);
              }
              row++;
            }
            if (row >= _fr.numRows()) break;
            float [] actual = null;
            if (_computeMetrics)
              actual = new float[]{(float)_fr.vec(respIdx).at(row)};
            if(_output.isClassifier()) {
              double[] preds =new double[classes+1];
              for (int i=0;i<classes;++i) {
                int idx=j*classes+i; //[p0,...,p9,p0,...,p9, ... ,p0,...,p9]
                preds[1+i] = predFloats[idx];
                if (Double.isNaN(preds[1+i]))
                  unstable = true;
              }
              if (_parms._balance_classes)
                GenModel.correctProbabilities(preds, _output._priorClassDist, _output._modelClassDist);
              preds[0] = hex.genmodel.GenModel.getPrediction(preds, _output._priorClassDist, null, defaultThreshold());
              if (_makePreds) {
                //Log.info(iter.getFiles()[j] + " -> preds: " + Arrays.toString(preds));
                for (int i = 0; i <= classes; ++i)
                  vw[i].set(row, preds[i]);
              }
              if (_computeMetrics)
                _mb.perRow(preds, actual, DeepWaterModel.this);
            }
            else {
              double pred = predFloats[j] * mul + sub;
              if (Double.isNaN(pred))
                unstable = true;
              if (_makePreds)
                vw[0].set(row, pred);
              if (_computeMetrics)
                _mb.perRow(new double[]{pred}, actual, DeepWaterModel.this);
            }
            row++;
          }
          if (_makePreds) {
            for (Vec.Writer aVw : vw) aVw.close(fs);
            fs.blockForPending();
          }
          if (unstable) {
            model_info._unstable = true;
            Log.err(unstable_msg);
            throw new UnsupportedOperationException(unstable_msg);
          }
        }
        if ( _j != null) _j.update(_fr.anyVec().nChunks());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    DeepWaterBigScore(String[] domain, int ncols, double[] mean, boolean testHasWeights, boolean computeMetrics, boolean makePreds, Job j) {
      super(domain, ncols, mean, testHasWeights, computeMetrics, makePreds, j);
    }
  }

  @Override
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics) {
    final boolean makeNative = model_info()._backend ==null;
    if (makeNative) model_info().javaToNative();
    // Build up the names & domains.
    String[] names = makeScoringNames();
    String[][] domains = new String[names.length][];
    domains[0] = names.length == 1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();

    //DEBUGGING ONLY
    /*
    DataInfo _dinfo = model_info._dataInfoKey.get();
    for (int r=0; r<_dinfo._adaptedFrame.numRows(); ++r) {

      // Version 1 - via DataInfo
      DataInfo.Row row = _dinfo.newDenseRow();
      Chunk[] chks = new Chunk[_dinfo._adaptedFrame.numCols()];
      for (int i = 0; i < chks.length; ++i)
        chks[i] = _dinfo._adaptedFrame.vec(i).chunkForRow(r);
      for (int i = 0; i < chks.length; ++i)
        assert (chks[i]._len == chks[0]._len);
      _dinfo.extractDenseRow(chks, r - (int)chks[0].start(), row);

      // Version 2 - via GenModel
      double[] from = new double[chks.length];
      for (int i = 0; i < chks.length; ++i)
        from[i] = chks[i].atd(r - (int)chks[0].start());
      float[] _destData = new float[_output._nums + _output._catOffsets[_output._cats]];
      GenModel.setInput(from, _destData, _output._nums, _output._cats, _output._catOffsets,
          _output._normMul, _output._normSub, _output._useAllFactorLevels);

      // Compare the two
      for (int i = 0; i < _dinfo.fullN(); ++i)
        assert Math.abs(_destData[i] - row.get(i)) <= 1e-6 * Math.abs(_destData[i] + row.get(i)) : " feature " + i + " is "  + _destData[i] + " vs " + row.get(i);
    }
    */
    // Score the dataset, building the class distribution & predictions
    BigScore bs = new DeepWaterBigScore(domains[0],names.length,adaptFrm.means(),_output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,computeMetrics, true /*make preds*/, j).doAll(adaptFrm);
    if (computeMetrics) bs._mb.makeModelMetrics(this, fr, adaptFrm, bs.outputFrame());
    if (makeNative) removeNativeState();
    return bs.outputFrame(null == destination_key ? Key.<Frame>make() : Key.<Frame>make(destination_key), names, domains);
  }

  @Override
  protected ModelMetrics.MetricBuilder scoreMetrics(Frame adaptFrm) {
    final boolean makeNative = model_info()._backend ==null;
    if (makeNative) model_info().javaToNative();
    final boolean computeMetrics = (!isSupervised() || (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad()));
    // Build up the names & domains.
    String [] domain = !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();
    // Score the dataset, building the class distribution & predictions
    BigScore bs = new DeepWaterBigScore(domain,0,adaptFrm.means(),_output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,computeMetrics, false /*no preds*/, null).doAll(adaptFrm);
    if (makeNative) removeNativeState();
    return bs._mb;
  }

  void removeNativeState() {
    model_info().nukeBackend();
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    cleanUpCache(fs);
    removeNativeState();
    if (actual_best_model_key!=null)
      DKV.remove(actual_best_model_key);
    if (model_info()._dataInfo !=null)
      model_info()._dataInfo.remove(fs);
    return super.remove_impl(fs);
  }

  void exportNativeModel(String path, int iteration) {
    model_info().saveNativeState(path, iteration);
  }

  static String CACHE_MARKER = "__d33pW473r_1n73rn4l__";

  void cleanUpCache() {
    cleanUpCache(null);
  }
  private void cleanUpCache(Futures fs) {
    final Key[] cacheKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, DeepWaterImageIterator.IcedImage.class) && k._key.toString().contains(CACHE_MARKER)
        || Value.isSubclassOf(k._type, DeepWaterDatasetIterator.IcedRow.class) && k._key.toString().contains(CACHE_MARKER);
      }
    }).keys();
    if (fs==null) fs = new Futures();
    for (Key k : cacheKeys) DKV.remove(k, fs);
    fs.blockForPending();
  }


  private static String getNvidiaStats() throws java.io.IOException {
    String cmd = "nvidia-smi";
    InputStream stdin = Runtime.getRuntime().exec(cmd).getInputStream();
    InputStreamReader isr = new InputStreamReader(stdin);
    BufferedReader br = new BufferedReader(isr);
    StringBuilder sb = new StringBuilder();
    String s;
    while ((s = br.readLine()) != null) {
      sb.append(s).append("\n");
    }
    return sb.toString();
  }

  static private String logNvidiaStats() { try { return (getNvidiaStats()); } catch (IOException e) { return null; } }
}

