package hex.deeplearning;

import hex.*;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.schemas.DeepLearningModelV3;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.ModelSchema;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static hex.ModelMetrics.calcVarImp;
import static hex.deeplearning.DeepLearning.makeDataInfo;
import static water.H2O.technote;

/**
 * The Deep Learning model
 * It contains a DeepLearningModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */

public class DeepLearningModel extends Model<DeepLearningModel,DeepLearningParameters,DeepLearningModel.DeepLearningModelOutput> implements Model.DeepFeatures {

  /**
   * The Deep Learning model output contains a few extra fields in addition to the metrics in Model.Output
   * 1) Scoring history (raw data)
   * 2) weights/biases (raw data)
   * 3) variable importances (TwoDimTable)
   */
  public static class DeepLearningModelOutput extends Model.Output {
    public DeepLearningModelOutput() { super(); autoencoder = false;}
    public DeepLearningModelOutput(DeepLearning b) {
      super(b);
      autoencoder = b._parms._autoencoder;
      assert b.isSupervised() == !autoencoder;
    }
    final boolean autoencoder;

    DeepLearningScoring errors;
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
  }

  /**
   * Deviance of given distribution function at predicted value f
   * @param w observation weight
   * @param y (actual) response
   * @param f (predicted) response in original response space
   * @return value of gradient
   */
  @Override
  public double deviance(double w, double y, double f) {
    // Note: Must use sanitized parameters via get_params() as this._params can still have defaults AUTO, etc.)
    assert(get_params()._distribution != Distribution.Family.AUTO);
    return new Distribution(get_params()._distribution, get_params()._tweedie_power).deviance(w,y,f);
  }

  // Default publicly visible Schema is V2
  public ModelSchema schema() { return new DeepLearningModelV3(); }

  void set_model_info(DeepLearningModelInfo mi) { assert(mi != null); model_info = mi; }
  final public DeepLearningModelInfo model_info() { return model_info; }
  final public VarImp varImp() { return _output.errors.variable_importances; }

  private volatile DeepLearningModelInfo model_info;

  public long total_run_time;
  public long total_scoring_time;
  private long time_of_start;

  public long actual_train_samples_per_iteration;
  public long tspiGuess;
  public double time_for_communication_us; //helper for auto-tuning: time in microseconds for collective bcast/reduce of the model

  public double epoch_counter;
  public boolean stopped_early;

  public long training_rows;

  public long validation_rows;

  private DeepLearningScoring[] errors;
  public DeepLearningScoring[] scoring_history() { return errors; }
  public ScoreKeeper[] scoreKeepers() {
    ScoreKeeper[] sk = new ScoreKeeper[scoring_history().length];
    for (int i=0;i<sk.length;++i) {
      sk[i] = errors[i].validation ? errors[i].scored_valid : errors[i].scored_train;
    }
    return sk;
  }

  // Keep the best model so far, based on a single criterion (overall class. error or MSE)
  private float _bestLoss = Float.POSITIVE_INFINITY;

  public Key actual_best_model_key;
  public Key model_info_key;

  // return the most up-to-date model metrics
  DeepLearningScoring last_scored() { return errors == null ? null : errors[errors.length-1]; }

  /**
   * Get the parameters actually used for model building, not the user-given ones (_parms)
   * They might differ since some defaults are filled in, and some invalid combinations are auto-disabled in modifyParams
   * @return actually used parameters
   */
  public final DeepLearningParameters get_params() { return model_info.get_params(); }

  // Lower is better
  public float loss() {
    switch (_parms._stopping_metric) {
      case MSE:
        return (float) mse();
      case logloss:
        return (float) logloss();
      case deviance:
        return (float) deviance();
      case misclassification:
        return (float) classification_error();
      case AUC:
        return (float)(1-auc());
      case AUTO:
      default:
        return (float) (_output.isClassifier() ? logloss() : _output.autoencoder ? mse() : deviance());

    }
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      case AutoEncoder: return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
      default: throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
    }
  }

  public int compareTo(DeepLearningModel o) {
    if (o._output.isClassifier() != _output.isClassifier()) throw new UnsupportedOperationException("Cannot compare classifier against regressor.");
    if (o._output.isClassifier()) {
      if (o._output.nclasses() != _output.nclasses())
        throw new UnsupportedOperationException("Cannot compare models with different number of classes.");
    }
    return (loss() < o.loss() ? -1 : loss() > o.loss() ? 1 : 0);
  }

  public static class DeepLearningScoring extends Iced {
    public double epoch_counter;
    public double training_samples;
    public long time_stamp;
    public long training_time_ms;
    boolean validation;
    public long score_training_samples;
    public long score_validation_samples;
    public boolean classification;
    VarImp variable_importances;
    ScoreKeeper scored_train = new ScoreKeeper();
    ScoreKeeper scored_valid = new ScoreKeeper();
    public AUC2 training_AUC;
    public AUC2 validation_AUC;
    public long scoring_time;

    DeepLearningScoring deep_clone() {
      AutoBuffer ab = new AutoBuffer();
      this.write(ab);
      ab.flipForReading();
      return (DeepLearningScoring) new DeepLearningScoring().read(ab);
    }
  }

  public double classification_error() {
    if (errors == null) return Double.NaN;
    return last_scored().validation ? last_scored().scored_valid._classError : last_scored().scored_train._classError;
  }

  public double mse() {
    if (errors == null) return Double.NaN;
    return last_scored().validation ? last_scored().scored_valid._mse : last_scored().scored_train._mse;
  }

  public double auc() {
    if (errors == null) return Double.NaN;
    return last_scored().validation ? last_scored().scored_valid._AUC : last_scored().scored_train._AUC;
  }

  public double deviance() {
    if (errors == null) return Double.NaN;
    return last_scored().validation ? last_scored().scored_valid._mean_residual_deviance : last_scored().scored_train._mean_residual_deviance;
  }

  public double logloss() {
    if (errors == null) return Double.NaN;
    return last_scored().validation ? last_scored().scored_valid._logloss : last_scored().scored_train._logloss;
  }

  private TwoDimTable createScoringHistoryTable(DeepLearningScoring[] errors) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Training Speed"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Epochs"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Samples"); colTypes.add("double"); colFormat.add("%f");
    colHeaders.add("Training MSE"); colTypes.add("double"); colFormat.add("%.5f");

    if (_output.getModelCategory() == ModelCategory.Regression) {
      colHeaders.add("Training Deviance"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (!_output.autoencoder) {
      colHeaders.add("Training R^2"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.isClassifier()) {
      colHeaders.add("Training LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial) {
      colHeaders.add("Training AUC"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial || _output.getModelCategory() == ModelCategory.Multinomial) {
      colHeaders.add("Training Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (get_params()._valid != null) {
      colHeaders.add("Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (_output.getModelCategory() == ModelCategory.Regression) {
        colHeaders.add("Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (!_output.autoencoder) {
        colHeaders.add("Validation R^2"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        colHeaders.add("Validation AUC"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
      }
    }

    final int rows = errors.length;
    String[] s = new String[0];
    TwoDimTable table = new TwoDimTable(
            "Scoring History", null,
            new String[rows],
            colHeaders.toArray(s),
            colTypes.toArray(s),
            colFormat.toArray(s),
            "");
    int row = 0;
    long scoring_time = 0;
    for (final DeepLearningScoring e : errors) {
      scoring_time += e.scoring_time;
      int col = 0;
      assert (row < table.getRowDim());
      assert (col < table.getColDim());
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(e.time_stamp));
      table.set(row, col++, PrettyPrint.msecs(e.training_time_ms, true));
      int speed = (int)(e.training_samples / ((e.training_time_ms - scoring_time)/ 1e3));
//      assert(speed >= 0) : "Speed should not be negative! " + speed + " = (int)(" + e.training_samples + "/((" + e.training_time_ms + "-" + scoring_time + ")/1e3)";
      table.set(row, col++, e.training_time_ms == 0 ? null : (String.format("%d", speed) + " rows/sec"));
      table.set(row, col++, e.epoch_counter);
      table.set(row, col++, e.training_samples);
      table.set(row, col++, e.scored_train != null ? e.scored_train._mse : Double.NaN);
      if (_output.getModelCategory() == ModelCategory.Regression) {
        table.set(row, col++, e.scored_train != null ? e.scored_train._mean_residual_deviance : Double.NaN);
      }
      if (!_output.autoencoder) {
        table.set(row, col++, e.scored_train != null ? e.scored_train._r2 : Double.NaN);
      }
      if (_output.isClassifier()) {
        table.set(row, col++, e.scored_train != null ? e.scored_train._logloss : Double.NaN);
      }
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        table.set(row, col++, e.training_AUC != null ? e.training_AUC._auc : Double.NaN);
      }
      if (_output.isClassifier()) {
        table.set(row, col++, e.scored_train != null ? e.scored_train._classError : Double.NaN);
      }
      if (get_params()._valid != null) {
        table.set(row, col++, e.scored_valid != null ? e.scored_valid._mse : Double.NaN);
        if (_output.getModelCategory() == ModelCategory.Regression) {
          table.set(row, col++, e.scored_valid != null ? e.scored_valid._mean_residual_deviance : Double.NaN);
        }
        if (!_output.autoencoder) {
          table.set(row, col++, e.scored_valid != null ? e.scored_valid._r2 : Double.NaN);
        }
        if (_output.isClassifier()) {
          table.set(row, col++, e.scored_valid != null ? e.scored_valid._logloss : Double.NaN);
        }
        if (_output.getModelCategory() == ModelCategory.Binomial) {
          table.set(row, col++, e.validation_AUC != null ? e.validation_AUC._auc : Double.NaN);
        }
        if (_output.isClassifier()) {
          table.set(row, col, e.scored_valid != null ? e.scored_valid._classError : Double.NaN);
        }
      }
      row++;
    }
    return table;
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
        _output.weights[i] = Key.makeUserHidden(Key.make(destKey + ".weights." + i));
      }
      _output.biases = new Key[get_params()._hidden.length + 1];
      for (int i = 0; i < _output.biases.length; ++i) {
        _output.biases[i] = Key.makeUserHidden(Key.make(destKey + ".biases." + i));
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
    model_info = cp.model_info.deep_clone(); //don't want to interfere with model being built, just make a deep copy and store that
    if (store_best_model) {
      model_info.data_info = dataInfo.deep_clone(); //replace previous data_info with updated version that's passed in (contains enum for classification)
    } else {
      model_info.data_info = dataInfo; //shallow clone is ok
      if (parms != null) {
        assert (_parms == parms);
        assert (_parms._checkpoint == parms._checkpoint);
        assert (_parms._checkpoint == cp._key);
      }
//      _parms._checkpoint = cp._key; //it's only a "real" checkpoint if job != null, otherwise a best model copy
    }
    DKV.put(dataInfo);
    assert(get_params() != cp.model_info().get_params()); //make sure we have a clone
    actual_best_model_key = cp.actual_best_model_key;
    time_of_start = cp.time_of_start;
    total_run_time = cp.total_run_time;
    total_scoring_time = cp.total_scoring_time;
    training_rows = cp.training_rows; //copy the value to display the right number on the model page before training has started
    validation_rows = cp.validation_rows; //copy the value to display the right number on the model page before training has started
    _bestLoss = cp._bestLoss;
    epoch_counter = cp.epoch_counter;

    // deep clone scoring history
    errors = cp.errors.clone();
    for (int i=0; i<errors.length;++i)
      errors[i] = cp.errors[i].deep_clone();
    _output.errors = last_scored();
    makeWeightsBiases(destKey);
    _output._scoring_history = createScoringHistoryTable(errors);
    _output._variable_importances = calcVarImp(last_scored().variable_importances);
    _output._names = dataInfo._adaptedFrame.names();
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
    final DataInfo dinfo = makeDataInfo(train, valid, _parms);
    _output._names  = train._names   ; // Since changed by DataInfo, need to be reflected in the Model output as well
    _output._domains= train.domains();
    _output._names = dinfo._adaptedFrame.names();
    _output._domains = dinfo._adaptedFrame.domains();
    DKV.put(dinfo);
    model_info = new DeepLearningModelInfo(parms, dinfo, nClasses, train, valid);
    model_info_key = Key.makeUserHidden(Key.make(H2O.SELF));
    actual_best_model_key = Key.makeUserHidden(Key.make(H2O.SELF));
    if (parms._nfolds != 0) actual_best_model_key = null;
    if (!parms._autoencoder) {
      errors = new DeepLearningScoring[1];
      errors[0] = new DeepLearningScoring();
      errors[0].validation = (parms._valid != null);
      errors[0].time_stamp = System.currentTimeMillis();
      _output.errors = last_scored();
      _output._scoring_history = createScoringHistoryTable(errors);
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
    }
    time_of_start = System.currentTimeMillis();
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

  /**
   * Score this DeepLearning model
   * @param ftrain potentially downsampled training data for scoring
   * @param ftest  potentially downsampled validation data for scoring
   * @param job_key key of the owning job
   * @param progressKey key of the progress
   * @param iteration Map/Reduce iteration count
   * @return true if model building is ongoing
   */
  boolean doScoring(Frame ftrain, Frame ftest, Key job_key, Key progressKey, int iteration, boolean finalScoring) {
    final long now = System.currentTimeMillis();
    final double time_since_last_iter = now - _timeLastIterationEnter;
    total_run_time +=  time_since_last_iter;
    _timeLastIterationEnter = now;
    epoch_counter = (double)model_info().get_processed_total()/training_rows;


    boolean keep_running;
    // Auto-tuning
    // if multi-node and auto-tuning and at least 10 ms for communication (to avoid doing thins on multi-JVM on same node),
    // then adjust the auto-tuning parameter 'actual_train_samples_per_iteration' such that the targeted ratio of comm to comp is achieved
    // Note: actual communication time is estimated by the NetworkTest's collective test.
    if (H2O.CLOUD.size() > 1 && get_params()._train_samples_per_iteration == -2 && iteration > 1) {
      Log.info("Auto-tuning train_samples_per_iteration.");
      if (time_for_communication_us > 1e4) {
        Log.info("  Time taken for communication: " + PrettyPrint.usecs((long) time_for_communication_us));
        Log.info("  Time taken for Map/Reduce iteration: " + PrettyPrint.msecs((long) time_since_last_iter, true));
        final double comm_to_work_ratio = (time_for_communication_us * 1e-3) / time_since_last_iter;
        Log.info("  Ratio of network communication to computation: " + String.format("%.5f", comm_to_work_ratio));
        Log.info("  target_comm_to_work: " + get_params()._target_ratio_comm_to_comp);
        Log.info("Old value of train_samples_per_iteration: " + actual_train_samples_per_iteration);
        double correction = get_params()._target_ratio_comm_to_comp / comm_to_work_ratio;
        correction = Math.max(0.5,Math.min(2, correction)); //it's ok to train up to 2x more training rows per iteration, but not fewer than half.
        if (Math.abs(correction) < 0.8 || Math.abs(correction) > 1.2) { //don't correct unless it's significant (avoid slow drift)
          actual_train_samples_per_iteration /= correction;
          actual_train_samples_per_iteration = Math.max(1, actual_train_samples_per_iteration);
          Log.info("New value of train_samples_per_iteration: " + actual_train_samples_per_iteration);
        } else {
          Log.info("Keeping value of train_samples_per_iteration the same (would deviate too little from previous value): " + actual_train_samples_per_iteration);
        }
      } else {
        Log.info("Communication is faster than 10 ms. Not modifying train_samples_per_iteration: " + actual_train_samples_per_iteration);
      }
    }

    keep_running = (epoch_counter < get_params()._epochs) && !stopped_early;
    final long sinceLastScore = now -_timeLastScoreStart;

    // this is potentially slow - only do every so often
    if( !keep_running ||
        (sinceLastScore > get_params()._score_interval *1000 //don't score too often
            &&(double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < get_params()._score_duty_cycle) ) { //duty cycle
      if (progressKey != null) {
        new Job.ProgressUpdate("Scoring on " + ftrain.numRows() + " training samples" +
            (ftest != null ? (", " + ftest.numRows() + " validation samples") : "")
        ).fork(progressKey);
      }
      final boolean printme = !get_params()._quiet_mode;
      _timeLastScoreStart = System.currentTimeMillis();
      model_info().computeStats(); //might not be necessary, but is done to be certain that numbers are good
      DeepLearningScoring err = new DeepLearningScoring();
      err.time_stamp = _timeLastScoreStart;
      err.training_time_ms = total_run_time;
      err.epoch_counter = epoch_counter;
      err.training_samples = (double)model_info().get_processed_total();
      err.validation = ftest != null;
      err.score_training_samples = ftrain.numRows();
      err.classification = _output.isClassifier();

      if (get_params()._autoencoder) {
        if (printme) Log.info("Scoring the auto-encoder.");
        // training
        {
          final Frame mse_frame = scoreAutoEncoder(ftrain, Key.make(), false);
          mse_frame.delete();
          ModelMetrics mtrain = ModelMetrics.getFromDKV(this,ftrain); //updated by model.score
          _output._training_metrics = mtrain;
          err.scored_train = new ScoreKeeper(mtrain);
        }
        if (ftest != null) {
          final Frame mse_frame = scoreAutoEncoder(ftest, Key.make(), false);
          mse_frame.delete();
          ModelMetrics mtest = ModelMetrics.getFromDKV(this,ftest); //updated by model.score
          _output._validation_metrics = mtest;
          err.scored_valid = new ScoreKeeper(mtest);
        }
      } else {
        if (printme) Log.info("Scoring the model.");
        // compute errors
        final String m = model_info().toString();
        if (m.length() > 0) Log.info(m);
        final Frame trainPredict = score(ftrain);
        trainPredict.delete();

        hex.ModelMetrics mtrain = ModelMetrics.getFromDKV(this, ftrain);
        _output._training_metrics = mtrain;
        err.scored_train = new ScoreKeeper(mtrain);
        hex.ModelMetrics mtest;

        hex.ModelMetricsSupervised mm1 = (ModelMetricsSupervised)ModelMetrics.getFromDKV(this,ftrain);
        if (mm1 instanceof ModelMetricsBinomial) {
          ModelMetricsBinomial mm = (ModelMetricsBinomial)(mm1);
          err.training_AUC = mm._auc;
        }
        if (ftrain.numRows() != training_rows) {
          _output._training_metrics._description = "Metrics reported on temporary training frame with " + ftrain.numRows() + " samples";
        } else if (ftrain._key != null && ftrain._key.toString().contains("chunks")){
          _output._training_metrics._description = "Metrics reported on temporary (load-balanced) training frame";
        } else {
          _output._training_metrics._description = "Metrics reported on full training frame";
        }

        if (ftest != null) {
          Frame validPred = score(ftest);
          validPred.delete();
          mtest = ModelMetrics.getFromDKV(this, ftest);
          _output._validation_metrics = mtest;
          err.scored_valid = new ScoreKeeper(mtest);
          if (mtest != null) {
            if (mtest instanceof ModelMetricsBinomial) {
              ModelMetricsBinomial mm = (ModelMetricsBinomial)mtest;
              err.validation_AUC = mm._auc;
            }
            if (ftest.numRows() != validation_rows) {
              _output._validation_metrics._description = "Metrics reported on temporary validation frame with " + ftest.numRows() + " samples";
              if (get_params()._score_validation_sampling == DeepLearningParameters.ClassSamplingMethod.Stratified) {
                _output._validation_metrics._description += " (stratified sampling)";
              }
            } else if (ftest._key != null && ftest._key.toString().contains("chunks")){
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
        err.variable_importances = new VarImp(vi, Arrays.copyOfRange(model_info().data_info().coefNames(), 0, vi.length));
      }

      _timeLastScoreEnd = System.currentTimeMillis();
      err.scoring_time = _timeLastScoreEnd - _timeLastScoreStart;
      err.training_time_ms += err.scoring_time; //training_time_was recorded above based on time of entry into this function, but we need to add the time for scoring to this to get the total time right
      total_scoring_time += err.scoring_time;
      // enlarge the error array by one, push latest score back
      if (errors == null) {
        errors = new DeepLearningScoring[]{err};
      } else {
        DeepLearningScoring[] err2 = new DeepLearningScoring[errors.length + 1];
        System.arraycopy(errors, 0, err2, 0, errors.length);
        err2[err2.length - 1] = err;
        errors = err2;
      }
      _output.errors = last_scored();
      makeWeightsBiases(_key);
      water.util.Timer t = new Timer();
      // store weights and matrices to Frames
      if (_output.weights != null && _output.biases != null) {
        for (int i = 0; i < _output.weights.length; ++i) {
          Frame f = model_info.get_weights(i).toFrame(_output.weights[i]);
          if (i==0) {
            f._names = model_info.data_info.coefNames();
            DKV.put(f);
          }
        }
        for (int i = 0; i < _output.biases.length; ++i) {
          model_info.get_biases(i).toFrame(_output.biases[i]);
        }
        if (!_parms._quiet_mode)
          Log.info("Writing weights and biases to Frames took " + t.time()/1000. + " seconds.");
      }
      _output._scoring_history = createScoringHistoryTable(errors);
      _output._variable_importances = calcVarImp(last_scored().variable_importances);
      _output._model_summary = model_info.createSummaryTable();

      // always keep a copy of the best model so far (based on the following criterion)
      if (!finalScoring) {
        if (actual_best_model_key != null && get_params()._overwrite_with_best_model && (
                // if we have a best_model in DKV, then compare against its error() (unless it's a different model as judged by the network size)
                (DKV.get(actual_best_model_key) != null && (loss() < DKV.get(actual_best_model_key).<DeepLearningModel>get().loss() || !Arrays.equals(model_info().units, DKV.get(actual_best_model_key).<DeepLearningModel>get().model_info().units)))
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
        if (ScoreKeeper.earlyStopping(scoreKeepers(),
                get_params()._stopping_rounds, _output.isClassifier(), get_params()._stopping_metric, get_params()._stopping_tolerance
        )) {
          Log.info("Convergence detected based on simple moving average of the loss function for the past " + get_params()._stopping_rounds + " scoring events. Model building completed.");
          stopped_early = true;
        }
        if (printme) Log.info("Time taken for scoring and diagnostics: " + PrettyPrint.msecs(err.scoring_time, true));
      }
    }
    if (stopped_early) {
      // pretend as if we finished all epochs to get the progress bar pretty (especially for N-fold and grid-search)
      ((Job) DKV.getGet(job_key)).update((long) (_parms._epochs * training_rows));
      update(job_key);
      return false;
    }
    progressUpdate(progressKey, job_key, iteration, keep_running);
    update(job_key);
    return keep_running;
  }

  private void progressUpdate(Key progressKey, Key job_key, int iteration, boolean keep_running) {
    long now = System.currentTimeMillis();
    long timeSinceEntering = now - _timeLastIterationEnter;
    Job.Progress prog = DKV.getGet(progressKey);
    double progress = prog == null ? 0 : prog.progress();
    int speed = (int)(model_info().get_processed_total() * 1000. / ((total_run_time + timeSinceEntering) - total_scoring_time));
//    assert(speed >= 0);
    String msg = "Map/Reduce Iterations: " + String.format("%,d", iteration) + ". Speed: " + String.format("%,d", speed) + " samples/sec."
            + (progress == 0 ? "" : " Estimated time left: " + PrettyPrint.msecs((long) (total_run_time * (1. - progress) / progress), true));
    ((Job) DKV.getGet(job_key)).update(actual_train_samples_per_iteration); //mark the amount of work done for the progress bar
    if (progressKey != null) new Job.ProgressUpdate(msg).fork(progressKey); //update the message for the progress bar
    long sinceLastPrint = now -_timeLastPrintStart;
    if (!keep_running || sinceLastPrint > get_params()._score_interval * 1000) { //print this after every score_interval, not considering duty cycle
      _timeLastPrintStart = now;
      if (!get_params()._quiet_mode) {
        Log.info(
                "Training time: " + PrettyPrint.msecs((total_run_time + timeSinceEntering), true) + " (scoring: " + PrettyPrint.msecs(total_scoring_time, true) + "). "
                + "Processed " + String.format("%,d", model_info().get_processed_total()) + " samples" + " (" + String.format("%.3f", epoch_counter) + " epochs).\n");
        Log.info(msg);
      }
    }
  }

  /** Make either a prediction or a reconstruction.
   * @param orig Test dataset
   * @param adaptedFr Test dataset, adapted to the model
   * @return A frame containing the prediction or reconstruction
   */
  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key) {
    if (!get_params()._autoencoder) {
      return super.predictScoreImpl(orig, adaptedFr, destination_key);
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

      Frame of = new Frame((null == destination_key ? Key.make() : Key.make(destination_key)), names, f.vecs());
      DKV.put(of);
      makeMetricBuilder(null).makeModelMetrics(this, orig);
      return of;
    }
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 1, 0);
  }

  /**
   * Compute the loss function
   * @param myRows Mini-Batch Array of denseRow's containing numerical/categorical predictor and response data (standardized)
   * @return loss
   */
  public double loss(DataInfo.Row[] myRows) {
    double loss = 0;
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(model_info());
    for (DataInfo.Row myRow : myRows) {
      if (myRow == null) continue;
      long seed = -1; //ignored
      // check that all non-last layer errors/gradients are empty
      for (int i = 0; i<neurons.length-1;++i) {
        Storage.DenseVector e = neurons[i]._e;
        if (e==null) continue;
        assert(ArrayUtils.sum(e.raw()) == 0);
      }
      ((Neurons.Input)neurons[0]).setInput(seed, myRow.numIds, myRow.numVals, myRow.nBins, myRow.binIds);
      DeepLearningTask.step(seed, neurons, model_info(), null, false, null, myRow.offset);
      // check that all non-last layer errors/gradients are empty
      for (int i = 0; i<neurons.length-1;++i) {
        Storage.DenseVector e = neurons[i]._e;
        if (e==null) continue;
        assert(ArrayUtils.sum(e.raw()) == 0);
      }

      if (get_params()._loss == DeepLearningParameters.Loss.CrossEntropy) {
        if (_parms._balance_classes) throw H2O.unimpl();
        int actual = (int) myRow.response[0];
        double pred = neurons[neurons.length - 1]._a.get(actual);
        loss += -Math.log(Math.max(1e-15, pred)); //cross-entropy (same as log loss)
      } else {
        if (model_info.get_params()._autoencoder) throw H2O.unimpl();

        //prediction and actual response in standardized response space
        double pred = neurons[neurons.length - 1]._a.get(0);
        double actual = myRow.response[0];

        // FIXME: re-enable this such that the loss is computed from the de-standardized prediction/response
        //bring standardized prediction and actual response to real space
//      DataInfo di = model_info().data_info();
//      if (di._normRespMul != null) { //either both are null or none
//        pred = (pred / di._normRespMul[0] + di._normRespSub[0]);
//        actual = (actual / di._normRespMul[0] + di._normRespSub[0]);
//      }
        Distribution dist = new Distribution(model_info.get_params()._distribution, model_info.get_params()._tweedie_power);
        pred = dist.linkInv(pred);
        loss += 0.5 * dist.deviance(1 /*weight*/, actual, pred);
      }

      // add L1/L2 penalty of model coefficients (weights & biases)
      for (int i = 0; i < _parms._hidden.length + 1; ++i) {
        if (neurons[i]._w == null) continue;
        for (int row = 0; row < neurons[i]._w.rows(); ++row) {
          for (int col = 0; col < neurons[i]._w.cols(); ++col) {
            loss += _parms._l1 * Math.abs(neurons[i]._w.get(row, col));
            loss += 0.5 * _parms._l2 * Math.pow(neurons[i]._w.get(row, col), 2);
          }
        }
        for (int row = 0; row < neurons[i]._b.size(); ++row) {
          loss += _parms._l1 * Math.abs(neurons[i]._b.get(row));
          loss += 0.5 * _parms._l2 * Math.pow(neurons[i]._b.get(row), 2);
        }
      }
    }
    return loss;
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override
  public double[] score0(double[] data, double[] preds, double weight, double offset) {
    if (model_info().isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
    Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
    ((Neurons.Input)neurons[0]).setInput(-1, data);
    DeepLearningTask.step(-1, neurons, model_info, null, false, null, offset);
    double[] out = neurons[neurons.length - 1]._a.raw();
    if (_output.isClassifier()) {
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
      preds[0] = new Distribution(model_info.get_params()._distribution, model_info.get_params()._tweedie_power).linkInv(preds[0]);
      if (Double.isNaN(preds[0])) throw new RuntimeException("Predicted regression target NaN!");
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
    _output.addModelMetrics(new ModelMetricsAutoEncoder(this, frame, res.vecs()[0].mean() /*mean MSE*/));
    return res;
  }

   /**
   * Score auto-encoded reconstruction (on-the-fly, and materialize the deep features of given layer
   * @param frame Original data (can contain response, will be ignored)
   * @param layer index of the hidden layer for which to extract the features
   * @return Frame containing the deep features (#cols = hidden[layer])
   */
  public Frame scoreDeepFeatures(Frame frame, final int layer) {
    if (layer < 0 || layer >= model_info().get_params()._hidden.length)
      throw new H2OIllegalArgumentException("hidden layer (index) to extract must be between " + 0 + " and " + (model_info().get_params()._hidden.length-1),"");
    final int len = _output.nfeatures();
    Vec resp = null;
    if (isSupervised()) {
      int ridx = frame.find(_output.responseName());
      if (ridx != -1) { // drop the response for scoring!
        frame = new Frame(frame);
        resp = frame.vecs()[ridx];
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
    adaptTestForTrain(_output._names, _output.weightsName(), _output.offsetName(), _output.foldName(), null /*don't skip response*/, _output._domains, adaptFrm, _parms.missingColumnsType(), true, true);
    for (int j=0; j<features; ++j) {
      adaptFrm.add("DF.L"+(layer+1)+".C" + (j+1), vecs[j]);
    }
    new MRTask() {
      @Override public void map( Chunk chks[] ) {
        double tmp [] = new double[len];
        final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(model_info);
        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<len; i++ )
            tmp[i] = chks[i].atd(row);
          ((Neurons.Input)neurons[0]).setInput(-1, tmp); //FIXME: No weights yet
          DeepLearningTask.step(-1, neurons, model_info, null, false, null, 0 /*no offset*/);
          double[] out = neurons[layer+1]._a.raw(); //extract the layer-th hidden feature
          for( int c=0; c<features; c++ )
            chks[_output._names.length+c].set(row,out[c]);
        }
      }
    }.doAll(adaptFrm);

    // Return just the output columns
    int x=_output._names.length, y=adaptFrm.numCols();
    Frame ret = adaptFrm.extractFrame(x, y);
    if (resp != null) ret.prepend(_output.responseName(), resp);
    Scope.exit();
    return ret;
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
    assert(model_info().get_params()._autoencoder);
    if (model_info().isUnstable()) {
      Log.err(unstable_msg);
      throw new UnsupportedOperationException(unstable_msg);
    }
    ((Neurons.Input)neurons[0]).setInput(-1, data);
    DeepLearningTask.step(-1, neurons, model_info, null, false, null, 0 /*no offset*/); // reconstructs data in expanded space
    double[] in  = neurons[0]._a.raw(); //input (expanded)
    double[] out = neurons[neurons.length - 1]._a.raw(); //output (expanded)
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
    Frame mse_frame = new Frame(Key.make(), new String[]{"Reconstruction.MSE"}, new Vec[]{mse});
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
    DeepLearningModel bestModel = new DeepLearningModel(bestModelKey, null, this, true, model_info().data_info());
    DKV.put(bestModel._key, bestModel);
    if (model_info().get_params()._elastic_averaging) {
      DeepLearningModelInfo eamodel = DKV.getGet(model_info.elasticAverageModelInfoKey());
      if (eamodel != null)
        DKV.put(bestModel.model_info().elasticAverageModelInfoKey(), eamodel);
    }
    assert (DKV.get(bestModelKey) != null);
    assert (bestModel.compareTo(this) <= 0);
  }

  @Override public void delete() {
    if (_output.weights != null && _output.biases != null) {
      for (Key k : _output.weights) {
        if (DKV.getGet(k) != null) ((Frame) DKV.getGet(k)).delete();
      }
      for (Key k : _output.biases) {
        if (DKV.getGet(k) != null) ((Frame) DKV.getGet(k)).delete();
      }
    }
    DKV.remove(model_info().data_info()._key);
    deleteElasticAverageModels();
    super.delete();
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

    sb.ip("public boolean isSupervised() { return " + isSupervised() + "; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+ (p._autoencoder ? neurons[neurons.length-1].units : _output.nclasses()) + "; }").nl();

    if (model_info().data_info()._nums > 0) {
      JCodeGen.toStaticVarZeros(sb, "NUMS", new double[model_info().data_info()._nums], "Workspace for storing numerical input variables.");
      JCodeGen.toClassWithArray(sb, "static", "NORMMUL", model_info().data_info()._normMul);//, "Standardization/Normalization scaling factor for numerical variables.");
      JCodeGen.toClassWithArray(sb, "static", "NORMSUB", model_info().data_info()._normSub);//, "Standardization/Normalization offset for numerical variables.");
    }
    if (model_info().data_info()._cats > 0) {
      JCodeGen.toStaticVar(sb, "CATS", new int[model_info().data_info()._cats], "Workspace for storing categorical input variables.");
    }
    JCodeGen.toStaticVar(sb, "CATOFFSETS", model_info().data_info()._catOffsets, "Workspace for categorical offsets.");
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
    sb.i(1).p("// Storage for neuron activation values.").nl();
    sb.i(1).p("public static final double[][] ACTIVATION = new double[][] {").nl();
    for (int i=0; i<neurons.length; i++) {
      String colInfoClazz = mname + "_Activation_"+i;
      sb.i(2).p("/* ").p(neurons[i].getClass().getSimpleName()).p(" */ ");
      sb.p(colInfoClazz).p(".VALUES");
      if (i!=neurons.length-1) sb.p(',');
      sb.nl();
    }
    sb.i(1).p("};").nl();
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for (int i=0; i<neurons.length; i++) {
          String colInfoClazz = mname + "_Activation_"+i;
          out.i().p("// Neuron activation values for ").p(neurons[i].getClass().getSimpleName()).p(" layer").nl();
          JCodeGen.toClassWithArray(out, null, colInfoClazz, new double[layers[i]]);
        }
      }
    });

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
        bodySb.i(2).p("CATS[ncats++] = c + CATOFFSETS[i];").nl();
      else
        bodySb.i(2).p("if (c != 0) CATS[ncats++] = c + CATOFFSETS[i] - 1;").nl();
      bodySb.i(1).p("}").nl();
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
      bodySb.i(4).p("ACTIVATION[i][r] *= HIDDEN_DROPOUT_RATIOS[i-1];").nl();
      bodySb.i(3).p("}").nl();
    }
    bodySb.i(2).p("}").nl();
    bodySb.i(1).p("}").nl();
    if (maxout) {
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      pureMatVec(bodySb);
      bodySb.i(1).p("}").nl();
    }
    if (_output.isClassifier()) {
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
    } else if (!p._autoencoder) { //Regression
      bodySb.i(1).p("if (i == ACTIVATION.length-1) {").nl();
      // regression: set preds[1], FillPreds0 will put it into preds[0]
      if (model_info().data_info()._normRespMul != null) {
        bodySb.i(2).p("preds[1] = (ACTIVATION[i][0] / NORMRESPMUL[0] + NORMRESPSUB[0]);").nl();
      }
      else {
        bodySb.i(2).p("preds[1] = ACTIVATION[i][0];").nl();
      }
      bodySb.i(2).p("preds[1] = " + new Distribution(model_info.get_params()._distribution, model_info.get_params()._tweedie_power).linkInvString("preds[1]")+";").nl();
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
      if (_parms._balance_classes)
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
}

