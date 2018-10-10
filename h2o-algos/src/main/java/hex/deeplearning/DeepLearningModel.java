package hex.deeplearning;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.*;

import java.lang.reflect.Field;
import java.util.Arrays;

import static hex.ModelMetrics.calcVarImp;
import static hex.deeplearning.DeepLearning.makeDataInfo;
import static hex.deeplearning.DeepLearningModel.DeepLearningParameters.Loss.*;
import static water.H2O.technote;

/**
 * The Deep Learning model
 * It contains a DeepLearningModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */

public class DeepLearningModel extends Model<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningModelOutput> implements Model.DeepFeatures {
  @Override public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

  /**
   * The Deep Learning model output contains a few extra fields in addition to the metrics in Model.Output
   * 1) Scoring history (raw data)
   * 2) weights/biases (raw data)
   * 3) variable importances (TwoDimTable)
   */
  public static class DeepLearningModelOutput extends Model.Output {
    public DeepLearningModelOutput(DeepLearning b) {
      super(b);
      autoencoder = b._parms._autoencoder;
      assert b.isSupervised() == !autoencoder;
    }
    final boolean autoencoder;

    @Override
    public boolean isAutoencoder() { return autoencoder; }

    DeepLearningScoringInfo errors;
    Key[] weights;
    Key[] biases;
    double[] normmul;
    double[] normsub;
    double[] normrespmul;
    double[] normrespsub;
    int[] catoffsets;
    public TwoDimTable _variable_importances;

    @Override public ModelCategory getModelCategory() {
      return autoencoder ? ModelCategory.AutoEncoder : super.getModelCategory();
    }

    @Override public boolean isSupervised() {
      return !autoencoder;
    }
  } // DeepLearningModelOutput

  void set_model_info(DeepLearningModelInfo mi) {
    assert(mi != null);
    model_info = mi;
  }

  final public DeepLearningModelInfo model_info() { return model_info; }
  final public VarImp varImp() { return _output.errors.variable_importances; }

  private volatile DeepLearningModelInfo model_info;

  // timing
  public long total_checkpointed_run_time_ms; //time spent in previous models
  public long total_training_time_ms; //total time spent running (training+scoring, including all previous models)
  public long total_scoring_time_ms; //total time spent scoring (including all previous models)
  public long total_setup_time_ms; //total time spent setting up (including all previous models)
  private long time_of_start_ms; //start time for this model (this cp restart)

  // auto-tuning
  public long actual_train_samples_per_iteration;
  public double time_for_communication_us; //helper for auto-tuning: time in microseconds for collective bcast/reduce of the model

  // helpers for diagnostics
  public double epoch_counter;
  public int iterations;
  public boolean stopped_early;
  public long training_rows;
  public long validation_rows;

  // Keep the best model so far, based on a single criterion (overall class. error or MSE)
  private float _bestLoss = Float.POSITIVE_INFINITY;

  public Key actual_best_model_key;
  public Key model_info_key;

  public DeepLearningScoringInfo last_scored() { return (DeepLearningScoringInfo) super.last_scored(); }


  /**
   * Get the parameters actually used for model building, not the user-given ones (_parms)
   * They might differ since some defaults are filled in, and some invalid combinations are auto-disabled in modifyParams
   * @return actually used parameters
   */
  public final DeepLearningParameters get_params() { return model_info.get_params(); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      case AutoEncoder: return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
      default: throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
    }
  }

  /**
   * Helper to allocate keys for output frames for weights and biases
   * @param destKey Base destination key for output frames
   */
  private void makeWeightsBiases(Key destKey) {
    if (!model_info.get_params()._export_weights_and_biases) {
      _output.weights = null;
      _output.biases = null;
      _output.normmul = null;
      _output.normsub = null;
      _output.normrespmul = null;
      _output.normrespsub = null;
      _output.catoffsets = null;
    } else {
      _output.weights = new Key[get_params()._hidden.length + 1];
      for (int i = 0; i < _output.weights.length; ++i) {
        _output.weights[i] = Key.make(destKey + ".weights." + i);
      }
      _output.biases = new Key[get_params()._hidden.length + 1];
      for (int i = 0; i < _output.biases.length; ++i) {
        _output.biases[i] = Key.make(destKey + ".biases." + i);
      }
      _output.normmul = model_info.data_info._normMul;
      _output.normsub = model_info.data_info._normSub;
      _output.normrespmul = model_info.data_info._normRespMul;
      _output.normrespsub = model_info.data_info._normRespSub;
      _output.catoffsets = model_info.data_info._catOffsets;
    }
  }

  /** Constructor to restart from a checkpointed model
   * @param destKey New destination key for the model
   *  @param parms User-given parameters for checkpoint restart
   *  @param cp Checkpoint to restart from
   * @param store_best_model Store only the best model instead of the latest one  */
  public DeepLearningModel(final Key destKey, final DeepLearningParameters parms, final DeepLearningModel cp, final boolean store_best_model, final DataInfo dataInfo) {
    super(destKey, parms == null ? (DeepLearningParameters)cp._parms.clone() : parms, (DeepLearningModelOutput)cp._output.clone());
    assert(_parms != cp._parms); //make sure we have a clone
    model_info = IcedUtils.deepCopy(cp.model_info); //don't want to interfere with model being built, just make a deep copy and store that
    if (store_best_model) {
      model_info.data_info = IcedUtils.deepCopy(dataInfo); //replace previous data_info with updated version that's passed in (contains enum for classification)
    } else {
      model_info.data_info = dataInfo; //shallow clone is ok
      if (parms != null) {
        assert (_parms == parms);
        assert (_parms._checkpoint == parms._checkpoint);
        assert (_parms._checkpoint == cp._key);
      }
    }
    assert(get_params() != cp.model_info().get_params()); //make sure we have a clone
    _dist = new Distribution(get_params());
    assert(_dist.distribution != DistributionFamily.AUTO); // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    actual_best_model_key = cp.actual_best_model_key;
    if (actual_best_model_key.get() == null) {
      DeepLearningModel best = IcedUtils.deepCopy(cp);
      //best.model_info.data_info = model_info.data_info; // Note: we currently DO NOT use the checkpoint's data info - as data may change during checkpoint restarts
      actual_best_model_key = Key.<DeepLearningModel>make(H2O.SELF);
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
    makeWeightsBiases(destKey);
    _output._scoring_history = DeepLearningScoringInfo.createScoringHistoryTable(scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
    _output._variable_importances = calcVarImp(last_scored().variable_importances);
    _output.setNames(dataInfo._adaptedFrame.names());
    _output._domains = dataInfo._adaptedFrame.domains();
    assert(Arrays.equals(_key._kb, destKey._kb));
  }

  /**
   * Regular constructor (from scratch)
   * @param destKey destination key
   * @param parms DL parameters
   * @param output DL model output
   * @param train Training frame
   * @param valid Validation frame
   * @param nClasses Number of classes (1 for regression or autoencoder)
   */
  public DeepLearningModel(final Key destKey, final DeepLearningParameters parms, final DeepLearningModelOutput output, Frame train, Frame valid, int nClasses) {
    super(destKey, parms, output);
    final DataInfo dinfo = makeDataInfo(train, valid, _parms, nClasses);
    DKV.put(dinfo);
    _output.setNames(dinfo._adaptedFrame.names());
    _output._domains = dinfo._adaptedFrame.domains();
    _output._origNames = parms._train.get().names();
    _output._origDomains = parms._train.get().domains();
    Log.info("Building the model on " + dinfo.numNums() + " numeric features and " + dinfo.numCats() + " (one-hot encoded) categorical features.");
    model_info = new DeepLearningModelInfo(parms, destKey, dinfo, nClasses, train, valid);
    model_info_key = Key.make(H2O.SELF);
    _dist = new Distribution(get_params());
    assert(_dist.distribution != DistributionFamily.AUTO); // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    actual_best_model_key = Key.make(H2O.SELF);
    if (parms._nfolds != 0) actual_best_model_key = null;
    if (!parms._autoencoder) {
      scoringInfo = new DeepLearningScoringInfo[1];
      scoringInfo[0] = new DeepLearningScoringInfo();
      scoringInfo[0].validation = (parms._valid != null);
      scoringInfo[0].time_stamp_ms = System.currentTimeMillis();
      _output.errors = last_scored();
      _output._scoring_history = DeepLearningScoringInfo.createScoringHistoryTable(scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
    }
    time_of_start_ms = System.currentTimeMillis();
    makeWeightsBiases(destKey);
    assert _key.equals(destKey);
    boolean fail = false;
    long byte_size = 0;
    try {
      byte_size = new AutoBuffer().put(this).buf().length;
    } catch(Throwable t) {
      fail = true;
    }
    if (byte_size > Value.MAX || fail)
      throw new IllegalArgumentException(technote(5, "Model is too large"));
  }

  public long _timeLastIterationEnter;
  public long _timeLastScoreStart; //start actual scoring
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

  void updateTiming(Key<Job> job_key) {
    final long now = System.currentTimeMillis();
    long start_time_current_model = job_key.get().start_time();
    total_training_time_ms = total_checkpointed_run_time_ms + (now - start_time_current_model);
    checkTimingConsistency();
  }

  /**
   * Score this DeepLearning model
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
    // if multi-node and auto-tuning and at least 10 ms for communication (to avoid doing thins on multi-JVM on same node),
    // then adjust the auto-tuning parameter 'actual_train_samples_per_iteration' such that the targeted ratio of comm to comp is achieved
    // Note: actual communication time is estimated by the NetworkTest's collective test.
    if (H2O.CLOUD.size() > 1 && get_params()._train_samples_per_iteration == -2 && iteration > 1) {
      Log.debug("Auto-tuning train_samples_per_iteration.");
      if (time_for_communication_us > 1e4) {
        Log.debug("  Time taken for communication: " + PrettyPrint.usecs((long) time_for_communication_us));
        Log.debug("  Time taken for Map/Reduce iteration: " + PrettyPrint.msecs((long) time_since_last_iter, true));
        final double comm_to_work_ratio = (time_for_communication_us * 1e-3) / time_since_last_iter;
        Log.debug("  Ratio of network communication to computation: " + String.format("%.5f", comm_to_work_ratio));
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
        Log.debug("Communication is faster than 10 ms. Not modifying train_samples_per_iteration: " + actual_train_samples_per_iteration);
      }
    }

    keep_running = (epoch_counter < get_params()._epochs) && !stopped_early;
    final long sinceLastScore = now -_timeLastScoreStart;

    // this is potentially slow - only do every so often
    if( !keep_running || get_params()._score_each_iteration ||
        (sinceLastScore > get_params()._score_interval *1000 //don't score too often
            &&(double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < get_params()._score_duty_cycle) ) { //duty cycle
      jobKey.get().update(0,"Scoring on " + fTrain.numRows() + " training samples" +(fValid != null ? (", " + fValid.numRows() + " validation samples") : ""));
      final boolean printme = !get_params()._quiet_mode;
      _timeLastScoreStart = System.currentTimeMillis();
      model_info().computeStats(); //might not be necessary, but is done to be certain that numbers are good
      DeepLearningScoringInfo scoringInfo = new DeepLearningScoringInfo();
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
      scoringInfo.is_classification = _output.isClassifier();
      scoringInfo.is_autoencoder = _output.isAutoencoder();

      if (get_params()._autoencoder) {
        if (printme) Log.info("Scoring the auto-encoder.");
        // training
        {
          final Frame mse_frame = scoreAutoEncoder(fTrain, Key.make(), false);
          mse_frame.delete();
          ModelMetrics mtrain = ModelMetrics.getFromDKV(this, fTrain); //updated by model.score
          _output._training_metrics = mtrain;
          scoringInfo.scored_train = new ScoreKeeper(mtrain);
        }
        if (fValid != null) {
          final Frame mse_frame = scoreAutoEncoder(fValid, Key.make(), false);
          mse_frame.delete();
          ModelMetrics mtest = ModelMetrics.getFromDKV(this, fValid); //updated by model.score
          _output._validation_metrics = mtest;
          scoringInfo.scored_valid = new ScoreKeeper(mtest);
        }
      } else {
        if (printme) Log.info("Scoring the model.");
        // compute errors
        final String m = model_info().toString();
        if (m.length() > 0) Log.info(m);

        // For GainsLift and Huber, we need the full predictions to compute the model metrics
        boolean needPreds = _output.nclasses() == 2 /* gains/lift table requires predictions */ ||
                            get_params()._distribution == DistributionFamily.huber;

        // Scoring on training data
        hex.ModelMetrics mtrain;
        Frame preds = null;
        if (needPreds) {
          // allocate predictions since they are needed
          preds = score(fTrain);
          mtrain = ModelMetrics.getFromDKV(this, fTrain);
          if (get_params()._distribution == DistributionFamily.huber) {
            Vec absdiff = new MathUtils.ComputeAbsDiff().doAll(1, (byte)3,
                new Frame(new String[]{"a","p"}, new Vec[]{fTrain.vec(get_params()._response_column), preds.anyVec()})
            ).outputFrame().anyVec();
            double huberDelta = MathUtils.computeWeightedQuantile(fTrain.vec(get_params()._weights_column), absdiff, get_params()._huber_alpha);
            if (model_info().gradientCheck == null) _dist.setHuberDelta(huberDelta);
          }
        } else {
          // no need to allocate predictions
          ModelMetrics.MetricBuilder mb = scoreMetrics(fTrain);
          mtrain = mb.makeModelMetrics(this,fTrain,fTrain,null);
        }
        if (preds!=null) preds.remove();
        _output._training_metrics = mtrain;
        scoringInfo.scored_train = new ScoreKeeper(mtrain);
        hex.ModelMetricsSupervised mm1 = (ModelMetricsSupervised)mtrain;
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
        hex.ModelMetrics mvalid;
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
              if (get_params()._score_validation_sampling == DeepLearningParameters.ClassSamplingMethod.Stratified) {
                _output._validation_metrics._description += " (stratified sampling)";
              }
            } else if (fValid._key != null && fValid._key.toString().contains("chunks")){
              _output._validation_metrics._description = "Metrics reported on temporary (load-balanced) validation frame";
            } else {
              _output._validation_metrics._description = "Metrics reported on full validation frame";
            }
          }
        }
      }
      if (get_params()._variable_importances) {
        if (!get_params()._quiet_mode) Log.info("Computing variable importances.");
        final float[] vi = model_info().computeVariableImportances();
        scoringInfo.variable_importances = new VarImp(vi, Arrays.copyOfRange(model_info().data_info().coefNames(), 0, vi.length));
      }

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
        this.scoringInfo = new DeepLearningScoringInfo[]{scoringInfo};
      } else {
        DeepLearningScoringInfo[] err2 = new DeepLearningScoringInfo[this.scoringInfo.length + 1];
        System.arraycopy(this.scoringInfo, 0, err2, 0, this.scoringInfo.length);
        err2[err2.length - 1] = scoringInfo;
        this.scoringInfo = err2;
      }
      _output.errors = last_scored();
      makeWeightsBiases(_key);
      water.util.Timer t = new Timer();
      // store weights and matrices to Frames
      if (_output.weights != null && _output.biases != null) {
        for (int i = 0; i < _output.weights.length; ++i) {
          Frame f = model_info.get_weights(i).toFrame(_output.weights[i]);
          if (i==0) {
            f.setNames(model_info.data_info.coefNames());
            DKV.put(f);
          }
        }
        for (int i = 0; i < _output.biases.length; ++i) {
          model_info.get_biases(i).toFrame(_output.biases[i]);
        }
        if (!get_params()._quiet_mode)
          Log.info("Writing weights and biases to Frames took " + t.time()/1000. + " seconds.");
      }
      _output._scoring_history = DeepLearningScoringInfo.createScoringHistoryTable(this.scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
      _output._model_summary = model_info.createSummaryTable();

      // always keep a copy of the best model so far (based on the following criterion)
      if (!finalScoring) {
        if (actual_best_model_key != null && get_params()._overwrite_with_best_model && (
                // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                (DKV.get(actual_best_model_key) != null && (!(loss() >= DKV.get(actual_best_model_key).<DeepLearningModel>get().loss()) || !Arrays.equals(model_info().units, DKV.get(actual_best_model_key).<DeepLearningModel>get().model_info().units)))
                        ||
                        // otherwise, compare against our own _bestError
                        (DKV.get(actual_best_model_key) == null && loss() < _bestLoss)
        ) ) {
          _bestLoss = loss();
          putMeAsBestModel(actual_best_model_key);
        }
        // print the freshly scored model to ASCII
        if (keep_running && printme)
          Log.info(toString());
        if ((_output.isClassifier() && last_scored().scored_train._classError <= get_params()._classification_stop)
                || (!_output.isClassifier() && last_scored().scored_train._mse <= get_params()._regression_stop)) {
          Log.info("Achieved requested predictive accuracy on the training data. Model building completed.");
          stopped_early = true;
        }
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
    update(jobKey);
    return keep_running;
  }

  private void progressUpdate(Key<Job> job_key, boolean keep_running) {
    updateTiming(job_key);
    Job job = job_key.get();
    double progress = job.progress();
//    Log.info("2nd speed: (samples: " + model_info().get_processed_total() + ", total_run_time: " + total_training_time_ms + ", total_scoring_time: " + total_scoring_time_ms + ", total_setup_time: " + total_setup_time_ms + ")");
    int speed = (int)(model_info().get_processed_total() * 1000. / (total_training_time_ms -total_scoring_time_ms-total_setup_time_ms));
    assert(speed >= 0) : "negative speed computed! (total_run_time: " + total_training_time_ms + ", total_scoring_time: " + total_scoring_time_ms + ", total_setup_time: " + total_setup_time_ms + ")";
    String msg =
            "Iterations: " + String.format("%,d", iterations)
            + ". Epochs: " + String.format("%g", epoch_counter)
            + ". Speed: " + String.format("%,d", speed) + " samples/sec."
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

  /** Make either a prediction or a reconstruction.
   * @param orig Test dataset
   * @param adaptedFr Test dataset, adapted to the model
   * @param computeMetrics
   * @return A frame containing the prediction or reconstruction
   */
  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    if (!get_params()._autoencoder) {
      return super.predictScoreImpl(orig, adaptedFr, destination_key, j, computeMetrics, customMetricFunc);
    } else {
      // Reconstruction
      final int len = model_info().data_info().fullN();
      assert(model_info().data_info()._responses == 0);
      String[] coefnames = model_info().data_info().coefNames();
      assert(len == coefnames.length);
      String[] names = new String[len];
      for(int i = 0; i < names.length; ++i) {
        names[i] = "reconstr_" + coefnames[i];
      }
      Frame f = new MRTask() {
        @Override public void map( Chunk chks[], NewChunk recon[] ) {
          double tmp [] = new double[_output._names.length];
          double preds[] = new double [len];
          final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
          for( int row=0; row<chks[0]._len; row++ ) {
            double p[] = score_autoencoder(chks, row, tmp, preds, neurons, true /*reconstruction*/, false /*reconstruction_error_per_feature*/);
            for( int c=0; c<len; c++ )
              recon[c].addNum(p[c]);
          }
        }
      }.doAll(len, Vec.T_NUM, adaptedFr).outputFrame();

      Frame of = new Frame(Key.<Frame>make(destination_key), names, f.vecs());
      DKV.put(of);
      makeMetricBuilder(null).makeModelMetrics(this, orig, null, null);
      return of;
    }
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 0);
  }

  /**
   * Compute the loss function
   * @param myRows Mini-Batch Array of denseRow's containing numerical/categorical predictor and response data (standardized)
   * @return loss
   */
  public double meanLoss(DataInfo.Row[] myRows) {
    double loss = 0;
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(model_info());
    //for absolute error, gradient -1/1 matches the derivative of abs(x) without correction term
    long seed = -1; //ignored

    double[] responses = new double[myRows.length];
    double[] offsets   = new double[myRows.length];
    int n=0;
    for (int mb=0; mb<myRows.length; ++mb) {
      DataInfo.Row myRow = myRows[mb];
      if (myRow == null) continue;
      n++;
      ((Neurons.Input) neurons[0]).setInput(seed, myRow.numIds, myRow.numVals, myRow.nBins, myRow.binIds, mb);
      responses[mb] = myRow.response(0);
      offsets[mb] = myRow.offset;

      // check that all non-last layer errors/gradients are empty
      for (int i = 0; i < neurons.length - 1; ++i) {
        Storage.DenseVector e = neurons[i]._e == null ? null : neurons[i]._e[mb];
        if (e == null) continue;
        assert (ArrayUtils.sum(e.raw()) == 0);
      }
    }

    DeepLearningTask.fpropMiniBatch(seed, neurons, model_info(), null, false, responses, offsets, myRows.length);

    for (int mb=0; mb<myRows.length; ++mb) {
      DataInfo.Row myRow = myRows[mb];
      if (myRow==null) continue;

      // check that all non-last layer errors/gradients are still empty
      for (int i = 0; i<neurons.length-1;++i) {
        Storage.DenseVector e = neurons[i]._e == null ? null : neurons[i]._e[mb];
        if (e==null) continue;
        assert (ArrayUtils.sum(e.raw()) == 0);
      }

      if (get_params()._loss == CrossEntropy) {
        if (get_params()._balance_classes) throw H2O.unimpl();
        int actual = (int) myRow.response[0];
        double pred = neurons[neurons.length - 1]._a[mb].get(actual);
        loss += -Math.log(Math.max(1e-15, pred)); //cross-entropy (same as log loss)
      } else {
        if (model_info.get_params()._autoencoder) throw H2O.unimpl();

        //prediction and actual response in standardized response space
        double pred = neurons[neurons.length - 1]._a[mb].get(0);
        double actual = myRow.response[0];

        // FIXME: re-enable this such that the loss is computed from the de-standardized prediction/response
        //bring standardized prediction and actual response to real space
//      DataInfo di = model_info().data_info();
//      if (di._normRespMul != null) { //either both are null or none
//        pred = (pred / di._normRespMul[0] + di._normRespSub[0]);
//        actual = (actual / di._normRespMul[0] + di._normRespSub[0]);
//      }
        pred = _dist.linkInv(pred);
        loss += _dist.deviance(1 /*weight*/, actual, pred);
      }

      // add L1/L2 penalty of model coefficients (weights & biases)
      for (int i = 0; i <= get_params()._hidden.length+1; ++i) {
        if (neurons[i]._w != null) {
          for (int row = 0; row < neurons[i]._w.rows(); ++row) {
            for (int col = 0; col < neurons[i]._w.cols(); ++col) {
              loss += get_params()._l1 * Math.abs(neurons[i]._w.get(row, col));
              loss += 0.5 * get_params()._l2 * Math.pow(neurons[i]._w.get(row, col), 2);
            }
          }
        }
        if (neurons[i]._b != null) {
          for (int row = 0; row < neurons[i]._b.size(); ++row) {
            loss += get_params()._l1 * Math.abs(neurons[i]._b.get(row));
            loss += 0.5 * get_params()._l2 * Math.pow(neurons[i]._b.get(row), 2);
          }
        }
      }
    }
    return n>0?loss/n:loss;
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override
  public double[] score0(double[] data, double[] preds, double offset) {
    int mb=0;
    int n=1;

    if (model_info().isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
    ((Neurons.Input)neurons[0]).setInput(-1, data, mb);
    DeepLearningTask.fpropMiniBatch(-1, neurons, model_info, null, false, null, new double[]{offset}, n);
    double[] out = neurons[neurons.length - 1]._a[mb].raw();

    if (get_params()._distribution == DistributionFamily.modified_huber) {
      preds[0] = -1;
      preds[2] = _dist.linkInv(out[0]);
      preds[1] = 1-preds[2];
      return preds;
    } else if (_output.isClassifier()) {
      assert (preds.length == out.length + 1);
      for (int i = 0; i < preds.length - 1; ++i) {
        preds[i + 1] = out[i];
        if (Double.isNaN(preds[i + 1])) throw new RuntimeException("Predicted class probability NaN!");
      }
      // label assignment happens later - explicitly mark it as invalid here
      preds[0] = -1;
    } else {
      if (model_info().data_info()._normRespMul != null) //either both are null or none
        preds[0] = (out[0] / model_info().data_info()._normRespMul[0] + model_info().data_info()._normRespSub[0]);
      else
        preds[0] = out[0];
      // transform prediction to response space
      preds[0] = _dist.linkInv(preds[0]);
      if (Double.isNaN(preds[0]))
        throw new RuntimeException("Predicted regression target NaN!");
    }
    return preds;
  }


  /**
   * Score auto-encoded reconstruction (on-the-fly, without allocating the reconstruction as done in Frame score(Frame fr))
   * @param frame Original data (can contain response, will be ignored)
   * @param destination_key Frame Id for output
   * @param reconstruction_error_per_feature whether to return the squared error per feature
   * @return Frame containing one Vec with reconstruction error (MSE) of each reconstructed row, caller is responsible for deletion
   */
  public Frame scoreAutoEncoder(Frame frame, Key destination_key, final boolean reconstruction_error_per_feature) {
    if (!get_params()._autoencoder)
      throw new H2OIllegalArgumentException("Only for AutoEncoder Deep Learning model.", "");
    final int len = _output._names.length;
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    final int outputcols = reconstruction_error_per_feature ? model_info.data_info.fullN() : 1;
    Frame mse = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk[] mse ) {
        double tmp [] = new double[len];
        double out[] = new double[outputcols];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<len; i++ )
            tmp[i] = chks[i].atd(row);
          score_autoencoder(tmp, out, neurons, false /*reconstruction*/, reconstruction_error_per_feature);
          for (int i=0; i<outputcols; ++i)
            mse[i].addNum(out[i]);
        }
      }
    }.doAll(outputcols, Vec.T_NUM, adaptFrm).outputFrame();

    String[] names;
    if (reconstruction_error_per_feature) {
      String[] coefnames = model_info().data_info().coefNames();
      assert (outputcols == coefnames.length);
      names = new String[outputcols];
      for (int i = 0; i < names.length; ++i) {
        names[i] = "reconstr_" + coefnames[i] + ".SE";
      }
    } else {
      names = new String[]{"Reconstruction.MSE"};
    }

    Frame res = new Frame(destination_key, names, mse.vecs());
    DKV.put(res);
    addModelMetrics(new ModelMetricsAutoEncoder(this, frame, res.numRows(), res.vecs()[0].mean() /*mean MSE*/, CustomMetric.EMPTY));
    return res;
  }

   /**
   * Score auto-encoded reconstruction (on-the-fly, and materialize the deep features of given layer
   * @param frame Original data (can contain response, will be ignored)
   * @param layer index of the hidden layer for which to extract the features
   * @return Frame containing the deep features (#cols = hidden[layer])
   */

   public Frame scoreDeepFeatures(Frame frame, final int layer) {
     return  scoreDeepFeatures(frame, layer, null);
   }

  public Frame scoreDeepFeatures(Frame frame, final int layer, final Job job) {
    if (layer < 0 || layer >= model_info().get_params()._hidden.length)
      throw new H2OIllegalArgumentException("hidden layer (index) to extract must be between " + 0 + " and " + (model_info().get_params()._hidden.length-1),"");
    final int len = _output.nfeatures();
    if (isSupervised()) {
      int ridx = frame.find(_output.responseName());
      if (ridx != -1) { // drop the response for scoring!
        frame = new Frame(frame);
        frame.remove(ridx);
      }
    }
    Frame adaptFrm = new Frame(frame);
    //create new features, will be dense
    final int features = model_info().get_params()._hidden[layer];
    Vec v = adaptFrm.anyVec();
    Vec[] vecs = v!=null ? v.makeZeros(features) : null;
    if (vecs == null) throw new IllegalArgumentException("Cannot create deep features from a frame with no columns.");

    Scope.enter();
    adaptTestForTrain(adaptFrm, true, false);
    for (int j=0; j<features; ++j) {
      adaptFrm.add("DF.L"+(layer+1)+".C" + (j+1), vecs[j]);
    }
    final int mb=0;
    final int n=1;
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        if (isCancelled() || job !=null && job.stop_requested()) throw new Job.JobCancelledException();
        double tmp [] = new double[len];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<len; i++ )
            tmp[i] = chks[i].atd(row);
          ((Neurons.Input)neurons[0]).setInput(-1, tmp, mb); //FIXME: No weights yet
          DeepLearningTask.fpropMiniBatch(-1, neurons, model_info, null, false, null, null /*no offset*/, n);
          double[] out = neurons[layer+1]._a[mb].raw(); //extract the layer-th hidden feature
          for( int c=0; c<features; c++ )
            chks[_output._names.length+c].set(row,out[c]);
        }
        if (job != null) job.update(1);
      }
    }.doAll(adaptFrm);

    // Return just the output columns
    int x=_output._names.length, y=adaptFrm.numCols();
    Frame ret = adaptFrm.extractFrame(x, y);
    Scope.exit();
    return ret;
  }

  @Override
  public Frame scoreDeepFeatures(Frame frame, String layer, Job j) {
    throw H2O.unimpl("Cannot extract named hidden layer '" + layer + "' for H2O DeepLearning.");
  }


  // Make (potentially expanded) reconstruction
  private double[] score_autoencoder(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds, Neurons[] neurons, boolean reconstruction, boolean reconstruction_error_per_feature) {
    assert(get_params()._autoencoder);
    assert(tmp.length == _output._names.length);
    for (int i=0; i<tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
    score_autoencoder(tmp, preds, neurons, reconstruction, reconstruction_error_per_feature); // this fills preds, returns MSE error (ignored here)
    return preds;
  }

  /**
   * Helper to reconstruct original data into preds array and compute the reconstruction error (MSE)
   * @param data Original data (unexpanded)
   * @param preds Reconstruction (potentially expanded)
   * @param neurons Array of neurons to work with (will call fprop on them)
   */
  private void score_autoencoder(double[] data, double[] preds, Neurons[] neurons, boolean reconstruction, boolean reconstruction_error_per_feature) {
    final int mb=0;
    final int n=1;
    assert(model_info().get_params()._autoencoder);
    if (model_info().isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
    ((Neurons.Input)neurons[0]).setInput(-1, data, mb);
    DeepLearningTask.fpropMiniBatch(-1, neurons, model_info, null, false, null, null /*no offset*/, n); // reconstructs data in expanded space
    double[] in  = neurons[0]._a[mb].raw(); //input (expanded)
    double[] out = neurons[neurons.length - 1]._a[mb].raw(); //output (expanded)
    assert(in.length == out.length);

    if (reconstruction) {
      // Now scale back numerical columns to original data space (scale + shift)
      model_info().data_info().unScaleNumericals(out, out); //only modifies the numericals
      System.arraycopy(out, 0, preds, 0, out.length); //copy reconstruction into preds
    } else if (reconstruction_error_per_feature){
      // Compute SE of reconstruction in expanded space for each feature
      for (int i = 0; i < in.length; ++i)
        preds[i] = Math.pow((out[i] - in[i]), 2);
    } else {
      // Compute MSE of reconstruction in expanded space
      assert(preds.length == 1);
      double l2 = 0;
      for (int i = 0; i < in.length; ++i)
        l2 += Math.pow((out[i] - in[i]), 2);
      l2 /= in.length;
      preds[0] = l2;
    }
  }

  /**
   * Compute quantile-based threshold (in reconstruction error) to find outliers
   * @param mse Vector containing reconstruction errors
   * @param quantile Quantile for cut-off
   * @return Threshold in MSE value for a point to be above the quantile
   */
  public double calcOutlierThreshold(Vec mse, double quantile) {
    Frame mse_frame = new Frame(Key.<Frame>make(), new String[]{"Reconstruction.MSE"}, new Vec[]{mse});
    DKV.put(mse_frame._key, mse_frame);

    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._train = mse_frame._key;
    parms._probs = new double[]{quantile};
    Job<QuantileModel> job = new Quantile(parms).trainModel();
    QuantileModel kmm = job.get();
    job.remove();
    double q = kmm._output._quantiles[0][0];
    kmm.delete();
    DKV.remove(mse_frame._key);
    return q;
  }

  // helper to push this model to another key (for keeping good models)
  private void putMeAsBestModel(Key bestModelKey) {
    DeepLearningModel bestModel = IcedUtils.deepCopy(this);
    DKV.put(bestModelKey, bestModel);
    if (model_info().get_params()._elastic_averaging) {
      DeepLearningModelInfo eamodel = DKV.getGet(model_info.elasticAverageModelInfoKey());
      if (eamodel != null)
        DKV.put(bestModel.model_info().elasticAverageModelInfoKey(), eamodel);
    }
    assert (DKV.get(bestModelKey) != null);
    assert (bestModel.compareTo(this) <= 0);
  }

  @Override protected Futures remove_impl(Futures fs) {
    if (_output.weights != null && _output.biases != null) {
      for (Key k : _output.weights) if (k!=null) k.remove(fs);
      for (Key k : _output.biases) if (k!=null) k.remove(fs);
    }
    if (actual_best_model_key!=null) DKV.remove(actual_best_model_key);
    DKV.remove(model_info().data_info()._key, fs);
    deleteElasticAverageModels();
    return super.remove_impl(fs);
  }

  void deleteElasticAverageModels() {
    if (model_info().get_params()._elastic_averaging) {
      DKV.remove(model_info().elasticAverageModelInfoKey());
      for (H2ONode node : H2O.CLOUD._memary) {
        DKV.remove(model_info().localModelInfoKey(node));
      }
    }
  }

  private String getHeader() {
    assert get_params()._autoencoder;
    StringBuilder sb = new StringBuilder();
    final int len = model_info().data_info().fullN();
    String prefix = "reconstr_";
    assert (model_info().data_info()._responses == 0);
    String[] coefnames = model_info().data_info().coefNames();
    assert (len == coefnames.length);
    for (int c = 0; c < len; c++) {
      if (c>0) sb.append(",");
      sb.append(prefix).append(coefnames[c]);
    }
    return sb.toString();
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb = super.toJavaInit(sb, fileCtx);
    final String mname = JCodeGen.toJavaId(_key.toString());

    final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info());
    final DeepLearningParameters p = model_info.get_params();
    if (p._categorical_encoding != Parameters.CategoricalEncodingScheme.AUTO &&
        p._categorical_encoding != Parameters.CategoricalEncodingScheme.OneHotInternal) {
      throw new IllegalArgumentException("Only default categorical_encoding scheme is supported for POJO/MOJO");
    }

    sb.ip("public boolean isSupervised() { return " + isSupervised() + "; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+ (p._autoencoder ? neurons[neurons.length-1].units : _output.nclasses()) + "; }").nl();

    if (model_info().data_info()._nums > 0) {
      sb.i(0).p("// Thread-local storage for input neuron activation values.").nl();
      sb.i(0).p("final double[] NUMS = new double[" + model_info().data_info()._nums +"];").nl();
      JCodeGen.toClassWithArray(sb, "static", "NORMMUL", model_info().data_info()._normMul);//, "Standardization/Normalization scaling factor for numerical variables.");
      JCodeGen.toClassWithArray(sb, "static", "NORMSUB", model_info().data_info()._normSub);//, "Standardization/Normalization offset for numerical variables.");
    }
    if (model_info().data_info()._cats > 0) {
      sb.i(0).p("// Thread-local workspace for storing categorical input variables.").nl();
      sb.i(0).p("final int[] CATS = new int[" + model_info().data_info()._cats +"];").nl();
    }
    JCodeGen.toStaticVar(sb, "CATOFFSETS", model_info().data_info()._catOffsets, "Offset into the workspace for categorical variables.");
    if (model_info().data_info()._normRespMul != null) {
      JCodeGen.toStaticVar(sb, "NORMRESPMUL", model_info().data_info()._normRespMul, "Standardization/Normalization scaling factor for response.");
      JCodeGen.toStaticVar(sb, "NORMRESPSUB", model_info().data_info()._normRespSub, "Standardization/Normalization offset for response.");
    }
    if (p._hidden_dropout_ratios != null) {
      JCodeGen.toStaticVar(sb, "HIDDEN_DROPOUT_RATIOS", p._hidden_dropout_ratios, "Hidden layer dropout ratios.");
    }

    final int[] layers = new int[neurons.length];
    for (int i=0;i<neurons.length;++i)
      layers[i] = neurons[i].units;
    JCodeGen.toStaticVar(sb, "NEURONS", layers, "Number of neurons for each layer.");

    if (get_params()._autoencoder) {
      sb.i(1).p("public int getPredsSize() { return " + model_info.units[model_info.units.length-1] + "; }").nl();
      sb.i(1).p("public boolean isAutoEncoder() { return true; }").nl();
      sb.i(1).p("public String getHeader() { return \"" + getHeader() + "\"; }").nl();
    }

    // Generate activation storage
    sb.i(1).p("// Thread-local storage for neuron activation values.").nl();
    sb.i(1).p("final double[][] ACTIVATION = new double[][] {").nl();
    for (int i=0; i<neurons.length; i++) {
      sb.i(2).p("/* ").p(neurons[i].getClass().getSimpleName()).p(" */ ").p("new double[").p(layers[i]).p("]");
      if (i!=neurons.length-1) sb.p(',');
      sb.nl();
    }
    sb.i(1).p("};").nl();

    // biases
    sb.i(1).p("// Neuron bias values.").nl();
    sb.i(1).p("public static final double[][] BIAS = new double[][] {").nl();
    for (int i=0; i<neurons.length; i++) {
      String colInfoClazz = mname + "_Bias_"+i;
      sb.i(2).p("/* ").p(neurons[i].getClass().getSimpleName()).p(" */ ");
      sb.p(colInfoClazz).p(".VALUES");
      if (i!=neurons.length-1) sb.p(',');
      sb.nl();
    }
    sb.i(1).p("};").nl();
    // Generate additonal classes
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for (int i=0; i<neurons.length; i++) {
          String colInfoClazz = mname + "_Bias_"+i;
          out.i().p("// Neuron bias values for ").p(neurons[i].getClass().getSimpleName()).p(" layer").nl();
          double[] bias = i == 0 ? null : new double[model_info().get_biases(i-1).size()];
          if (i>0) {
            for (int j=0; j<bias.length; ++j) bias[j] = model_info().get_biases(i-1).get(j);
          }
          JCodeGen.toClassWithArray(out, null, colInfoClazz, bias);
        }
      }
    });

    // Weights
    sb.i(1).p("// Connecting weights between neurons.").nl();
    sb.i(1).p("public static final float[][] WEIGHT = new float[][] {").nl();
    for (int i=0; i<neurons.length; i++) {
      String colInfoClazz = mname + "_Weight_"+i;
      sb.i(2).p("/* ").p(neurons[i].getClass().getSimpleName()).p(" */ ");
      sb.p(colInfoClazz).p(".VALUES");
      if (i!=neurons.length-1) sb.p(',');
      sb.nl();
    }
    sb.i(1).p("};").nl();
    // Generate weight classes
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for (int i = 0; i < neurons.length; i++) {
          String colInfoClazz = mname + "_Weight_" + i;
          if (i > 0) {
            out.i().p("// Neuron weights connecting ").
                p(neurons[i - 1].getClass().getSimpleName()).p(" and ").
                p(neurons[i].getClass().getSimpleName()).
                p(" layer").nl();
          }
          float[]
              weights =
              i == 0 ? null : new float[model_info().get_weights(i - 1).rows() * model_info()
                  .get_weights(i - 1).cols()];
          if (i > 0) {
            final int rows = model_info().get_weights(i - 1).rows();
            final int cols = model_info().get_weights(i - 1).cols();
            for (int j = 0; j < rows; ++j)
              for (int k = 0; k < cols; ++k)
                weights[j * cols + k] = model_info().get_weights(i - 1).get(j, k);
          }
          JCodeGen.toClassWithArray(out, null, colInfoClazz, weights);
        }
      }
    });

    return sb;
  }

  @Override protected boolean toJavaCheckTooBig() { return (model_info.size() > 1e6); }

  private SBPrintStream pureMatVec(final SBPrintStream bodySb) {
    bodySb.i(1).p("int cols = ACTIVATION[i-1].length;").nl();
    bodySb.i(1).p("int rows = ACTIVATION[i].length;").nl();
    bodySb.i(1).p("int extra=cols-cols%8;").nl();
    bodySb.i(1).p("int multiple = (cols/8)*8-1;").nl();
    bodySb.i(1).p("int idx = 0;").nl();
    bodySb.i(1).p("float[] a = WEIGHT[i];").nl();
    bodySb.i(1).p("double[] x = ACTIVATION[i-1];").nl();
    bodySb.i(1).p("double[] y = BIAS[i];").nl();
    bodySb.i(1).p("double[] res = ACTIVATION[i];").nl();
    bodySb.i(1).p("for (int row=0; row<rows; ++row) {").nl();
    bodySb.i(2).p("double psum0 = 0, psum1 = 0, psum2 = 0, psum3 = 0, psum4 = 0, psum5 = 0, psum6 = 0, psum7 = 0;").nl();
    bodySb.i(2).p("for (int col = 0; col < multiple; col += 8) {").nl();
    bodySb.i(3).p("int off = idx + col;").nl();
    bodySb.i(3).p("psum0 += a[off    ] * x[col    ];").nl();
    bodySb.i(3).p("psum1 += a[off + 1] * x[col + 1];").nl();
    bodySb.i(3).p("psum2 += a[off + 2] * x[col + 2];").nl();
    bodySb.i(3).p("psum3 += a[off + 3] * x[col + 3];").nl();
    bodySb.i(3).p("psum4 += a[off + 4] * x[col + 4];").nl();
    bodySb.i(3).p("psum5 += a[off + 5] * x[col + 5];").nl();
    bodySb.i(3).p("psum6 += a[off + 6] * x[col + 6];").nl();
    bodySb.i(3).p("psum7 += a[off + 7] * x[col + 7];").nl();
    bodySb.i(2).p("}").nl();
    bodySb.i(2).p("res[row] += psum0 + psum1 + psum2 + psum3;").nl();
    bodySb.i(2).p("res[row] += psum4 + psum5 + psum6 + psum7;").nl();
    bodySb.i(2).p("for (int col = extra; col < cols; col++)").nl();
    bodySb.i(3).p("res[row] += a[idx + col] * x[col];").nl();
    bodySb.i(2).p("res[row] += y[row];").nl();
    bodySb.i(2).p("idx += cols;").nl();
    bodySb.i(1).p("}").nl();
    return bodySb;
  }

  @Override protected void toJavaPredictBody(SBPrintStream bodySb,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    final DeepLearningParameters p = model_info.get_params();
    bodySb.i().p("java.util.Arrays.fill(preds,0);").nl();
    final int cats = model_info().data_info()._cats;
    final int nums = model_info().data_info()._nums;
    // initialize input layer
    if (nums > 0) bodySb.i().p("java.util.Arrays.fill(NUMS,0);").nl();
    if (cats > 0) bodySb.i().p("java.util.Arrays.fill(CATS,0);").nl();
    bodySb.i().p("int i = 0, ncats = 0;").nl();
    if (cats > 0) {
      bodySb.i().p("for(; i<"+cats+"; ++i) {").nl();
      bodySb.i(1).p("if (!Double.isNaN(data[i])) {").nl();
      bodySb.i(2).p("int c = (int) data[i];").nl();
      if (model_info().data_info()._useAllFactorLevels)
        bodySb.i(2).p("CATS[ncats] = c + CATOFFSETS[i];").nl();
      else
        bodySb.i(2).p("if (c != 0) CATS[ncats] = c + CATOFFSETS[i] - 1;").nl();
      bodySb.i(1).p("} else {").nl();  // set CAT level when encountering NAN
      bodySb.i(2).p("CATS[ncats] = CATOFFSETS[i+1]-1;").nl();
      bodySb.i(1).p("}").nl();
      bodySb.i(1).p("ncats++;").nl();
      bodySb.i().p("}").nl();
    }
    if (nums > 0) {
      bodySb.i().p("final int n = data.length;").nl();
      bodySb.i().p("for(; i<n; ++i) {").nl();
      bodySb.i(1).p("NUMS[i" + (cats > 0 ? "-" + cats : "") + "] = Double.isNaN(data[i]) ? 0 : ");
      if (model_info().data_info()._normMul != null) {
        bodySb.p("(data[i] - NORMSUB.VALUES[i" + (cats > 0 ? "-" + cats : "") + "])*NORMMUL.VALUES[i" + (cats > 0 ? "-" + cats : "") + "];").nl();
      } else {
        bodySb.p("data[i];").nl();
      }
      bodySb.i(0).p("}").nl();
    }
    bodySb.i().p("java.util.Arrays.fill(ACTIVATION[0],0);").nl();
    if (cats > 0) {
      bodySb.i().p("for (i=0; i<ncats; ++i) ACTIVATION[0][CATS[i]] = 1;").nl();
    }
    if (nums > 0) {
      bodySb.i().p("for (i=0; i<NUMS.length; ++i) {").nl();
      bodySb.i(1).p("ACTIVATION[0][CATOFFSETS[CATOFFSETS.length-1] + i] = Double.isNaN(NUMS[i]) ? 0 : NUMS[i];").nl();
      bodySb.i().p("}").nl();
    }

    boolean tanh=(p._activation == DeepLearningParameters.Activation.Tanh || p._activation == DeepLearningParameters.Activation.TanhWithDropout);
    boolean relu=(p._activation == DeepLearningParameters.Activation.Rectifier || p._activation == DeepLearningParameters.Activation.RectifierWithDropout);
    boolean maxout=(p._activation == DeepLearningParameters.Activation.Maxout || p._activation == DeepLearningParameters.Activation.MaxoutWithDropout);

    final String stopping = p._autoencoder ? "(i<=ACTIVATION.length-1)" : "(i<ACTIVATION.length-1)";

    // make prediction: forward propagation
    bodySb.i().p("for (i=1; i<ACTIVATION.length; ++i) {").nl();
    bodySb.i(1).p("java.util.Arrays.fill(ACTIVATION[i],0);").nl();
    if (maxout) {
      bodySb.i(1).p("int _k = 2; // channels").nl();
      bodySb.i(1).p("if " + stopping + " {").nl();
      bodySb.i(2).p("double[] channel = new double[_k];").nl();
      bodySb.i(2).p("for (int r=0; r<ACTIVATION[i].length; ++r) {").nl();
        bodySb.i(3).p("final int cols = ACTIVATION[i-1].length;").nl();
        bodySb.i(3).p("short maxK = 0;").nl();
        bodySb.i(3).p("for (short k = 0; k < _k; ++k) {").nl();
          bodySb.i(4).p("channel[k] = 0;").nl();
          bodySb.i(4).p("for (int c=0; c<cols; ++c) {").nl();
            bodySb.i(5).p("channel[k] += WEIGHT[i][_k*(r * cols + c) + k] * ACTIVATION[i-1][c];").nl();
          bodySb.i(4).p("}").nl();
          bodySb.i(4).p("channel[k] += BIAS[i][_k*r+k];").nl();
          bodySb.i(4).p("if (channel[k] > channel[maxK]) maxK=k;").nl();
        bodySb.i(3).p("}").nl();
        bodySb.i(3).p("ACTIVATION[i][r] = channel[maxK];").nl();
    } else {
      // optimized
      pureMatVec(bodySb);
      // Activation function
      bodySb.i(1).p("if " + stopping + " {").nl();
      bodySb.i(2).p("for (int r=0; r<ACTIVATION[i].length; ++r) {").nl();
      if (tanh) {
        bodySb.i(3).p("ACTIVATION[i][r] = 1 - 2 / (1 + Math.exp(2*ACTIVATION[i][r]));").nl();
      } else if (relu) {
        bodySb.i(3).p("ACTIVATION[i][r] = Math.max(0, ACTIVATION[i][r]);").nl();
      }
    }
    if (p._hidden_dropout_ratios != null) {
      bodySb.i(3).p("if (i<ACTIVATION.length-1) {").nl();
      bodySb.i(4).p("ACTIVATION[i][r] *= 1 - HIDDEN_DROPOUT_RATIOS[i-1];").nl();
      bodySb.i(3).p("}").nl();
    }
    bodySb.i(2).p("}").nl();
    bodySb.i(1).p("}").nl();
    if (maxout) {
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      pureMatVec(bodySb);
      bodySb.i(1).p("}").nl();
    }
    if (_output.isClassifier() && _parms._distribution != DistributionFamily.modified_huber) {
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      // softmax
      bodySb.i(2).p("double max = ACTIVATION[i][0];").nl();
      bodySb.i(2).p("for (int r=1; r<ACTIVATION[i].length; r++) {").nl();
      bodySb.i(3).p("if (ACTIVATION[i][r]>max) max = ACTIVATION[i][r];").nl();
      bodySb.i(2).p("}").nl();
      bodySb.i(2).p("double scale = 0;").nl();
      bodySb.i(2).p("for (int r=0; r<ACTIVATION[i].length; r++) {").nl();
      bodySb.i(3).p("ACTIVATION[i][r] = Math.exp(ACTIVATION[i][r] - max);").nl();
      bodySb.i(3).p("scale += ACTIVATION[i][r];").nl();
      bodySb.i(2).p("}").nl();
      bodySb.i(2).p("for (int r=0; r<ACTIVATION[i].length; r++) {").nl();
      bodySb.i(3).p("if (Double.isNaN(ACTIVATION[i][r]))").nl();
      bodySb.i(4).p("throw new RuntimeException(\"Numerical instability, predicted NaN.\");").nl();
      bodySb.i(3).p("ACTIVATION[i][r] /= scale;").nl();
      bodySb.i(3).p("preds[r+1] = ACTIVATION[i][r];").nl();
      bodySb.i(2).p("}").nl();
      bodySb.i(1).p("}").nl();
      bodySb.i().p("}").nl();
    } else if (!p._autoencoder) { //Regression and modified_huber
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      // regression: set preds[1], FillPreds0 will put it into preds[0]
      if (model_info().data_info()._normRespMul != null) {
        bodySb.i(2).p("preds[1] = (ACTIVATION[i][0] / NORMRESPMUL[0] + NORMRESPSUB[0]);").nl();
      }
      else {
        bodySb.i(2).p("preds[1] = ACTIVATION[i][0];").nl();
      }
      bodySb.i(2).p("preds[1] = " + _dist.linkInvString("preds[1]") + ";").nl();
      if (_parms._distribution == DistributionFamily.modified_huber){
        bodySb.i(2).p("preds[2] = preds[1];").nl();
        bodySb.i(2).p("preds[1] = 1-preds[2];").nl();
      }
      bodySb.i(2).p("if (Double.isNaN(preds[1])) throw new RuntimeException(\"Predicted regression target NaN!\");").nl();
      bodySb.i(1).p("}").nl();
      bodySb.i().p("}").nl();
    } else { //AutoEncoder
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      bodySb.i(2).p("for (int r=0; r<ACTIVATION[i].length; r++) {").nl();
      bodySb.i(3).p("if (Double.isNaN(ACTIVATION[i][r]))").nl();
      bodySb.i(4).p("throw new RuntimeException(\"Numerical instability, reconstructed NaN.\");").nl();
      bodySb.i(3).p("preds[r] = ACTIVATION[i][r];").nl();
      bodySb.i(2).p("}").nl();
      if (model_info().data_info()._nums > 0) {
        int ns = model_info().data_info().numStart();
        bodySb.i(2).p("for (int k=" + ns + "; k<" + model_info().data_info().fullN() + "; ++k) {").nl();
        bodySb.i(3).p("preds[k] = preds[k] / NORMMUL.VALUES[k-" + ns + "] + NORMSUB.VALUES[k-" + ns + "];").nl();
        bodySb.i(2).p("}").nl();
      }
      bodySb.i(1).p("}").nl();
      bodySb.i().p("}").nl();
      // DEBUGGING
//      bodySb.i().p("System.out.println(java.util.Arrays.toString(data));").nl();
//      bodySb.i().p("System.out.println(java.util.Arrays.toString(ACTIVATION[0]));").nl();
//      bodySb.i().p("System.out.println(java.util.Arrays.toString(ACTIVATION[ACTIVATION.length-1]));").nl();
//      bodySb.i().p("System.out.println(java.util.Arrays.toString(preds));").nl();
//      bodySb.i().p("System.out.println(\"\");").nl();
    }
    if (_output.autoencoder) return;
    if (_output.isClassifier()) {
      if (get_params()._balance_classes)
        bodySb.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
      bodySb.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold()+");").nl();
    } else {
      bodySb.ip("preds[0] = preds[1];").nl();
    }
  }

  private final String unstable_msg = technote(4,
      "\n\nTrying to predict with an unstable model." +
          "\nJob was aborted due to observed numerical instability (exponential growth)."
          + "\nEither the weights or the bias values are unreasonably large or lead to large activation values."
          + "\nTry a different initial distribution, a bounded activation function (Tanh), adding regularization"
          + "\n(via max_w2, l1, l2, dropout) or learning rate (either enable adaptive_rate or use a smaller learning rate or faster annealing).");

  @Override protected long checksum_impl() {
    return super.checksum_impl() * model_info.checksum_impl();
  }

  /**
   * Deep Learning Parameters
   */
  public static class DeepLearningParameters extends Model.Parameters {
    public String algoName() { return "DeepLearning"; }
    public String fullName() { return "Deep Learning"; }
    public String javaName() { return DeepLearningModel.class.getName(); }
    @Override protected double defaultStoppingTolerance() { return 0; }
    public DeepLearningParameters() {
      super();
      _stopping_rounds = 5;
    }
    @Override
    public long progressUnits() {
      if (train()==null) return 1;
      return (long)Math.ceil(_epochs*train().numRows());
    }
    @Override
    public double missingColumnsType() {
      return _sparse ? 0 : Double.NaN;
    }

    /**
     * If enabled, store the best model under the destination key of this model at the end of training.
     * Only applicable if training is not cancelled.
     */
    public boolean _overwrite_with_best_model = true;

    public boolean _autoencoder = false;

    public boolean _use_all_factor_levels = true;

    /**
     * If enabled, automatically standardize the data. If disabled, the user must provide properly scaled input data.
     */
    public boolean _standardize = true;

  /*Neural Net Topology*/
    /**
     * The activation function (non-linearity) to be used the neurons in the hidden layers.
     * Tanh: Hyperbolic tangent function (same as scaled and shifted sigmoid).
     * Rectifier: Chooses the maximum of (0, x) where x is the input value.
     * Maxout: Choose the maximum coordinate of the input vector.
     * With Dropout: Zero out a random user-given fraction of the
     * incoming weights to each hidden layer during training, for each
     * training row. This effectively trains exponentially many models at
     * once, and can improve generalization.
     */
    public Activation _activation = Activation.Rectifier;

    /**
     * The number and size of each hidden layer in the model.
     * For example, if a user specifies "100,200,100" a model with 3 hidden
     * layers will be produced, and the middle hidden layer will have 200
     * neurons.
     */
    public int[] _hidden = new int[]{200, 200};

    /**
     * The number of passes over the training dataset to be carried out.
     * It is recommended to start with lower values for initial experiments.
     * This value can be modified during checkpoint restarts and allows continuation
     * of selected models.
     */
    public double _epochs = 10;

    /**
     * The number of training data rows to be processed per iteration. Note that
     * independent of this parameter, each row is used immediately to update the model
     * with (online) stochastic gradient descent. This parameter controls the
     * synchronization period between nodes in a distributed environment and the
     * frequency at which scoring and model cancellation can happen. For example, if
     * it is set to 10,000 on H2O running on 4 nodes, then each node will
     * process 2,500 rows per iteration, sampling randomly from their local data.
     * Then, model averaging between the nodes takes place, and scoring can happen
     * (dependent on scoring interval and duty factor). Special values are 0 for
     * one epoch per iteration, -1 for processing the maximum amount of data
     * per iteration (if **replicate training data** is enabled, N epochs
     * will be trained per iteration on N nodes, otherwise one epoch). Special value
     * of -2 turns on automatic mode (auto-tuning).
     */
    public long _train_samples_per_iteration = -2;

    public double _target_ratio_comm_to_comp = 0.05;

  /*Adaptive Learning Rate*/
    /**
     * The implemented adaptive learning rate algorithm (ADADELTA) automatically
     * combines the benefits of learning rate annealing and momentum
     * training to avoid slow convergence. Specification of only two
     * parameters (rho and epsilon)  simplifies hyper parameter search.
     * In some cases, manually controlled (non-adaptive) learning rate and
     * momentum specifications can lead to better results, but require the
     * specification (and hyper parameter search) of up to 7 parameters.
     * If the model is built on a topology with many local minima or
     * long plateaus, it is possible for a constant learning rate to produce
     * sub-optimal results. Learning rate annealing allows digging deeper into
     * local minima, while rate decay allows specification of different
     * learning rates per layer.  When the gradient is being estimated in
     * a long valley in the optimization landscape, a large learning rate
     * can cause the gradient to oscillate and move in the wrong
     * direction. When the gradient is computed on a relatively flat
     * surface with small learning rates, the model can converge far
     * slower than necessary.
     */
    public boolean _adaptive_rate = true;

    /**
     * The first of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to momentum and relates to the memory to prior weight updates.
     * Typical values are between 0.9 and 0.999.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double _rho = 0.99;

    /**
     * The second of two hyper parameters for adaptive learning rate (ADADELTA).
     * It is similar to learning rate annealing during initial training
     * and momentum at later stages where it allows forward progress.
     * Typical values are between 1e-10 and 1e-4.
     * This parameter is only active if adaptive learning rate is enabled.
     */
    public double _epsilon = 1e-8;

  /*Learning Rate*/
    /**
     * When adaptive learning rate is disabled, the magnitude of the weight
     * updates are determined by the user specified learning rate
     * (potentially annealed), and are a function  of the difference
     * between the predicted value and the target value. That difference,
     * generally called delta, is only available at the output layer. To
     * correct the output at each hidden layer, back propagation is
     * used. Momentum modifies back propagation by allowing prior
     * iterations to influence the current update. Using the momentum
     * parameter can aid in avoiding local minima and the associated
     * instability. Too much momentum can lead to instabilities, that's
     * why the momentum is best ramped up slowly.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate = .005;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate_annealing = 1e-6;

    /**
     * The learning rate decay parameter controls the change of learning rate across layers.
     * For example, assume the rate parameter is set to 0.01, and the rate_decay parameter is set to 0.5.
     * Then the learning rate for the weights connecting the input and first hidden layer will be 0.01,
     * the learning rate for the weights connecting the first and the second hidden layer will be 0.005,
     * and the learning rate for the weights connecting the second and third hidden layer will be 0.0025, etc.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate_decay = 1.0;

  /*Momentum*/
    /**
     * The momentum_start parameter controls the amount of momentum at the beginning of training.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_start = 0;

    /**
     * The momentum_ramp parameter controls the amount of learning for which momentum increases
     * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
     * of training samples.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_ramp = 1e6;

    /**
     * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
     * The momentum used for training will remain the same for training beyond reaching that point.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_stable = 0;

    /**
     * The Nesterov accelerated gradient descent method is a modification to
     * traditional gradient descent for convex functions. The method relies on
     * gradient information at various points to build a polynomial approximation that
     * minimizes the residuals in fewer iterations of the descent.
     */
    public boolean _nesterov_accelerated_gradient = true;

  /*Regularization*/
    /**
     * A fraction of the features for each training row to be omitted from training in order
     * to improve generalization (dimension sampling).
     */
    public double _input_dropout_ratio = 0.0;

    /**
     * A fraction of the inputs for each hidden layer to be omitted from training in order
     * to improve generalization. Defaults to 0.5 for each hidden layer if omitted.
     */
    public double[] _hidden_dropout_ratios;

    /**
     * A regularization method that constrains the absolute value of the weights and
     * has the net effect of dropping some weights (setting them to zero) from a model
     * to reduce complexity and avoid overfitting.
     */
    public double _l1 = 0.0;

    /**
     * A regularization method that constrains the sum of the squared
     * weights. This method introduces bias into parameter estimates, but
     * frequently produces substantial gains in modeling as estimate variance is
     * reduced.
     */
    public double _l2 = 0.0;

    /**
     * A maximum on the sum of the squared incoming weights into
     * any one neuron. This tuning parameter is especially useful for unbound
     * activation functions such as Rectifier.
     */
    public float _max_w2 = Float.MAX_VALUE;

  /*Initialization*/
    /**
     * The distribution from which initial weights are to be drawn. The default
     * option is an optimized initialization that considers the size of the network.
     * The "uniform" option uses a uniform distribution with a mean of 0 and a given
     * interval. The "normal" option draws weights from the standard normal
     * distribution with a mean of 0 and given standard deviation.
     */
    public InitialWeightDistribution _initial_weight_distribution = InitialWeightDistribution.UniformAdaptive;

    /**
     * The scale of the distribution function for Uniform or Normal distributions.
     * For Uniform, the values are drawn uniformly from -initial_weight_scale...initial_weight_scale.
     * For Normal, the values are drawn from a Normal distribution with a standard deviation of initial_weight_scale.
     */
    public double _initial_weight_scale = 1.0;

    /**
     * Frame keys for initial weight matrices
     */
    public Key[] _initial_weights;

    /**
     * Frame keys for initial bias vectors
     */
    public Key[] _initial_biases;

    /**
     * The loss (error) function to be minimized by the model.
     * Cross Entropy loss is used when the model output consists of independent
     * hypotheses, and the outputs can be interpreted as the probability that each
     * hypothesis is true. Cross entropy is the recommended loss function when the
     * target values are class labels, and especially for imbalanced data.
     * It strongly penalizes error in the prediction of the actual class label.
     * Mean Square loss is used when the model output are continuous real values, but can
     * be used for classification as well (where it emphasizes the error on all
     * output classes, not just for the actual class).
     */
    public Loss _loss = Automatic;

  /*Scoring*/
    /**
     * The minimum time (in seconds) to elapse between model scoring. The actual
     * interval is determined by the number of training samples per iteration and the scoring duty cycle.
     */
    public double _score_interval = 5;

    /**
     * The number of training dataset points to be used for scoring. Will be
     * randomly sampled. Use 0 for selecting the entire training dataset.
     */
    public long _score_training_samples = 10000l;

    /**
     * The number of validation dataset points to be used for scoring. Can be
     * randomly sampled or stratified (if "balance classes" is set and "score
     * validation sampling" is set to stratify). Use 0 for selecting the entire
     * training dataset.
     */
    public long _score_validation_samples = 0l;

    /**
     * Maximum fraction of wall clock time spent on model scoring on training and validation samples,
     * and on diagnostics such as computation of feature importances (i.e., not on training).
     */
    public double _score_duty_cycle = 0.1;

    /**
     * The stopping criteria in terms of classification error (1-accuracy) on the
     * training data scoring dataset. When the error is at or below this threshold,
     * training stops.
     */
    public double _classification_stop = 0;

    /**
     * The stopping criteria in terms of regression error (MSE) on the training
     * data scoring dataset. When the error is at or below this threshold, training
     * stops.
     */
    public double _regression_stop = 1e-6;

    /**
     * Enable quiet mode for less output to standard output.
     */
    public boolean _quiet_mode = false;

    /**
     * Method used to sample the validation dataset for scoring, see Score Validation Samples above.
     */
    public ClassSamplingMethod _score_validation_sampling = ClassSamplingMethod.Uniform;

  /*Misc*/
    /**
     * Gather diagnostics for hidden layers, such as mean and RMS values of learning
     * rate, momentum, weights and biases.
     */
    public boolean _diagnostics = true;

    /**
     * Whether to compute variable importances for input features.
     * The implemented method (by Gedeon) considers the weights connecting the
     * input features to the first two hidden layers.
     */
    public boolean _variable_importances = true;

    /**
     * Enable fast mode (minor approximation in back-propagation), should not affect results significantly.
     */
    public boolean _fast_mode = true;

    /**
     * Increase training speed on small datasets by splitting it into many chunks
     * to allow utilization of all cores.
     */
    public boolean _force_load_balance = true;

    /**
     * Replicate the entire training dataset onto every node for faster training on small datasets.
     */
    public boolean _replicate_training_data = true;

    /**
     * Run on a single node for fine-tuning of model parameters. Can be useful for
     * checkpoint resumes after training on multiple nodes for fast initial
     * convergence.
     */
    public boolean _single_node_mode = false;

    /**
     * Enable shuffling of training data (on each node). This option is
     * recommended if training data is replicated on N nodes, and the number of training samples per iteration
     * is close to N times the dataset size, where all nodes train with (almost) all
     * the data. It is automatically enabled if the number of training samples per iteration is set to -1 (or to N
     * times the dataset size or larger).
     */
    public boolean _shuffle_training_data = false;

    public MissingValuesHandling _missing_values_handling = MissingValuesHandling.MeanImputation;

    public boolean _sparse = false;

    public boolean _col_major = false;

    public double _average_activation = 0;

    public double _sparsity_beta = 0;

    /**
     * Max. number of categorical features, enforced via hashing (Experimental)
     */
    public int _max_categorical_features = Integer.MAX_VALUE;

    /**
     * Force reproducibility on small data (will be slow - only uses 1 thread)
     */
    public boolean _reproducible = false;

    public boolean _export_weights_and_biases = false;

    public boolean _elastic_averaging = false;
    public double _elastic_averaging_moving_rate = 0.9;
    public double _elastic_averaging_regularization = 1e-3;

    // stochastic gradient descent: mini-batch size = 1
    // batch gradient descent: mini-batch size = # training rows
    public int _mini_batch_size = 1;

    public enum MissingValuesHandling {
      MeanImputation, Skip
    }

    public enum ClassSamplingMethod {
      Uniform, Stratified
    }

    public enum InitialWeightDistribution {
      UniformAdaptive, Uniform, Normal
    }

    /**
     * Activation functions
     */
    public enum Activation {
      Tanh, TanhWithDropout, Rectifier, RectifierWithDropout, Maxout, MaxoutWithDropout, ExpRectifier, ExpRectifierWithDropout
    }

    /**
     * Loss functions
     * Absolute, Quadratic, Huber, Quantile for regression
     * Quadratic, ModifiedHuber or CrossEntropy for classification
     */
    public enum Loss {
      Automatic, Quadratic, CrossEntropy, ModifiedHuber, Huber, Absolute, Quantile
    }

    /**
     * Validate model parameters
     * @param dl DL Model Builder (Driver)
     * @param expensive (whether or not this is the "final" check)
     */
    void validate(DeepLearning dl, boolean expensive) {
      boolean classification = expensive || dl.nclasses() != 0 ? dl.isClassifier() : _loss == CrossEntropy || _loss == ModifiedHuber;
      if (_loss == ModifiedHuber) dl.error("_loss", "ModifiedHuber loss function is not supported yet.");
//      if (_hidden == null || _hidden.length == 0) dl.error("_hidden", "There must be at least one hidden layer.");
      if (_hidden == null) _hidden = new int[0];
      for (int h : _hidden) if (h <= 0) dl.error("_hidden", "Hidden layer size must be positive.");
      if (_mini_batch_size < 1)
        dl.error("_mini_batch_size", "Mini-batch size must be >= 1");
      if (!_diagnostics)
        dl.warn("_diagnostics", "Deprecated option: Diagnostics are always enabled.");

      if (!_autoencoder) {
        if (_valid == null)
          dl.hide("_score_validation_samples", "score_validation_samples requires a validation frame.");

        if (classification) {
          dl.hide("_regression_stop", "regression_stop is used only with regression.");
        } else {
          dl.hide("_classification_stop", "classification_stop is used only with classification.");
  //          dl.hide("_max_hit_ratio_k", "max_hit_ratio_k is used only with classification.");
  //          dl.hide("_balance_classes", "balance_classes is used only with classification.");
        }
  //        if( !classification || !_balance_classes )
  //          dl.hide("_class_sampling_factors", "class_sampling_factors requires both classification and balance_classes.");
        if (!classification && _valid != null || _valid == null)
          dl.hide("_score_validation_sampling", "score_validation_sampling requires classification and a validation frame.");
      } else {
        if (_nfolds > 1) {
          dl.error("_nfolds", "N-fold cross-validation is not supported for Autoencoder.");
        }
      }
      if (_categorical_encoding==CategoricalEncodingScheme.Enum) {
        dl.error("_categorical_encoding", "Cannot use Enum encoding for categoricals - need numbers!");
      }
      if (_categorical_encoding==CategoricalEncodingScheme.OneHotExplicit) {
        dl.error("_categorical_encoding", "Won't use explicit Enum encoding for categoricals - it's much faster with OneHotInternal!");
      }
      if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout && _activation != Activation.ExpRectifierWithDropout) {
        dl.hide("_hidden_dropout_ratios", "hidden_dropout_ratios requires a dropout activation function.");
      }
      if (_hidden_dropout_ratios != null) {
        if (_hidden_dropout_ratios.length != _hidden.length) {
          dl.error("_hidden_dropout_ratios", "Must have " + _hidden.length + " hidden layer dropout ratios.");
        } else if (_activation != Activation.TanhWithDropout && _activation != Activation.MaxoutWithDropout && _activation != Activation.RectifierWithDropout && _activation != Activation.ExpRectifierWithDropout) {
          dl.error("_hidden_dropout_ratios", "Cannot specify hidden_dropout_ratios with a non-dropout activation function. Use 'RectifierWithDropout', 'TanhWithDropout', etc.");
        } else if (ArrayUtils.maxValue(_hidden_dropout_ratios) >= 1 || ArrayUtils.minValue(_hidden_dropout_ratios) < 0) {
          dl.error("_hidden_dropout_ratios", "Hidden dropout ratios must be >= 0 and <1.");
        }
      }
      if (_input_dropout_ratio < 0 || _input_dropout_ratio >= 1)
        dl.error("_input_dropout_ratio", "Input dropout must be >= 0 and <1.");
      if (_score_duty_cycle < 0 || _score_duty_cycle > 1)
        dl.error("_score_duty_cycle", "Score duty cycle must be >= 0 and <=1.");
      if (_l1 < 0)
        dl.error("_l1", "L1 penalty must be >= 0.");
      if (_l2 < 0)
        dl.error("_l2", "L2 penalty must be >= 0.");
      if (H2O.CLOUD.size() == 1 && _replicate_training_data)
        dl.hide("_replicate_training_data", "replicate_training_data is only valid with cloud size greater than 1.");
      if (_single_node_mode && (H2O.CLOUD.size() == 1 || !_replicate_training_data))
        dl.hide("_single_node_mode", "single_node_mode is only used with multi-node operation with replicated training data.");
      if (H2O.ARGS.client && _single_node_mode)
        dl.error("_single_node_mode", "Cannot run on a single node in client mode");
      if (_autoencoder)
        dl.hide("_use_all_factor_levels", "use_all_factor_levels is mandatory in combination with autoencoder.");
      if (_nfolds != 0)
        dl.hide("_overwrite_with_best_model", "overwrite_with_best_model is unsupported in combination with n-fold cross-validation.");
      if (_adaptive_rate) {
        dl.hide("_rate", "rate is not used with adaptive_rate.");
        dl.hide("_rate_annealing", "rate_annealing is not used with adaptive_rate.");
        dl.hide("_rate_decay", "rate_decay is not used with adaptive_rate.");
        dl.hide("_momentum_start", "momentum_start is not used with adaptive_rate.");
        dl.hide("_momentum_ramp", "momentum_ramp is not used with adaptive_rate.");
        dl.hide("_momentum_stable", "momentum_stable is not used with adaptive_rate.");
        if (_rate!=0.005) dl.warn("_rate", "rate cannot be specified if adaptive_rate is enabled.");
        if (_rate_annealing!=1e-6) dl.warn("_rate_annealing", "rate_annealing cannot be specified if adaptive_rate is enabled.");
        if (_rate_decay!=1) dl.warn("_rate_decay", "rate_decay cannot be specified if adaptive_rate is enabled.");
        if (_momentum_start!=0) dl.warn("_momentum_start", "momentum_start cannot be specified if adaptive_rate is enabled.");
        if (_momentum_ramp!=1e6) dl.warn("_momentum_ramb", "momentum_ramp cannot be specified if adaptive_rate is enabled.");
        if (_momentum_stable!=0) dl.warn("_momentum_stable", "momentum_stable cannot be specified if adaptive_rate is enabled.");
      } else {
        // ! adaptive_rate
        dl.hide("_rho", "rho is only used with adaptive_rate.");
        dl.hide("_epsilon", "epsilon is only used with adaptive_rate.");
      }
      if (_initial_weight_distribution == InitialWeightDistribution.UniformAdaptive) {
        dl.hide("_initial_weight_scale", "initial_weight_scale is not used if initial_weight_distribution == UniformAdaptive.");
      }
      if ((_initial_weights != null || _initial_biases != null) && _checkpoint != null) {
        dl.error("_checkpoint", "Cannot specify initial weights or biases during checkpoint restart. Will use the checkpoint model's weights and biases.");
      }
      if (_initial_weights != null && _initial_weights.length!=_hidden.length+1) {
        dl.error("_initial_weights", "The number of initial weights matrices must be " + (_hidden.length+1) + " (some weight matrices can be NULL/None/null).");
      }
      if (_initial_biases != null && _initial_biases.length!=_hidden.length+1) {
        dl.error("_initial_biases", "The number of initial bias vectors must be " + (_hidden.length+1) + " (some bias vectors can be NULL/None/null).");
      }
      if (_loss == null) {
        if (expensive || dl.nclasses() != 0) {
          dl.error("_loss", "Loss function must be specified. Try CrossEntropy for categorical response (classification), ModifiedHuber for binomial response, Quadratic, Absolute or Huber for numerical response (regression).");
        }
        //otherwise, we might not know whether classification=true or false (from R, for example, the training data isn't known when init(false) is called).
      } else {
        if (_autoencoder && _loss == CrossEntropy)
          dl.error("_loss", "Cannot use CrossEntropy loss for auto-encoder.");
        if (!classification && _loss == CrossEntropy)
          dl.error("_loss", technote(2, "For CrossEntropy loss, the response must be categorical."));
      }
      if (!classification && _loss == CrossEntropy)
        dl.error("_loss", "For CrossEntropy loss, the response must be categorical.");
      if (classification && (_loss != Automatic && _loss != CrossEntropy && _loss != Quadratic && _loss != ModifiedHuber))
        dl.error("_loss", "For classification tasks, the loss must be one of: Automatic, Quadratic, CrossEntropy or ModifiedHuber.");
      if (classification) {
        switch(_distribution) {
          case gaussian:
          case huber:
          case laplace:
          case quantile:
          case tweedie:
          case gamma:
          case poisson:
            dl.error("_distribution", technote(2, _distribution  + " distribution is not allowed for classification."));
            break;
          case AUTO:
          case bernoulli:
          case modified_huber:
          case multinomial:
          default:
            //OK
            break;
        }
      } else {
        switch(_distribution) {
          case multinomial:
          case bernoulli:
          case modified_huber:
            dl.error("_distribution", technote(2, _distribution  + " distribution is not allowed for regression."));
            break;
          case tweedie:
          case gamma:
          case poisson:
            if (_loss != Automatic)
              dl.error("_distribution", "Only Automatic loss (deviance) is allowed for " + _distribution + " distribution.");
            break;
          case laplace:
            if (_loss != Loss.Absolute && _loss != Automatic)
              dl.error("_distribution", "Only Automatic or Absolute loss is allowed for " + _distribution + " distribution.");
            break;
          case quantile:
            if (_loss != Loss.Quantile && _loss != Automatic)
              dl.error("_distribution", "Only Automatic or Quantile loss is allowed for " + _distribution + " distribution.");
            break;
          case huber:
            if (_loss != Loss.Huber && _loss != Automatic)
              dl.error("_distribution", "Only Automatic or Huber loss is allowed for " + _distribution + " distribution.");
            break;
          case AUTO:
          case gaussian:
          default:
            //OK
            break;
        }
      }

      if (_distribution == DistributionFamily.quasibinomial)
        dl.error("_distribution", "Quasibinomial is not supported for deeplearning in current H2O.");
      if (expensive) dl.checkDistributions();

      if (_score_training_samples < 0)
        dl.error("_score_training_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      if (_score_validation_samples < 0)
        dl.error("_score_validation_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      if (_autoencoder && _sparsity_beta > 0) {
        if (_activation == Activation.Tanh || _activation == Activation.TanhWithDropout || _activation == Activation.ExpRectifier || _activation == Activation.ExpRectifierWithDropout) {
          if (_average_activation >= 1 || _average_activation <= -1)
            dl.error("_average_activation", "Tanh average activation must be in (-1,1).");
        } else if (_activation == Activation.Rectifier || _activation == Activation.RectifierWithDropout) {
          if (_average_activation <= 0)
            dl.error("_average_activation", "Rectifier average activation must be positive.");
        }
      }
      if (!_autoencoder && _sparsity_beta != 0)
        dl.error("_sparsity_beta", "Sparsity beta can only be used for autoencoder.");
      if (classification && dl.hasOffsetCol())
        dl.error("_offset_column", "Offset is only supported for regression.");

      // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
      if (_autoencoder && _activation == Activation.Maxout)
        dl.error("_activation", "Maxout activation is not supported for auto-encoder.");
      if (_max_categorical_features < 1)
        dl.error("_max_categorical_features", "max_categorical_features must be at least 1.");
      if (_col_major)
        dl.error("_col_major", "Deprecated: Column major data handling not supported anymore - not faster.");
      if (!_sparse && _col_major) {
        dl.error("_col_major", "Cannot use column major storage for non-sparse data handling.");
      }
      if (_sparse && _elastic_averaging) {
        dl.error("_elastic_averaging", "Cannot use elastic averaging for sparse data handling.");
      }
      if (_max_w2 <= 0) {
        dl.error("_max_w2", "Cannot use max_w2 <= 0.");
      }
      if (expensive) {
        if (!classification && _balance_classes) {
          dl.error("_balance_classes", "balance_classes requires classification.");
        }
        if (_class_sampling_factors != null && !_balance_classes) {
          dl.error("_class_sampling_factors", "class_sampling_factors requires balance_classes to be enabled.");
        }
        if (_replicate_training_data && null != train() && train().byteSize() > 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() && H2O.CLOUD.size() > 1) {
          dl.error("_replicate_training_data", "Compressed training dataset takes more than 90% of avg. free available memory per node (" + 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() + "), cannot run with replicate_training_data.");
        }
      }
      if (!_elastic_averaging) {
        dl.hide("_elastic_averaging_moving_rate", "Elastic averaging is required for this parameter.");
        dl.hide("_elastic_averaging_regularization", "Elastic averaging is required for this parameter.");
      } else {
        if (_elastic_averaging_moving_rate > 1 || _elastic_averaging_moving_rate < 0)
          dl.error("_elastic_averaging_moving_rate", "Elastic averaging moving rate must be between 0 and 1.");
        if (_elastic_averaging_regularization < 0)
          dl.error("_elastic_averaging_regularization", "Elastic averaging regularization strength must be >= 0.");
      }
      if (_autoencoder && _stopping_metric != ScoreKeeper.StoppingMetric.AUTO && _stopping_metric != ScoreKeeper.StoppingMetric.MSE) {
        dl.error("_stopping_metric", "Stopping metric must either be AUTO or MSE for autoencoder.");
      }
    }

    static class Sanity {
      // the following parameters can be modified when restarting from a checkpoint
      transient static private final String[] cp_modifiable = new String[]{
              "_seed",
              "_checkpoint",
              "_epochs",
              "_score_interval",
              "_train_samples_per_iteration",
              "_target_ratio_comm_to_comp",
              "_score_duty_cycle",
              "_score_training_samples",
              "_score_validation_samples",
              "_score_validation_sampling",
              "_classification_stop",
              "_regression_stop",
              "_stopping_rounds",
              "_stopping_metric",
              "_stopping_tolerance",
              "_quiet_mode",
              "_max_confusion_matrix_size",
              "_max_hit_ratio_k",
              "_diagnostics",
              "_variable_importances",
              "_initial_weight_distribution", //will be ignored anyway
              "_initial_weight_scale", //will be ignored anyway
              "_initial_weights",
              "_initial_biases",
              "_force_load_balance",
              "_replicate_training_data",
              "_shuffle_training_data",
              "_single_node_mode",
              "_fast_mode",
              // Allow modification of the regularization parameters after a checkpoint restart
              "_l1",
              "_l2",
              "_max_w2",
              "_input_dropout_ratio",
              "_hidden_dropout_ratios",
              "_loss",
              "_overwrite_with_best_model",
              "_missing_values_handling",
              "_average_activation",
              "_reproducible",
              "_export_weights_and_biases",
              "_elastic_averaging",
              "_elastic_averaging_moving_rate",
              "_elastic_averaging_regularization",
              "_mini_batch_size",
              "_pretrained_autoencoder"
      };

      // the following parameters must not be modified when restarting from a checkpoint
      transient static private final String[] cp_not_modifiable = new String[]{
              "_drop_na20_cols",
              "_response_column",
              "_activation",
              "_use_all_factor_levels",
              "_standardize",
              "_adaptive_rate",
              "_autoencoder",
              "_rho",
              "_epsilon",
              "_sparse",
              "_sparsity_beta",
              "_col_major",
              "_rate",
              "_rate_annealing",
              "_rate_decay",
              "_momentum_start",
              "_momentum_ramp",
              "_momentum_stable",
              "_nesterov_accelerated_gradient",
              "_ignore_const_cols",
              "_max_categorical_features",
              "_nfolds",
              "_distribution",
              "_quantile_alpha",
              "_huber_alpha",
              "_tweedie_power"
      };

      static void checkCompleteness() {
        for (Field f : DeepLearningParameters.class.getDeclaredFields())
          if (!ArrayUtils.contains(cp_not_modifiable, f.getName())
                  &&
                  !ArrayUtils.contains(cp_modifiable, f.getName())
                  ) {
            if (f.getName().equals("_hidden")) continue;
            if (f.getName().equals("_ignored_columns")) continue;
	    if (f.getName().equals("$jacocoData")) continue; // If code coverage is enabled
            throw H2O.unimpl("Please add " + f.getName() + " to either cp_modifiable or cp_not_modifiable");
          }
      }

      /**
       * Check that checkpoint continuation is possible
       *
       * @param oldP old DL parameters (from checkpoint)
       * @param newP new DL parameters (user-given, to restart from checkpoint)
       */
      static void checkIfParameterChangeAllowed(final DeepLearningParameters oldP, final DeepLearningParameters newP) {
        checkCompleteness();
        if (newP._nfolds != 0)
          throw new UnsupportedOperationException("nfolds must be 0: Cross-validation is not supported during checkpoint restarts.");
        if ((newP._valid == null) != (oldP._valid == null)) {
          throw new H2OIllegalArgumentException("Presence of validation dataset must agree with the checkpointed model.");
        }
        if (!newP._autoencoder && (newP._response_column == null || !newP._response_column.equals(oldP._response_column))) {
          throw new H2OIllegalArgumentException("Response column (" + newP._response_column + ") is not the same as for the checkpointed model: " + oldP._response_column);
        }
        if (!Arrays.equals(newP._hidden, oldP._hidden)) {
          throw new H2OIllegalArgumentException("Hidden layers (" + Arrays.toString(newP._hidden) + ") is not the same as for the checkpointed model: " + Arrays.toString(oldP._hidden));
        }
        if (!Arrays.equals(newP._ignored_columns, oldP._ignored_columns)) {
          throw new H2OIllegalArgumentException("Ignored columns must be the same as for the checkpointed model.");
        }

        //compare the user-given parameters before and after and check that they are not changed
        for (Field fBefore : oldP.getClass().getFields()) {
          if (ArrayUtils.contains(cp_not_modifiable, fBefore.getName())) {
            for (Field fAfter : newP.getClass().getFields()) {
              if (fBefore.equals(fAfter)) {
                try {
                  if (fAfter.get(newP) == null || fBefore.get(oldP) == null || !fBefore.get(oldP).toString().equals(fAfter.get(newP).toString())) { // if either of the two parameters is null, skip the toString()
                    if (fBefore.get(oldP) == null && fAfter.get(newP) == null)
                      continue; //if both parameters are null, we don't need to do anything
                    throw new H2OIllegalArgumentException("Cannot change parameter: '" + fBefore.getName() + "': " + fBefore.get(oldP) + " -> " + fAfter.get(newP));
                  }
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }
          }
        }
      }

      /**
       * Update the parameters from checkpoint to user-specified
       *
       * @param srcParms     source: user-specified parameters
       * @param tgtParms     target: parameters to be modified
       * @param doIt         whether to overwrite target parameters (or just print the message)
       * @param quiet        whether to suppress the notifications about parameter changes
       */
      static void updateParametersDuringCheckpointRestart(DeepLearningParameters srcParms, DeepLearningParameters tgtParms/*actually used during training*/, boolean doIt, boolean quiet) {
        for (Field fTarget : tgtParms.getClass().getFields()) {
          if (ArrayUtils.contains(cp_modifiable, fTarget.getName())) {
            for (Field fSource : srcParms.getClass().getFields()) {
              if (fTarget.equals(fSource)) {
                try {
                  if (fSource.get(srcParms) == null || fTarget.get(tgtParms) == null || !fTarget.get(tgtParms).toString().equals(fSource.get(srcParms).toString())) { // if either of the two parameters is null, skip the toString()
                    if (fTarget.get(tgtParms) == null && fSource.get(srcParms) == null)
                      continue; //if both parameters are null, we don't need to do anything
                    if (!tgtParms._quiet_mode && !quiet)
                      Log.info("Applying user-requested modification of '" + fTarget.getName() + "': " + fTarget.get(tgtParms) + " -> " + fSource.get(srcParms));
                    if (doIt)
                      fTarget.set(tgtParms, fSource.get(srcParms));
                  }
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }
          }
        }
      }

      /**
       * Take user-given parameters and turn them into usable, fully populated parameters (e.g., to be used by Neurons during training)
       *
       * @param fromParms      raw user-given parameters from the REST API (READ ONLY)
       * @param toParms        modified set of parameters, with defaults filled in (WILL BE MODIFIED)
       * @param nClasses       number of classes (1 for regression or autoencoder)
       */
      static void modifyParms(DeepLearningParameters fromParms, DeepLearningParameters toParms, int nClasses) {
        if (fromParms._hidden_dropout_ratios == null) {
          if (fromParms._activation == Activation.TanhWithDropout
                  || fromParms._activation == Activation.MaxoutWithDropout
                  || fromParms._activation == Activation.RectifierWithDropout
                  || fromParms._activation == Activation.ExpRectifierWithDropout
              ) {
            toParms._hidden_dropout_ratios = new double[fromParms._hidden.length];
            if (!fromParms._quiet_mode)
              Log.info("_hidden_dropout_ratios: Automatically setting all hidden dropout ratios to 0.5.");
            Arrays.fill(toParms._hidden_dropout_ratios, 0.5);
          }
        } else {
          toParms._hidden_dropout_ratios = fromParms._hidden_dropout_ratios.clone();
        }
        if (H2O.CLOUD.size() == 1 && fromParms._replicate_training_data) {
          if (!fromParms._quiet_mode)
            Log.info("_replicate_training_data: Disabling replicate_training_data on 1 node.");
          toParms._replicate_training_data = false;
        }
        if (fromParms._single_node_mode && (H2O.CLOUD.size() == 1 || !fromParms._replicate_training_data)) {
          if (!fromParms._quiet_mode)
            Log.info("_single_node_mode: Disabling single_node_mode (only for multi-node operation with replicated training data).");
          toParms._single_node_mode = false;
        }
        if (!fromParms._use_all_factor_levels && fromParms._autoencoder) {
          if (!fromParms._quiet_mode)
            Log.info("_use_all_factor_levels: Automatically enabling all_factor_levels for auto-encoders.");
          toParms._use_all_factor_levels = true;
        }
        if (fromParms._overwrite_with_best_model && fromParms._nfolds != 0) {
          if (!fromParms._quiet_mode)
            Log.info("_overwrite_with_best_model: Disabling overwrite_with_best_model in combination with n-fold cross-validation.");
          toParms._overwrite_with_best_model = false;
        }
        if (fromParms._categorical_encoding==CategoricalEncodingScheme.AUTO) {
          if (!fromParms._quiet_mode)
            Log.info("_categorical_encoding: Automatically enabling OneHotInternal categorical encoding.");
          toParms._categorical_encoding = CategoricalEncodingScheme.OneHotInternal;
         }
        if (fromParms._mini_batch_size > 1) {
          Log.warn("_mini_batch_size", "Only mini-batch size = 1 is supported right now.");
          toParms._mini_batch_size = 1;
        }
        if (fromParms._adaptive_rate) {
          if (!fromParms._quiet_mode)
            Log.info("_adaptive_rate: Using automatic learning rate. Ignoring the following input parameters: "
                    + "rate, rate_decay, rate_annealing, momentum_start, momentum_ramp, momentum_stable.");
          toParms._rate = 0;
          toParms._rate_decay = 0;
          toParms._rate_annealing = 0;
          toParms._momentum_start = 0;
          toParms._momentum_ramp = 0;
          toParms._momentum_stable = 0;
        } else {
          if (!fromParms._quiet_mode)
            Log.info("_adaptive_rate: Using manual learning rate. Ignoring the following input parameters: "
                    + "rho, epsilon.");
          toParms._rho = 0;
          toParms._epsilon = 0;
        }
        if (fromParms._activation == Activation.Rectifier || fromParms._activation == Activation.RectifierWithDropout) {
          if (fromParms._max_w2 == Float.POSITIVE_INFINITY) {
            if (!fromParms._quiet_mode)
              Log.info("_max_w2: Automatically setting max_w2 to 1000 to keep (unbounded) Rectifier activation in check.");
            toParms._max_w2 = 1e3f;
          }
        }
        if (fromParms._nfolds != 0) {
          if (fromParms._overwrite_with_best_model) {
            if (!fromParms._quiet_mode)
              Log.info("_overwrite_with_best_model: Automatically disabling overwrite_with_best_model, since the final model is the only scored model with n-fold cross-validation.");
            toParms._overwrite_with_best_model = false;
          }
        }
        if (fromParms._autoencoder && fromParms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
          if (!fromParms._quiet_mode)
            Log.info("_stopping_metric: Automatically setting stopping_metric to MSE for autoencoder.");
          toParms._stopping_metric = ScoreKeeper.StoppingMetric.MSE;
        }

        // Automatically set the distribution
        if (fromParms._distribution == DistributionFamily.AUTO) {
          // For classification, allow AUTO/bernoulli/multinomial with losses CrossEntropy/Quadratic/Huber/Absolute
          if (nClasses > 1) {
            toParms._distribution = nClasses == 2 ? DistributionFamily.bernoulli : DistributionFamily.multinomial;
          }
          else {
            //regression/autoencoder
            switch(fromParms._loss) {
              case Automatic:
              case Quadratic:
                toParms._distribution = DistributionFamily.gaussian;
                break;
              case Absolute:
                toParms._distribution = DistributionFamily.laplace;
                break;
              case Quantile:
                toParms._distribution = DistributionFamily.quantile;
                break;
              case Huber:
                toParms._distribution = DistributionFamily.huber;
                break;
              case ModifiedHuber:
                toParms._distribution = DistributionFamily.modified_huber;
                break;
              default:
                throw H2O.unimpl();
            }
          }
        }

        if (fromParms._loss == Automatic) {
          switch (toParms._distribution) {
            // regression
            case gaussian:
              toParms._loss = Quadratic;
              break;
            case quantile:
              toParms._loss = Loss.Quantile;
              break;
            case laplace:
              toParms._loss = Loss.Absolute;
              break;
            case huber:
              toParms._loss = Loss.Huber;
              break;
            case tweedie:
            case poisson:
            case gamma:
              toParms._loss = Automatic; //deviance
              break;

            // classification
            case multinomial:
            case bernoulli:
              toParms._loss = CrossEntropy;
              break;
            case modified_huber:
              toParms._loss = ModifiedHuber;
              break;
            default:
              throw H2O.unimpl();
          }
        }
        if (fromParms._reproducible) {
          if (!fromParms._quiet_mode)
            Log.info("_reproducibility: Automatically enabling force_load_balancing and score_each_iteration to enforce reproducibility. Turning off replicate_training_data.");
          toParms._force_load_balance = true;
          toParms._score_each_iteration = true;
          toParms._replicate_training_data = false;
          if (fromParms._train_samples_per_iteration == -2) {
            toParms._train_samples_per_iteration = -1;
            Log.info("_reproducibility: Also setting train_samples_per_iteration to -1 since auto-tuning (-2) was specified.");
          }
        }
      }
    }

  }

  @Override
  public DeepLearningMojoWriter getMojo() {
    return new DeepLearningMojoWriter(this);
  }
}

