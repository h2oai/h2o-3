package hex.deepwater;

import hex.*;
import static hex.ModelMetrics.calcVarImp;
import hex.genmodel.GenModel;
import hex.schemas.DeepWaterModelV3;
import water.*;
import static water.H2O.technote;
import water.api.schemas3.ModelSchemaV3;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.gpu.util;
import water.parser.BufferedString;
import water.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * The Deep Learning model
 * It contains a DeepWaterModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */
public class DeepWaterModel extends Model<DeepWaterModel,DeepWaterParameters,DeepWaterModelOutput> {

  // Default publicly visible Schema is V2
  public ModelSchemaV3 schema() { return new DeepWaterModelV3(); }

  void set_model_info(DeepWaterModelInfo mi) {
    model_info = mi;
  }

  final public DeepWaterModelInfo model_info() { return model_info; }
  final public VarImp varImp() { return _output.errors.variable_importances; }

  private volatile DeepWaterModelInfo model_info;

  // timing
  public long total_checkpointed_run_time_ms; //time spent in previous models
  public long total_training_time_ms; //total time spent running (training+scoring, including all previous models)
  public long total_scoring_time_ms; //total time spent scoring (including all previous models)
  public long total_setup_time_ms; //total time spent setting up (including all previous models)
  private long time_of_start_ms; //start time for this model (this cp restart)

  // auto-tuning
  public long actual_train_samples_per_iteration;
  public long tspiGuess;
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

  /**
   * Regular constructor (from scratch)
   * @param destKey destination key
   * @param parms DL parameters
   * @param output DL model output
   * @param train Training frame
   * @param valid Validation frame
   * @param nClasses Number of classes (1 for regression or autoencoder)
   */
  public DeepWaterModel(final Key destKey, final DeepWaterParameters parms, final DeepWaterModelOutput output, Frame train, Frame valid, int nClasses) {
    super(destKey, parms, output);
    try {
      final boolean GPU = System.getenv("CUDA_PATH")!=null;
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");
    } catch (IOException e) {
      throw new IllegalArgumentException("Couldn't load native DL libraries");
    }
    if (H2O.getCloudSize() != 1)
      throw new IllegalArgumentException("Deep Water currently only supports execution of 1 node.");

    _output._names  = train._names   ; // Since changed by DataInfo, need to be reflected in the Model output as well
    _output._domains= train.domains();
    model_info = new DeepWaterModelInfo(parms, destKey, nClasses, train, valid);
    model_info_key = Key.make(H2O.SELF);
    _dist = new Distribution(get_params());
    assert(_dist.distribution != Distribution.Family.AUTO); // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    actual_best_model_key = Key.make(H2O.SELF);
    if (parms._nfolds != 0) actual_best_model_key = null;
    if (!parms._autoencoder) {
      scoringInfo = new DeepWaterScoringInfo[1];
      scoringInfo[0] = new DeepWaterScoringInfo();
      scoringInfo[0].validation = (parms._valid != null);
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
      scoringInfo.is_classification = _output.isClassifier();
      scoringInfo.is_autoencoder = _output.isAutoencoder();

      if (printme) Log.info("Scoring the model.");
      // compute errors
      final String m = model_info().toString();
      if (m.length() > 0) Log.info(m);

      // For GainsLift and Huber, we need the full predictions to compute the model metrics
      boolean needPreds = _output.nclasses() == 2 /* gains/lift table requires predictions */ || get_params()._distribution==Distribution.Family.huber;

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
      Timer t = new Timer();
      _output._scoring_history = DeepWaterScoringInfo.createScoringHistoryTable(this.scoringInfo, (null != get_params()._valid), false, _output.getModelCategory(), _output.isAutoencoder());
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
      _output._model_summary = model_info.createSummaryTable();

      // always keep a copy of the best model so far (based on the following criterion)
      if (!finalScoring) {
        if (actual_best_model_key != null && get_params()._overwrite_with_best_model && (
                // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                (DKV.get(actual_best_model_key) != null && (loss() < DKV.get(actual_best_model_key).<DeepWaterModel>get().loss() ) )
                        ||
                        // otherwise, compare against our own _bestError
                        (DKV.get(actual_best_model_key) == null && loss() < _bestLoss)
        ) ) {
          _bestLoss = loss();
//          putMeAsBestModel(actual_best_model_key);
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
    update(jobKey);
    return keep_running;
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

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 1, 0);
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override
  public double[] score0(double[] data, double[] preds, double weight, double offset) {
    throw H2O.unimpl();
  }
  synchronized
  public double[] score0(Chunk chks[], double weight, double offset, int row_in_chunk, double[] tmp, double[] preds ) {
    throw H2O.unimpl();

  }


  @Override protected long checksum_impl() {
    return super.checksum_impl() * _output._run_time + model_info()._imageTrain.toString().hashCode();
  }

  class DeepWaterBigScore extends BigScore {
    Frame _predFrame; //OUTPUT
    @Override public Frame outputFrame(Key key, String [] names, String [][] domains){
      _predFrame = new Frame(key, names, _predFrame.vecs());
      if (domains!=null)
        _predFrame.vec(0).setDomain(domains[0]); //only the label is ever categorical
      if (_predFrame._key!=null)
        DKV.put(_predFrame);
      return _predFrame;
    }
    @Override public void map(Chunk[] chks, NewChunk[] cpreds) { }
    @Override protected void setupLocal() {
      final int weightIdx =_fr.find(get_params()._weights_column);
      final int respIdx =_fr.find(get_params()._response_column);
      final int batch_size = get_params()._mini_batch_size;
      final int classes = _output.nclasses();

      BufferedString bs = new BufferedString();
      int width = model_info()._width;
      int height = model_info()._height;
      int channels = model_info()._channels;

      ArrayList<String> train_data = new ArrayList<>();
      ArrayList<Integer> skipped = new ArrayList<>();

      //make predictions for all rows - even those with weights 0 for now (easier to deal with minibatch)
      for (int i=0; i<_fr.vec(0).length(); ++i) {
        if (isCancelled() || _j != null && _j.stop_requested()) return;
        double weight = weightIdx == -1 ? 1 : _fr.vec(weightIdx).at(i);
        if (weight == 0) { //don't send observations with weight 0 to the GPU
          skipped.add(i);
          continue;
        }
        String file = _fr.vec(0).atStr(bs, i).toString();
        train_data.add(file);
      }
      // randomly add more rows to fill up to a multiple of batch_size
      long seed = 0xDECAF + 0xD00D * model_info().get_processed_global();
      Random rng = RandomUtils.getRNG(seed);
      while (train_data.size()%batch_size!=0) {
        int pick = rng.nextInt(train_data.size());
        train_data.add(train_data.get(pick));
      }

      _mb = makeMetricBuilder(_domain);
      assert(isSupervised()); //not yet implemented for autoencoder
      int cols = _output.nclasses() + (_output.isClassifier()?1:0);
      if (_makePreds) {
        Vec[] predVecs = new Vec[cols];
        for (int i = 0; i < cols; ++i)
          predVecs[i] = _fr.anyVec().makeZero(_fr.numRows());
        _predFrame = new Frame(predVecs);
      }

      DeepWaterImageIterator img_iter;
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
        img_iter = new DeepWaterImageIterator(train_data, null /*no labels*/, model_info()._meanData, batch_size, width, height, channels, true);
        Futures fs=new Futures();
        while(img_iter.Next(fs)) {
          if (isCancelled() || _j != null && _j.stop_requested()) return;
          float[] data = img_iter.getData();
          float[] predFloats = model_info()._imageTrain.predict(data);
          Log.info("Scoring on " + batch_size + " samples (rows " + row + " and up): " + Arrays.toString(img_iter.getFiles()));

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
            float [] actual = null;
            if (_computeMetrics)
              actual = new float[]{(float)_fr.vec(respIdx).at(row)};
            if(_output.isClassifier()) {
              double[] preds =new double[classes+1];
              for (int i=0;i<classes;++i) {
                int idx=j*classes+i; //[p0,...,p9,p0,...,p9, ... ,p0,...,p9]
                preds[1+i] = predFloats[idx];
              }
              if (_parms._balance_classes)
                GenModel.correctProbabilities(preds, _output._priorClassDist, _output._modelClassDist);
              preds[0] = hex.genmodel.GenModel.getPrediction(preds, _output._priorClassDist, null, defaultThreshold());
              if (_makePreds) {
                Log.info(img_iter.getFiles()[j] + " -> preds: " + Arrays.toString(preds));
                for (int i = 0; i <= classes; ++i)
                  vw[i].set(row, preds[i]);
              }
              if (_computeMetrics)
                _mb.perRow(preds, actual, DeepWaterModel.this);
            }
            else {
              vw[0].set(row, predFloats[j]);
              if (_computeMetrics)
                _mb.perRow(new double[]{predFloats[j]}, actual, DeepWaterModel.this);
            }
            row++;
          }
          if (_makePreds) {
            for (int i = 0; i < vw.length; ++i)
              vw[i].close(fs);
            fs.blockForPending();
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
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j) {
    if (model_info()._imageTrain==null)
      model_info().javaToNative();
    final boolean computeMetrics = (!isSupervised() || (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad()));
    // Build up the names & domains.
    String[] names = makeScoringNames();
    String[][] domains = new String[names.length][];
    domains[0] = names.length == 1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();
    // Score the dataset, building the class distribution & predictions
    BigScore bs = new DeepWaterBigScore(domains[0],names.length,adaptFrm.means(),_output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,computeMetrics, true /*make preds*/, j).doAll(names.length, Vec.T_NUM, adaptFrm);
    if (computeMetrics)
      bs._mb.makeModelMetrics(this, fr, adaptFrm, bs.outputFrame());
    return bs.outputFrame(null == destination_key ? Key.make() : Key.make(destination_key), names, domains);
  }

  @Override
  protected ModelMetrics.MetricBuilder scoreMetrics(Frame adaptFrm) {
    if (model_info()._imageTrain==null)
      model_info().javaToNative();
    final boolean computeMetrics = (!isSupervised() || (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad()));
    // Build up the names & domains.
    String [] domain = !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();
    // Score the dataset, building the class distribution & predictions
    BigScore bs = new DeepWaterBigScore(domain,0,adaptFrm.means(),_output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,computeMetrics, false /*no preds*/, null).doAll(adaptFrm);
    return bs._mb;
  }

  void exportNativeModel(String path, int iteration) {
    if (_parms._backend==DeepWaterParameters.Backend.mxnet) {
      model_info()._imageTrain.saveModel(path + ".json"); //independent of iterations
      model_info()._imageTrain.saveParam(path + "." + iteration + ".params");
    } else throw H2O.unimpl();
  }

  public static String CACHE_MARKER = "__d33pW473r_1n73rn4l__";

  public void cleanUpCache() {
    final Key[] cacheKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, DeepWaterImageIterator.IcedImage.class) && k._key.toString().contains(CACHE_MARKER);
      }
    }).keys();
    Futures fs = new Futures();
    for (Key k : cacheKeys) DKV.remove(k, fs);
    fs.blockForPending();
  }
}

