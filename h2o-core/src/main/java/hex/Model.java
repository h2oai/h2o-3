package hex;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.*;
import hex.genmodel.utils.DistributionFamily;
import org.joda.time.DateTime;
import water.*;
import water.api.StreamWriter;
import water.api.StreamingSchema;
import water.api.schemas3.KeyV3;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.*;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import static water.util.FrameUtils.categoricalEncoder;
import static water.util.FrameUtils.cleanUp;

/**
 * A Model models reality (hopefully).
 * A model can be used to 'score' a row (make a prediction), or a collection of
 * rows on any compatible dataset - meaning the row has all the columns with the
 * same names as used to build the mode and any categorical columns can
 * be adapted.
 */
public abstract class Model<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Lockable<M> {

  public P _parms;   // TODO: move things around so that this can be protected
  public O _output;  // TODO: move things around so that this can be protected
  public String[] _warnings = new String[0];
  public Distribution _dist;
  protected ScoringInfo[] scoringInfo;
  public IcedHashMap<Key, String> _toDelete = new IcedHashMap<>();


  public interface DeepFeatures {
    Frame scoreAutoEncoder(Frame frame, Key destination_key, boolean reconstruction_error_per_feature);
    Frame scoreDeepFeatures(Frame frame, final int layer);
    Frame scoreDeepFeatures(Frame frame, final int layer, final Job j); //for Deep Learning
    Frame scoreDeepFeatures(Frame frame, final String layer, final Job j); //for Deep Water
  }

  public interface GLRMArchetypes {
    Frame scoreReconstruction(Frame frame, Key<Frame> destination_key, boolean reverse_transform);
    Frame scoreArchetypes(Frame frame, Key<Frame> destination_key, boolean reverse_transform);
  }

  public interface LeafNodeAssignment {
    Frame scoreLeafNodeAssignment(Frame frame, Key<Frame> destination_key);
  }

  public interface ExemplarMembers {
    Frame scoreExemplarMembers(Key<Frame> destination_key, int exemplarIdx);
  }

  public interface GetMostImportantFeatures {
    String[] getMostImportantFeatures(int n);
  }

  /**
   * Default threshold for assigning class labels to the target class (for binomial models)
   * @return threshold in 0...1
   */
  public double defaultThreshold() {
    if (_output.nclasses() != 2 || _output._training_metrics == null)
      return 0.5;
    if (_output._validation_metrics != null && ((ModelMetricsBinomial)_output._validation_metrics)._auc != null)
      return ((ModelMetricsBinomial)_output._validation_metrics)._auc.defaultThreshold();
    if (((ModelMetricsBinomial)_output._training_metrics)._auc != null)
      return ((ModelMetricsBinomial)_output._training_metrics)._auc.defaultThreshold();
    return 0.5;
  }

  public final boolean isSupervised() { return _output.isSupervised(); }

  public ToEigenVec getToEigenVec() { return null; }

  /** Model-specific parameter class.  Each model sub-class contains
   *  instance of one of these containing its builder parameters, with
   *  model-specific parameters.  E.g. KMeansModel extends Model and has a
   *  KMeansParameters extending Model.Parameters; sample parameters include K,
   *  whether or not to normalize, max iterations and the initial random seed.
   *
   *  <p>The non-transient fields are input parameters to the model-building
   *  process, and are considered "first class citizens" by the front-end - the
   *  front-end will cache Parameters (in the browser, in JavaScript, on disk)
   *  and rebuild Parameter instances from those caches.
   *
   *  WARNING: Model Parameters is not immutable object and ModelBuilder can modify
   *  them!
   */
  public abstract static class Parameters extends Iced<Parameters> {
    /** Maximal number of supported levels in response. */
    public static final int MAX_SUPPORTED_LEVELS = 1<<20;

    /** The short name, used in making Keys.  e.g. "GBM" */
    abstract public String algoName();

    /** The pretty algo name for this Model (e.g., Gradient Boosting Machine, rather than GBM).*/
    abstract public String fullName();

    /** The Java class name for this Model (e.g., hex.tree.gbm.GBM, rather than GBM).*/
    abstract public String javaName();

    /** Default relative tolerance for convergence-based early stopping  */
    protected double defaultStoppingTolerance() { return 1e-3; }

    /** How much work will be done for this model? */
    abstract public long progressUnits();

    public Key<Frame> _train;               // User-Key of the Frame the Model is trained on
    public Key<Frame> _valid;               // User-Key of the Frame the Model is validated on, if any
    public int _nfolds = 0;
    public boolean _keep_cross_validation_predictions = false;
    public boolean _keep_cross_validation_fold_assignment = false;
    public boolean _parallelize_cross_validation = true;
    public boolean _auto_rebalance = true;

    public void setTrain(Key<Frame> train) {
      this._train = train;
    }

    public enum FoldAssignmentScheme {
      AUTO, Random, Modulo, Stratified
    }
    public enum CategoricalEncodingScheme {
      AUTO(false), OneHotInternal(false), OneHotExplicit(false), Enum(false), Binary(false), Eigen(false), LabelEncoder(false), SortByResponse(true);
      CategoricalEncodingScheme(boolean needResponse) { _needResponse = needResponse; }
      final boolean _needResponse;
      boolean needsResponse() { return _needResponse; }
    }
    public long _seed = -1;
    public long getOrMakeRealSeed(){
      while (_seed==-1) {
        _seed = RandomUtils.getRNG(System.nanoTime()).nextLong();
        Log.debug("Auto-generated time-based seed for pseudo-random number generator (because it was set to -1): " + _seed);
      }
      return _seed;
    }
    public FoldAssignmentScheme _fold_assignment = FoldAssignmentScheme.AUTO;
    public CategoricalEncodingScheme _categorical_encoding = CategoricalEncodingScheme.AUTO;

    public DistributionFamily _distribution = DistributionFamily.AUTO;
    public double _tweedie_power = 1.5;
    public double _quantile_alpha = 0.5;
    public double _huber_alpha = 0.9;

    // TODO: This field belongs in the front-end column-selection process and
    // NOT in the parameters - because this requires all model-builders to have
    // column strip/ignore code.
    public String[] _ignored_columns;     // column names to ignore for training
    public boolean _ignore_const_cols;    // True if dropping constant cols
    public String _weights_column;
    public String _offset_column;
    public String _fold_column;

    public boolean _is_cv_model; //internal helper

    // Scoring a model on a dataset is not free; sometimes it is THE limiting
    // factor to model building.  By default, partially built models are only
    // scored every so many major model iterations - throttled to limit scoring
    // costs to less than 10% of the build time.  This flag forces scoring for
    // every iteration, allowing e.g. more fine-grained progress reporting.
    public boolean _score_each_iteration;

    /**
     * Maximum allowed runtime in seconds for model training. Use 0 to disable.
     */
    public double _max_runtime_secs = 0;

    /**
     * Early stopping based on convergence of stopping_metric.
     * Stop if simple moving average of the stopping_metric does not improve by stopping_tolerance for
     * k scoring events.
     * Can only trigger after at least 2k scoring events. Use 0 to disable.
     */
    public int _stopping_rounds = 0;

    /**
     * Metric to use for convergence checking, only for _stopping_rounds > 0.
     */
    public ScoreKeeper.StoppingMetric _stopping_metric = ScoreKeeper.StoppingMetric.AUTO;

    /**
     * Relative tolerance for metric-based stopping criterion: stop if relative improvement is not at least this much.
     */
    public double _stopping_tolerance = defaultStoppingTolerance();

    /** Supervised models have an expected response they get to train with! */
    public String _response_column; // response column name

    /** Should all classes be over/under-sampled to balance the class
     *  distribution? */
    public boolean _balance_classes = false;

    /** When classes are being balanced, limit the resulting dataset size to
     *  the specified multiple of the original dataset size.  Maximum relative
     *  size of the training data after balancing class counts (can be less
     *  than 1.0) */
    public float _max_after_balance_size = 5.0f;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    public float[] _class_sampling_factors;

    /** For classification models, the maximum size (in terms of classes) of
     *  the confusion matrix for it to be printed. This option is meant to
     *  avoid printing extremely large confusion matrices.  */
    public int _max_confusion_matrix_size = 20;

    /**
     * A model key associated with a previously trained Deep Learning
     * model. This option allows users to build a new model as a
     * continuation of a previously generated model.
     */
    public Key<? extends Model> _checkpoint;

    /**
     * A pretrained Autoencoder DL model with matching inputs and hidden layers
     * can be used to initialize the weights and biases (excluding the output layer).
     */
    public Key<? extends Model> _pretrained_autoencoder;

    // Public no-arg constructor for reflective creation
    public Parameters() { _ignore_const_cols = defaultDropConsCols(); }

    /** @return the training frame instance */
    public final Frame train() { return _train==null ? null : _train.get(); }
    /** @return the validation frame instance, or null
     *  if a validation frame was not specified */
    public final Frame valid() { return _valid==null ? null : _valid.get(); }

    /** Read-Lock both training and validation User frames. */
    public void read_lock_frames(Job job) {
      Frame tr = train();
      if (tr != null)
        tr.read_lock(job._key);
      if (_valid != null && !_train.equals(_valid))
        _valid.get().read_lock(job._key);
    }

    /** Read-UnLock both training and validation User frames.  This method is
     *  called on crashing cleanup pathes, so handles the case where the frames
     *  are not actually locked. */
    public void read_unlock_frames(Job job) {
      Frame tr = train();
      if( tr != null ) tr.unlock(job._key,false);
      if( _valid != null && !_train.equals(_valid) )
        valid().unlock(job._key,false);
    }

    // Override in subclasses to change the default; e.g. true in GLM
    protected boolean defaultDropConsCols() { return true; }

    /** Type of missing columns during adaptation between train/test datasets
     *  Overload this method for models that have sparse data handling - a zero
     *  will preserve the sparseness.  Otherwise, NaN is used.
     *  @return real-valued number (can be NaN)  */
    public double missingColumnsType() { return Double.NaN; }

    public boolean hasCheckpoint() { return _checkpoint != null; }

    // FIXME: this is really horrible hack, Model.Parameters has method checksum_impl,
    // but not checksum, the API is totally random :(
    public long checksum() {
      return checksum_impl();
    }

    /**
     * Compute a checksum based on all non-transient non-static ice-able assignable fields (incl. inherited ones) which have @API annotations.
     * Sort the fields first, since reflection gives us the fields in random order and we don't want the checksum to be affected by the field order.
     * NOTE: if a field is added to a Parameters class the checksum will differ even when all the previous parameters have the same value.  If
     * a client wants backward compatibility they will need to compare parameter values explicitly.
     *
     * The method is motivated by standard hash implementation `hash = hash * P + value` but we use high prime numbers in random order.
     * @return checksum
     */
    protected long checksum_impl() {
      long xs = 0x600DL;
      int count = 0;
      Field[] fields = Weaver.getWovenFields(this.getClass());
      Arrays.sort(fields,
                  new Comparator<Field>() {
                    public int compare(Field field1, Field field2) {
                      return field1.getName().compareTo(field2.getName());
                    }
                  });

      for (Field f : fields) {
        final long P = MathUtils.PRIMES[count % MathUtils.PRIMES.length];
        Class<?> c = f.getType();
        if (c.isArray()) {
          try {
            f.setAccessible(true);
            if (f.get(this) != null) {
              if (c.getComponentType() == Integer.TYPE){
                int[] arr = (int[]) f.get(this);
                xs = xs * P  + (long) Arrays.hashCode(arr);
              } else if (c.getComponentType() == Float.TYPE) {
                float[] arr = (float[]) f.get(this);
                xs = xs * P + (long) Arrays.hashCode(arr);
              } else if (c.getComponentType() == Double.TYPE) {
                double[] arr = (double[]) f.get(this);
                xs = xs * P + (long) Arrays.hashCode(arr);
              } else if (c.getComponentType() == Long.TYPE){
                long[] arr = (long[]) f.get(this);
                xs = xs * P + (long) Arrays.hashCode(arr);
              } else {
                Object[] arr = (Object[]) f.get(this);
                xs = xs * P + (long) Arrays.deepHashCode(arr);
              } //else lead to ClassCastException
            } else {
              xs = xs * P;
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          } catch (ClassCastException t) {
            throw H2O.fail(); //no support yet for int[][] etc.
          }
        } else {
          try {
            f.setAccessible(true);
            Object value = f.get(this);
            if (value != null) {
              xs = xs * P + (long)(value.hashCode());
            } else {
              xs = xs * P + P;
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
        count++;
      }
      xs ^= (train() == null ? 43 : train().checksum()) * (valid() == null ? 17 : valid().checksum());
      return xs;
    }
  }

  public ModelMetrics addModelMetrics(final ModelMetrics mm) {
    DKV.put(mm);
    incrementModelMetrics(_output, mm._key);
    return mm;
  }

  static void incrementModelMetrics(Output out, Key k) {
    synchronized(out) {
      for (Key key : out._model_metrics)
        if (k.equals(key)) return;
      out._model_metrics = Arrays.copyOf(out._model_metrics, out._model_metrics.length + 1);
      out._model_metrics[out._model_metrics.length - 1] = k;
    }
  }

  public void addWarning(String s){
    _warnings = Arrays.copyOf(_warnings,_warnings.length+1);
    _warnings[_warnings.length-1] = s;
  }

  /** Model-specific output class.  Each model sub-class contains an instance
   *  of one of these containing its "output": the pieces of the model needed
   *  for scoring.  E.g. KMeansModel has a KMeansOutput extending Model.Output
   *  which contains the cluster centers.  The output also includes the names,
   *  domains and other fields which are determined at training time.  */
  public abstract static class Output extends Iced {
    /** Columns used in the model and are used to match up with scoring data
     *  columns.  The last name is the response column name (if any). */
    public String _names[];
    
    public void setNames(String[] names) {
      _names = names;
    }
    
    public String _origNames[];

    /** Categorical/factor mappings, per column.  Null for non-categorical cols.
     *  Columns match the post-init cleanup columns.  The last column holds the
     *  response col categoricals for SupervisedModels.  */
    public String _domains[][];
    public String _origDomains[][];

    /** List of Keys to cross-validation models (non-null iff _parms._nfolds > 1 or _parms._fold_column != null) **/
    public Key _cross_validation_models[];
    /** List of Keys to cross-validation predictions (if requested) **/
    public Key _cross_validation_predictions[];
    public Key<Frame> _cross_validation_holdout_predictions_frame_id;
    public Key<Frame> _cross_validation_fold_assignment_frame_id;

    // Model-specific start/end/run times
    // Each individual model's start/end/run time is reported here, not the total time to build N+1 cross-validation models, or all grid models
    public long _start_time;
    public long _end_time;
    public long _run_time;
    protected void startClock() { _start_time = System.currentTimeMillis(); }
    protected void stopClock()  { _end_time   = System.currentTimeMillis(); _run_time = _end_time - _start_time; }

    public Output(){this(false,false,false);}
    public Output(boolean hasWeights, boolean hasOffset, boolean hasFold) {
      _hasWeights = hasWeights;
      _hasOffset = hasOffset;
      _hasFold = hasFold;
    }

    /** Any final prep-work just before model-building starts, but after the
     *  user has clicked "go".  E.g., converting a response column to an categorical
     *  touches the entire column (can be expensive), makes a parallel vec
     *  (Key/Data leak management issues), and might throw IAE if there are too
     *  many classes. */
    public Output(ModelBuilder b) {
      _isSupervised = b.isSupervised();
      if (b.error_count() > 0)
        throw new IllegalArgumentException(b.validationErrors());
      // Capture the data "shape" the model is valid on
      setNames(b._train != null ? b._train.names() : new String[0]);
      _domains = b._train != null ? b._train.domains() : new String[0][];
      _origNames = b._origNames;
      _origDomains = b._origDomains;
      _hasOffset = b.hasOffsetCol();
      _hasWeights = b.hasWeightCol();
      _hasFold = b.hasFoldCol();
      _distribution = b._distribution;
      _priorClassDist = b._priorClassDist;
      assert(_job==null);  // only set after job completion
    }

    /** Returns number of input features (OK for most supervised methods, need to override for unsupervised!) */
    public int nfeatures() {
      return _names.length - (_hasOffset?1:0)  - (_hasWeights?1:0) - (_hasFold?1:0) - (isSupervised()?1:0);
    }

    /** List of all the associated ModelMetrics objects, so we can delete them
     *  when we delete this model. */
    Key[] _model_metrics = new Key[0];

    /** Job info: final status (canceled, crashed), build time */
    public Job _job;

    /**
     * Training set metrics obtained during model training
     */
    public ModelMetrics _training_metrics;

    /**
     * Validation set metrics obtained during model training (if a validation data set was specified)
     */
    public ModelMetrics _validation_metrics;

    /**
     * Cross-Validation metrics obtained during model training
     */
    public ModelMetrics _cross_validation_metrics;

    /**
     * Summary of cross-validation metrics of all k-fold models
     */
    public TwoDimTable _cross_validation_metrics_summary;

    /**
     * User-facing model summary - Display model type, complexity, size and other useful stats
     */
    public TwoDimTable _model_summary;

    /**
     * User-facing model scoring history - 2D table with modeling accuracy as a function of time/trees/epochs/iterations, etc.
     */
    public TwoDimTable _scoring_history;

    public double[] _distribution;
    public double[] _modelClassDist;
    public double[] _priorClassDist;


    protected boolean _isSupervised;



    public boolean isSupervised() { return _isSupervised; }
    /** The name of the response column (which is always the last column). */
    protected final boolean _hasOffset; // weights and offset are kept at designated position in the names array
    protected final boolean _hasWeights;// only need to know if we have them
    protected final boolean _hasFold;// only need to know if we have them
    public boolean hasOffset  () { return _hasOffset;}
    public boolean hasWeights () { return _hasWeights;}
    public boolean hasFold () { return _hasFold;}
    public String responseName() { return isSupervised()?_names[responseIdx()]:null;}
    public String weightsName () { return _hasWeights ?_names[weightsIdx()]:null;}
    public String offsetName  () { return _hasOffset ?_names[offsetIdx()]:null;}
    public String foldName  () { return _hasFold ?_names[foldIdx()]:null;}
    public String[] interactions() { return null; }
    // Vec layout is  [c1,c2,...,cn,w?,o?,r], cn are predictor cols, r is response, w and o are weights and offset, both are optional
    public int weightsIdx() {
      if(!_hasWeights) return -1;
      return _names.length - (isSupervised()?1:0) - (hasOffset()?1:0) - 1 - (hasFold()?1:0);
    }
    public int offsetIdx() {
      if(!_hasOffset) return -1;
      return _names.length - (isSupervised()?1:0) - (hasFold()?1:0) - 1;
    }
    public int foldIdx() {
      if(!_hasFold) return -1;
      return _names.length - (isSupervised()?1:0) - 1;
    }
    public int responseIdx() {
      if(!isSupervised()) return -1;
      return _names.length-1;
    }

    /** Names of levels for a categorical response column. */
    public String[] classNames() {
      if (_domains == null || _domains.length == 0 || !isSupervised()) return null;
      return _domains[_domains.length - 1];
    }

    /** Is this model a classification model? (v. a regression or clustering model) */
    public boolean isClassifier() { return isSupervised() && nclasses() > 1; }
    /** Is this model a binomial classification model? (v. a regression or clustering model) */
    public boolean isBinomialClassifier() { return isSupervised() && nclasses() == 2; }

    /** Number of classes in the response column if it is categorical and the model is supervised. */
    public int nclasses() {
      String cns[] = classNames();
      return cns == null ? 1 : cns.length;
    }

    // Note: some algorithms MUST redefine this method to return other model categories
    public ModelCategory getModelCategory() {
      if (isSupervised())
        return (isClassifier() ?
                (nclasses() > 2 ? ModelCategory.Multinomial : ModelCategory.Binomial) :
                ModelCategory.Regression);
      return ModelCategory.Unknown;
    }
    public boolean isAutoencoder() { return false; } // Override in DeepLearning and so on.

    public synchronized void clearModelMetrics() { _model_metrics = new Key[0]; }

    public synchronized Key<ModelMetrics>[] getModelMetrics() { return Arrays.copyOf(_model_metrics, _model_metrics.length); }

    protected long checksum_impl() {
      return (null == _names ? 13 : Arrays.hashCode(_names)) *
              (null == _domains ? 17 : Arrays.deepHashCode(_domains)) *
              getModelCategory().ordinal();
    }

    public void printTwoDimTables(StringBuilder sb, Object o) {
      for (Field f : Weaver.getWovenFields(o.getClass())) {
        Class<?> c = f.getType();
        if (c.isAssignableFrom(TwoDimTable.class)) {
          try {
            TwoDimTable t = (TwoDimTable) f.get(this);
            f.setAccessible(true);
            if (t != null) sb.append(t.toString(1,false /*don't print the full table if too long*/));
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      }
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      if (_training_metrics!=null) sb.append(_training_metrics.toString());
      if (_validation_metrics!=null) sb.append(_validation_metrics.toString());
      if (_cross_validation_metrics!=null) sb.append(_cross_validation_metrics.toString());
      printTwoDimTables(sb, this);
      return sb.toString();
    }
  } // Output

  protected String[][] scoringDomains() { return _output._domains; }

  public ModelMetrics addMetrics(ModelMetrics mm) { return addModelMetrics(mm); }

  public abstract ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain);

  /** Full constructor */
  public Model(Key<M> selfKey, P parms, O output) {
    super(selfKey);
    assert parms != null;
    _parms = parms;
    _output = output;  // Output won't be set if we're assert output != null;
    if (_output != null)
      _output.startClock();
    _dist = isSupervised() && _output.nclasses() == 1 ? new Distribution(_parms) : null;
  }

  /**
   * Deviance of given distribution function at predicted value f
   * @param w observation weight
   * @param y (actual) response
   * @param f (predicted) response in original response space
   * @return value of gradient
   */
  public double deviance(double w, double y, double f) {
    return _dist.deviance(w, y, f);
  }

  public ScoringInfo[] scoring_history() { return scoringInfo; }

  /**
   * Fill a ScoringInfo with data from the ModelMetrics for this model.
   * @param scoringInfo
   */
  public void fillScoringInfo(ScoringInfo scoringInfo) {
    scoringInfo.is_classification = this._output.isClassifier();
    scoringInfo.is_autoencoder = _output.isAutoencoder();
    scoringInfo.scored_train = new ScoreKeeper(this._output._training_metrics);
    scoringInfo.scored_valid = new ScoreKeeper(this._output._validation_metrics);
    scoringInfo.scored_xval = new ScoreKeeper(this._output._cross_validation_metrics);
    scoringInfo.validation = _output._validation_metrics != null;
    scoringInfo.cross_validation = _output._cross_validation_metrics != null;

    if (this._output.isBinomialClassifier()) {
      scoringInfo.training_AUC = this._output._training_metrics == null ? null: ((ModelMetricsBinomial)this._output._training_metrics)._auc;
      scoringInfo.validation_AUC = this._output._validation_metrics == null ? null : ((ModelMetricsBinomial)this._output._validation_metrics)._auc;
    }
  }

  // return the most up-to-date model metrics
  public ScoringInfo last_scored() {
    return scoringInfo == null ? null : scoringInfo[scoringInfo.length-1];
  }

  // Lower is better
  public float loss() {
    switch (_parms._stopping_metric) {
      case MSE:
        return (float) mse();
      case MAE:
        return (float) mae();
      case RMSLE:
        return (float) rmsle();
      case logloss:
        return (float) logloss();
      case deviance:
        return (float) deviance();
      case misclassification:
        return (float) classification_error();
      case AUC:
        return (float)(1-auc());
      case mean_per_class_error:
        return (float)mean_per_class_error();
      case lift_top_group:
        return (float)lift_top_group();
      case AUTO:
      default:
        return (float) (_output.isClassifier() ? logloss() : _output.isAutoencoder() ? mse() : deviance());

    }
  } // loss()

  public int compareTo(M o) {
    if (o._output.isClassifier() != _output.isClassifier())
      throw new UnsupportedOperationException("Cannot compare classifier against regressor.");
    if (o._output.isClassifier()) {
      if (o._output.nclasses() != _output.nclasses())
        throw new UnsupportedOperationException("Cannot compare models with different number of classes.");
    }
    return (loss() < o.loss() ? -1 : loss() > o.loss() ? 1 : 0);
  }

  public double classification_error() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._classError : last_scored().validation ? last_scored().scored_valid._classError : last_scored().scored_train._classError;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    if (mm instanceof ModelMetricsBinomial) {
      return ((ModelMetricsBinomial)mm)._auc.defaultErr();
    } else if (mm instanceof ModelMetricsMultinomial) {
      return ((ModelMetricsMultinomial)mm)._cm.err();
    }
    return Double.NaN;
  }

  public double mse() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._mse : last_scored().validation ? last_scored().scored_valid._mse : last_scored().scored_train._mse;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    return mm.mse();
  }

  public double mae() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._mae : last_scored().validation ? last_scored().scored_valid._mae : last_scored().scored_train._mae;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    return ((ModelMetricsRegression)mm).mae();
  }

  public double rmsle() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._rmsle : last_scored().validation ? last_scored().scored_valid._rmsle : last_scored().scored_train._rmsle;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    return ((ModelMetricsRegression)mm).rmsle();
  }

  public double auc() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._AUC : last_scored().validation ? last_scored().scored_valid._AUC : last_scored().scored_train._AUC;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    return ((ModelMetricsBinomial)mm)._auc._auc;
  }

  public double deviance() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._mean_residual_deviance: last_scored().validation ? last_scored().scored_valid._mean_residual_deviance : last_scored().scored_train._mean_residual_deviance;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    return ((ModelMetricsRegression)mm)._mean_residual_deviance;
  }

  public double logloss() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._logloss : last_scored().validation ? last_scored().scored_valid._logloss : last_scored().scored_train._logloss;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    if (mm instanceof ModelMetricsBinomial) {
      return ((ModelMetricsBinomial)mm).logloss();
    } else if (mm instanceof ModelMetricsMultinomial) {
      return ((ModelMetricsMultinomial)mm).logloss();
    }
    return Double.NaN;
  }

  public double mean_per_class_error() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._mean_per_class_error : last_scored().validation ? last_scored().scored_valid._mean_per_class_error : last_scored().scored_train._mean_per_class_error;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    if (mm instanceof ModelMetricsBinomial) {
      return ((ModelMetricsBinomial)mm).mean_per_class_error();
    } else if (mm instanceof ModelMetricsMultinomial) {
      return ((ModelMetricsMultinomial)mm).mean_per_class_error();
    }
    return Double.NaN;
  }

  public double lift_top_group() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._lift : last_scored().validation ? last_scored().scored_valid._lift : last_scored().scored_train._lift;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;

    if (mm instanceof ModelMetricsBinomial) {
      GainsLift gl = ((ModelMetricsBinomial)mm)._gainsLift;
      if (gl != null && gl.response_rates != null && gl.response_rates.length > 0) {
        return gl.response_rates[0] / gl.avg_response_rate;
      }
    }
    return Double.NaN;
  }


  /** Adapt a Test/Validation Frame to be compatible for a Training Frame.  The
   *  intention here is that ModelBuilders can assume the test set has the same
   *  count of columns, and within each factor column the same set of
   *  same-numbered levels.  Extra levels are renumbered past those in the
   *  Train set but will still be present in the Test set, thus requiring
   *  range-checking.
   *
   *  This routine is used before model building (with no Model made yet) to
   *  check for compatible datasets, and also used to prepare a large dataset
   *  for scoring (with a Model).
   *
   *  Adaption does the following things:
   *  - Remove any "extra" Vecs appearing only in the test and not the train
   *  - Insert any "missing" Vecs appearing only in the train and not the test
   *    with all NAs ({@see missingColumnsType}).  This will issue a warning,
   *    and if the "expensive" flag is false won't actually make the column
   *    replacement column but instead will bail-out on the whole adaption (but
   *    will continue looking for more warnings).
   *  - If all columns are missing, issue an error.
   *  - Renumber matching cat levels to match the Train levels; this might make
   *    "holes" in the Test set cat levels, if some are not in the Test set.
   *  - Extra Test levels are renumbered past the end of the Train set, hence
   *    the train and test levels match up to all the train levels; there might
   *    be extra Test levels past that.
   *  - For all mis-matched levels, issue a warning.
   *
   *  The {@code test} frame is updated in-place to be compatible, by altering
   *  the names and Vecs; make a defensive copy if you do not want it modified.
   *  There is a fast-path cutout if the test set is already compatible.  Since
   *  the test-set is conditionally modifed with extra CategoricalWrappedVec optionally
   *  added it is recommended to use a Scope enter/exit to track Vec lifetimes.
   *
   *  @param test Testing Frame, updated in-place
   *  @param expensive Try hard to adapt; this might involve the creation of
   *  whole Vecs and thus get expensive.  If {@code false}, then only adapt if
   *  no warnings and errors; otherwise just the messages are produced.
   *  Created Vecs have to be deleted by the caller (e.g. Scope.enter/exit).
   *  @return Array of warnings; zero length (never null) for no warnings.
   *  Throws {@code IllegalArgumentException} if no columns are in common, or
   *  if any factor column has no levels in common.
   */
  public String[] adaptTestForTrain(Frame test, boolean expensive, boolean computeMetrics) {
    return adaptTestForTrain(
            test,
            _output._origNames,
            _output._origDomains,
            _output._names,
            _output._domains,
            _parms, expensive, computeMetrics, _output.interactions(), getToEigenVec(), _toDelete, false);
  }

  /**
   * @param test Frame to be adapted
   * @param origNames Training column names before categorical column encoding - can be the same as names
   * @param origDomains Training column levels before categorical column encoding - can be the same as domains
   * @param names Training column names
   * @param domains Training column levels
   * @param parms Model parameters
   * @param expensive Whether to actually do the hard work
   * @param computeMetrics Whether metrics can be (and should be) computed
   * @param interactions Column names to create pairwise interactions with
   * @param catEncoded Whether the categorical columns of the test frame were already transformed via categorical_encoding
   */
  public static String[] adaptTestForTrain(Frame test, String[] origNames, String[][] origDomains, String[] names, String[][] domains,
                                           Parameters parms, boolean expensive, boolean computeMetrics, String[] interactions, ToEigenVec tev,
                                           IcedHashMap<Key, String> toDelete, boolean catEncoded) throws IllegalArgumentException {
    String[] msg = new String[0];
    if (test == null) return msg;
    if (catEncoded && origNames==null) return msg;

    // test frame matches the training frame (after categorical encoding, if applicable)
    String[][] tdomains = test.domains();
    if (names == test._names && domains == tdomains || (Arrays.equals(names, test._names) && Arrays.deepEquals(domains, tdomains)) )
      return msg;

    String[] backupNames = names;
    String[][] backupDomains = domains;

    final String weights = parms._weights_column;
    final String offset = parms._offset_column;
    final String fold = parms._fold_column;
    final String response = parms._response_column;

    // whether we need to be careful with categorical encoding - the test frame could be either in original state or in encoded state
    final boolean checkCategoricals =
        parms._categorical_encoding == Parameters.CategoricalEncodingScheme.OneHotExplicit ||
        parms._categorical_encoding == Parameters.CategoricalEncodingScheme.Eigen ||
        parms._categorical_encoding == Parameters.CategoricalEncodingScheme.Binary;

    // test frame matches the user-given frame (before categorical encoding, if applicable)
    if( checkCategoricals && origNames != null ) {
      boolean match = Arrays.equals(origNames, test._names);
      if (!match) {
        match = true;
        // In case the test set has extra columns not in the training set - check that all original pre-encoding columns are available in the test set
        // We could be lenient here and fill missing columns with NA, but then it gets difficult to decide whether this frame is pre/post encoding, if a certain fraction of columns mismatch...
        for (String s : origNames) {
          match &= ArrayUtils.contains(test.names(), s);
          if (!match) break;
        }
      }
      // still have work to do below, make sure we set the names/domains to the original user-given values such that we can do the int->enum mapping and cat. encoding below (from scratch)
      if (match) {
        names = origNames;
        domains = origDomains;
      }
    }

    // create the interactions now and bolt them on to the front of the test Frame
    if( null!=interactions ) {
      int[] interactionIndexes = new int[interactions.length];
      for(int i=0;i<interactions.length;++i)
        interactionIndexes[i] = test.find(interactions[i]);
      test.add(makeInteractions(test, false, InteractionPair.generatePairwiseInteractionsFromList(interactionIndexes), true, true, false));
    }

    final double missing = parms.missingColumnsType();

    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap categoricals
    ArrayList<String> msgs = new ArrayList<>();
    Vec vvecs[] = new Vec[names.length];
    int good = 0;               // Any matching column names, at all?
    int convNaN = 0;  // count of columns that were replaced with NA
    for( int i=0; i<names.length; i++ ) {
      Vec vec = test.vec(names[i]); // Search in the given validation set
      boolean isResponse = response != null && names[i].equals(response);
      boolean isWeights = weights != null && names[i].equals(weights);
      boolean isOffset = offset != null && names[i].equals(offset);
      boolean isFold = fold != null && names[i].equals(fold);
      // If a training set column is missing in the test set, complain (if it's ok, fill in with NAs (or 0s if it's a fold-column))
      if (vec == null) {
        if (isResponse && computeMetrics)
          throw new IllegalArgumentException("Test/Validation dataset is missing response column '" + response + "'");
        else if (isOffset)
          throw new IllegalArgumentException("Test/Validation dataset is missing offset column '" + offset + "'");
        else if (isWeights && computeMetrics) {
          if (expensive) {
            vec = test.anyVec().makeCon(1);
            toDelete.put(vec._key, "adapted missing vectors");
            msgs.add(H2O.technote(1, "Test/Validation dataset is missing weights column '" + names[i] + "' (needed because a response was found and metrics are to be computed): substituting in a column of 1s"));
          }
        } else if (expensive) {
          String str = "Test/Validation dataset is missing column '" + names[i] + "': substituting in a column of " + (isFold ? 0 : missing);
          vec = test.anyVec().makeCon(isFold ? 0 : missing);
          toDelete.put(vec._key, "adapted missing vectors");
          if (!isFold) convNaN++;
          msgs.add(str);
        }
      }
      if( vec != null ) {          // I have a column with a matching name
        if( domains[i] != null ) { // Model expects an categorical
          if (vec.isString())
            vec = VecUtils.stringToCategorical(vec); //turn a String column into a categorical column (we don't delete the original vec here)
          if( expensive && vec.domain() != domains[i] && !Arrays.equals(vec.domain(),domains[i]) ) { // Result needs to be the same categorical
            CategoricalWrappedVec evec;
            try {
              evec = vec.adaptTo(domains[i]); // Convert to categorical or throw IAE
              toDelete.put(evec._key, "categorically adapted vec");
            } catch( NumberFormatException nfe ) {
              throw new IllegalArgumentException("Test/Validation dataset has a non-categorical column '"+names[i]+"' which is categorical in the training data");
            }
            String[] ds = evec.domain();
            assert ds != null && ds.length >= domains[i].length;
            if( isResponse && vec.domain() != null && ds.length == domains[i].length+vec.domain().length )
              throw new IllegalArgumentException("Test/Validation dataset has a categorical response column '"+names[i]+"' with no levels in common with the model");
            if (ds.length > domains[i].length)
              msgs.add("Test/Validation dataset column '" + names[i] + "' has levels not trained on: " + Arrays.toString(Arrays.copyOfRange(ds, domains[i].length, ds.length)));
            vec = evec;
          }
        } else if(vec.isCategorical()) {
          if (parms._categorical_encoding == Parameters.CategoricalEncodingScheme.LabelEncoder) {
            Vec evec = vec.toNumericVec();
            toDelete.put(evec._key, "label encoded vec");
            vec = evec;
          } else {
            throw new IllegalArgumentException("Test/Validation dataset has categorical column '" + names[i] + "' which is real-valued in the training data");
          }
        }
        good++;      // Assumed compatible; not checking e.g. Strings vs UUID
      }
      vvecs[i] = vec;
    }
    if( good == names.length || (response != null && test.find(response) == -1 && good == names.length - 1) )  // Only update if got something for all columns
      test.restructure(names,vvecs,good);

    boolean haveCategoricalPredictors = false;
    if (expensive && checkCategoricals && !catEncoded) {
      for (int i=0; i<test.numCols(); ++i) {
        if (test.names()[i].equals(response)) continue;
        if (test.names()[i].equals(weights)) continue;
        if (test.names()[i].equals(offset)) continue;
        if (test.names()[i].equals(fold)) continue;
        // either the column of the test set is categorical (could be a numeric col that's already turned into a factor)
        if (test.vec(i).cardinality() > 0) {
          haveCategoricalPredictors = true;
          break;
        }
        // or a equally named column of the training set is categorical, but the test column isn't (e.g., numeric column provided to be converted to a factor)
        int whichCol = ArrayUtils.find(names, test.name(i));
        if (whichCol >= 0 && domains[whichCol] != null) {
          haveCategoricalPredictors = true;
          break;
        }
      }
    }
    // check if we first need to expand categoricals before calling this method again
    if (expensive && !catEncoded && haveCategoricalPredictors) {
      Frame updated = categoricalEncoder(test, new String[]{weights, offset, fold, response}, parms._categorical_encoding, tev);
      toDelete.put(updated._key, "categorically encoded frame");
      test.restructure(updated.names(), updated.vecs()); //updated in place
      String[] msg2 = adaptTestForTrain(test, origNames, origDomains, backupNames, backupDomains, parms, expensive, computeMetrics, interactions, tev, toDelete, true /*catEncoded*/);
      msgs.addAll(Arrays.asList(msg2));
      return msgs.toArray(new String[msgs.size()]);
    }
    if( good == convNaN )
      throw new IllegalArgumentException("Test/Validation dataset has no columns in common with the training set");

    return msgs.toArray(new String[msgs.size()]);
  }


  /**
   * Bulk score the frame, and auto-name the resulting predictions frame.
   * @see #score(Frame, String)
   * @param fr frame which should be scored
   * @return A new frame containing a predicted values. For classification it
   *         contains a column with prediction and distribution for all
   *         response classes. For regression it contains only one column with
   *         predicted values.
   * @throws IllegalArgumentException
   */
  public Frame score(Frame fr) throws IllegalArgumentException {
    return score(fr, null, null, true);
  }

  /** Bulk score the frame {@code fr}, producing a Frame result; the 1st
   *  Vec is the predicted class, the remaining Vecs are the probability
   *  distributions.  For Regression (single-class) models, the 1st and only
   *  Vec is the prediction value.  The result is in the DKV; caller is
   *  responsible for deleting.
   *
   * @param fr frame which should be scored
   * @return A new frame containing a predicted values. For classification it
   *         contains a column with prediction and distribution for all
   *         response classes. For regression it contains only one column with
   *         predicted values.
   * @throws IllegalArgumentException
   */
  public Frame score(Frame fr, String destination_key) throws IllegalArgumentException {
    return score(fr, destination_key, null, true);
  }

  public Frame score(Frame fr, String destination_key, Job j) throws IllegalArgumentException {
    return score(fr, destination_key, j, true);
  }

  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics) throws IllegalArgumentException {
    Frame adaptFr = new Frame(fr);
    computeMetrics = computeMetrics && (!isSupervised() || (adaptFr.vec(_output.responseName()) != null && !adaptFr.vec(_output.responseName()).isBad()));
    String[] msg = adaptTestForTrain(adaptFr,true, computeMetrics);   // Adapt
    if (msg.length > 0) {
      for (String s : msg)
        Log.warn(s);
    }
    Frame output = predictScoreImpl(fr, adaptFr, destination_key, j, computeMetrics); // Predict & Score
    // Log modest confusion matrices
    Vec predicted = output.vecs()[0]; // Modeled/predicted response
    String mdomain[] = predicted.domain(); // Domain of predictions (union of test and train)

    // Output is in the model's domain, but needs to be mapped to the scored
    // dataset's domain.
    if(_output.isClassifier() && computeMetrics) {
      /*
      if (false) {
        assert(mdomain != null); // label must be categorical
        ModelMetrics mm = ModelMetrics.getFromDKV(this,fr);
        ConfusionMatrix cm = mm.cm();
        if (cm != null && cm._domain != null) //don't print table for regression
          if( cm._cm.length < _parms._max_confusion_matrix_size ) {  // Print size limitation
            Log.info(cm.table().toString(1));
          }
        if (mm.hr() != null) {
          Log.info(getHitRatioTable(mm.hr()));
        }
      }
      */
      Vec actual = fr.vec(_output.responseName());
      if( actual != null ) {  // Predict does not have an actual, scoring does
        String sdomain[] = actual.domain(); // Scored/test domain; can be null
        if (sdomain != null && mdomain != sdomain && !Arrays.equals(mdomain, sdomain))
          output.replace(0, new CategoricalWrappedVec(actual.group().addVec(), actual._rowLayout, sdomain, predicted._key));
      }
    }
    cleanup_adapt(adaptFr, fr);
    return output;
  }

  /**
   * Compute the deviances for each observation
   * @param valid Validation Frame (must contain the response)
   * @param predictions Predictions made by the model
   * @param outputName Name of the output frame
   * @return Frame containing 1 column with the per-row deviances
   */
  public Frame computeDeviances(Frame valid, Frame predictions, String outputName) {
    assert (_parms._response_column!=null) : "response column can't be null";
    assert valid.find(_parms._response_column)>=0 : "validation frame must contain a response column";
    predictions.add(_parms._response_column, valid.vec(_parms._response_column));
    if (valid.find(_parms._weights_column)>=0)
      predictions.add(_parms._weights_column, valid.vec(_parms._weights_column));
    final int respIdx=predictions.find(_parms._response_column);
    final int weightIdx=predictions.find(_parms._weights_column);

    final Distribution myDist = _dist == null ? null : IcedUtils.deepCopy(_dist);
    if (myDist != null && myDist.distribution == DistributionFamily.huber) {
      myDist.setHuberDelta(hex.ModelMetricsRegression.computeHuberDelta(
              valid.vec(_parms._response_column), //actual
              predictions.vec(0), //predictions
              valid.vec(_parms._weights_column), //weight
              _parms._huber_alpha));
    }
    return new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] nc) {
        Chunk weight = weightIdx>=0 ? cs[weightIdx] : new C0DChunk(1, cs[0]._len);
        Chunk response = cs[respIdx];
        for (int i=0;i<cs[0]._len;++i) {
          double w=weight.atd(i);
          double y=response.atd(i);
          if (_output.nclasses()==1) { //regression - deviance
            double f=cs[0].atd(i);
            if (myDist!=null && myDist.distribution == DistributionFamily.huber) {
              nc[0].addNum(myDist.deviance(w, y, f)); //use above custom huber delta for this dataset
            }
            else {
              nc[0].addNum(deviance(w, y, f));
            }
          } else {
            int iact=(int)y;
            double err = iact < _output.nclasses() ? 1-cs[1+iact].atd(i) : 1;
            nc[0].addNum(w*MathUtils.logloss(err));
          }
        }
      }
    }.doAll(Vec.T_NUM, predictions).outputFrame(Key.<Frame>make(outputName), new String[]{"deviance"}, null);
  }

  // Remove temp keys.  TODO: Really should use Scope but Scope does not
  // currently allow nested-key-keepers.
  static protected void cleanup_adapt( Frame adaptFr, Frame fr ) {
    Key[] keys = adaptFr.keys();
    for( int i=0; i<keys.length; i++ )
      if( fr.find(keys[i]) == -1 ) //only delete vecs that aren't shared
        keys[i].remove();
    DKV.remove(adaptFr._key); //delete the frame header
  }

  protected String [] makeScoringNames(){
    final int nc = _output.nclasses();
    final int ncols = nc==1?1:nc+1; // Regression has 1 predict col; classification also has class distribution
    String [] names = new String[ncols];
    names[0] = "predict";
    for(int i = 1; i < names.length; ++i) {
      names[i] = _output.classNames()[i - 1];
      // turn integer class labels such as 0, 1, etc. into p0, p1, etc.
      try {
        Integer.valueOf(names[i]);
        names[i] = "p" + names[i];
      } catch (Throwable t) {
        // do nothing, non-integer names are fine already
      }
    }
    return names;
  }

  /** Allow subclasses to define their own BigScore class. */
  protected BigScore makeBigScoreTask(String[][] domains, String[] names , Frame adaptFrm, boolean computeMetrics, boolean makePrediction, Job j) {
    return new BigScore(domains[0],
                        names != null ? names.length : 0,
                        adaptFrm.means(),
                        _output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,
                        computeMetrics,
                        makePrediction,
                        j);
        //.doAll(names.length, Vec.T_NUM, adaptFrm);
  }

  /** Score an already adapted frame.  Returns a new Frame with new result
   *  vectors, all in the DKV.  Caller responsible for deleting.  Input is
   *  already adapted to the Model's domain, so the output is also.  Also
   *  computes the metrics for this frame.
   *
   * @param adaptFrm Already adapted frame
   * @param computeMetrics
   * @return A Frame containing the prediction column, and class distribution
   */
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics) {
    // Build up the names & domains.
    String[] names = makeScoringNames();
    String[][] domains = new String[names.length][];
    domains[0] = names.length == 1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();

    // Score the dataset, building the class distribution & predictions
    BigScore bs = makeBigScoreTask(domains, names, adaptFrm, computeMetrics, true, j).doAll(names.length, Vec.T_NUM, adaptFrm);

    if (computeMetrics)
      bs._mb.makeModelMetrics(this, fr, adaptFrm, bs.outputFrame());
    return bs.outputFrame(Key.<Frame>make(destination_key), names, domains);
  }


  /** Score an already adapted frame.  Returns a MetricBuilder that can be used to make a model metrics.
   * @param adaptFrm Already adapted frame
   * @return MetricBuilder
   */
  protected ModelMetrics.MetricBuilder scoreMetrics(Frame adaptFrm) {
    final boolean computeMetrics = (!isSupervised() || (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad()));
    // Build up the names & domains.
    //String[] names = makeScoringNames();
    String[][] domains = new String[1][];
    domains[0] = _output.nclasses() == 1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();

    // Score the dataset, building the class distribution & predictions
    BigScore bs = makeBigScoreTask(domains, null, adaptFrm, computeMetrics, false, null).doAll(adaptFrm);
    return bs._mb;
  }

  protected class BigScore extends MRTask<BigScore> {
    final protected String[] _domain; // Prediction domain; union of test and train classes
    final protected int _npredcols;  // Number of columns in prediction; nclasses+1 - can be less than the prediction domain
    public ModelMetrics.MetricBuilder _mb;
    final double[] _mean;  // Column means of test frame
    final public boolean _computeMetrics;  // Column means of test frame
    final public boolean _hasWeights;
    final public boolean _makePreds;
    final public Job _j;

    public BigScore( String[] domain, int ncols, double[] mean, boolean testHasWeights, boolean computeMetrics, boolean makePreds, Job j) {
      _j = j;
      _domain = domain; _npredcols = ncols; _mean = mean; _computeMetrics = computeMetrics; _makePreds = makePreds;
      if(_output._hasWeights && _computeMetrics && !testHasWeights)
        throw new IllegalArgumentException("Missing weights when computing validation metrics.");
      _hasWeights = testHasWeights;
    }

    @Override public void map( Chunk chks[], NewChunk cpreds[] ) {
      if (isCancelled() || _j != null && _j.stop_requested()) return;
      Chunk weightsChunk = _hasWeights && _computeMetrics ? chks[_output.weightsIdx()] : null;
      Chunk offsetChunk = _output.hasOffset() ? chks[_output.offsetIdx()] : null;
      Chunk responseChunk = null;
      double [] tmp = new double[_output.nfeatures()];
      float [] actual = null;
      _mb = Model.this.makeMetricBuilder(_domain);
      if (_computeMetrics) {
        if (isSupervised()) {
          actual = new float[1];
          responseChunk = chks[_output.responseIdx()];
        } else
          actual = new float[chks.length];
      }
      double[] preds = _mb._work;  // Sized for the union of test and train classes
      int len = chks[0]._len;
      for (int row = 0; row < len; row++) {
        double weight = weightsChunk!=null?weightsChunk.atd(row):1;
        if (weight == 0) {
          if (_makePreds) {
            for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
              cpreds[c].addNum(0);
          }
          continue;
        }
        double offset = offsetChunk!=null?offsetChunk.atd(row):0;
        double [] p = score0(chks, weight, offset, row, tmp, preds);
        if (_computeMetrics) {
          if(isSupervised()) {
            actual[0] = (float)responseChunk.atd(row);
          } else {
            for(int i = 0; i < actual.length; ++i)
              actual[i] = (float)data(chks,row,i);
          }
          _mb.perRow(preds, actual, weight, offset, Model.this);
        }
        if (_makePreds) {
          for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
            cpreds[c].addNum(p[c]);
        }
      }
      if ( _j != null) _j.update(1);
    }
    @Override public void reduce( BigScore bs ) { if(_mb != null )_mb.reduce(bs._mb); }
    @Override protected void postGlobal() { if(_mb != null)_mb.postGlobal(); }

    @Override protected void setupLocal() { setupBigScoreLocal(); }
    @Override protected void closeLocal() { closeBigScoreLocal(); }
  }

  protected void setupBigScoreLocal() {}
  protected void closeBigScoreLocal() {}

  // OVerride this if your model needs data preprocessing (on the fly standardization, NA handling)
  protected double data(Chunk[] chks, int row, int col) {
    return chks[col].atd(row);
  }


  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  public double[] score0( Chunk chks[], int row_in_chunk, double[] tmp, double[] preds ) {
    return score0(chks, 1, 0, row_in_chunk, tmp, preds);
  }

  public double[] score0( Chunk chks[], double weight, double offset, int row_in_chunk, double[] tmp, double[] preds ) {
    assert(_output.nfeatures() == tmp.length);
    for( int i=0; i< tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
    double [] scored = score0(tmp, preds, weight, offset);
    if(isSupervised()) {
      // Correct probabilities obtained from training on oversampled data back to original distribution
      // C.f. http://gking.harvard.edu/files/0s.pdf Eq.(27)
      if( _output.isClassifier()) {
        if (_parms._balance_classes)
          GenModel.correctProbabilities(scored, _output._priorClassDist, _output._modelClassDist);
        //assign label at the very end (after potentially correcting probabilities)
        scored[0] = hex.genmodel.GenModel.getPrediction(scored, _output._priorClassDist, tmp, defaultThreshold());
      }
    }
    return scored;
  }

  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  */
  protected abstract double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]);

  /**Override scoring logic for models that handle weight/offset**/
  protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/], double weight, double offset) {
    assert (weight == 1 && offset == 0) : "Override this method for non-trivial weight/offset!";
    return score0(data, preds);
  }
  // Version where the user has just ponied-up an array of data to be scored.
  // Data must be in proper order.  Handy for JUnit tests.
  public double score(double[] data){ return ArrayUtils.maxIndex(score0(data, new double[_output.nclasses()]));  }

  @Override protected Futures remove_impl( Futures fs ) {
    if (_output._model_metrics != null)
      for( Key k : _output._model_metrics )
        k.remove(fs);
    cleanUp(_toDelete);
    return super.remove_impl(fs);
  }

  /** Write out K/V pairs, in this case model metrics. */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    if (_output._model_metrics != null)
      for( Key k : _output._model_metrics )
        ab.putKey(k);
    return super.writeAll_impl(ab);
  }
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    if (_output._model_metrics != null)
      for( Key k : _output._model_metrics )
        ab.getKey(k,fs);        // Load model metrics
    return super.readAll_impl(ab,fs);
  }

  @Override protected long checksum_impl() {
    return _parms.checksum_impl() * _output.checksum_impl();
  }

  /**
   * Override this in models that support serialization into the MOJO format.
   * @return a class that inherits from ModelMojoWriter
   */
  public ModelMojoWriter getMojo() {
    throw H2O.unimpl("MOJO format is not available for " + _parms.fullName() + " models.");
  }



  // ==========================================================================
  /** Return a String which is a valid Java program representing a class that
   *  implements the Model.  The Java is of the form:
   *  <pre>
   *    class UUIDxxxxModel {
   *      public static final String NAMES[] = { ....column names... }
   *      public static final String DOMAINS[][] = { ....domain names... }
   *      // Pass in data in a double[], pre-aligned to the Model's requirements.
   *      // Jam predictions into the preds[] array; preds[0] is reserved for the
   *      // main prediction (class for classifiers or value for regression),
   *      // and remaining columns hold a probability distribution for classifiers.
   *      double[] predict( double data[], double preds[] );
   *      double[] map( HashMap &lt; String,Double &gt; row, double data[] );
   *      // Does the mapping lookup for every row, no allocation
   *      double[] predict( HashMap &lt; String,Double &gt; row, double data[], double preds[] );
   *      // Allocates a double[] for every row
   *      double[] predict( HashMap &lt; String,Double &gt; row, double preds[] );
   *      // Allocates a double[] and a double[] for every row
   *      double[] predict( HashMap &lt; String,Double &gt; row );
   *    }
   *  </pre>
   */
  public final String toJava(boolean preview, boolean verboseCode) {
    // 32k buffer by default
    ByteArrayOutputStream os = new ByteArrayOutputStream(Short.MAX_VALUE);
    // We do not need to close BAOS
    /* ignore returned stream */ toJava(os, preview, verboseCode);
    return os.toString();
  }

  public final SBPrintStream toJava(OutputStream os, boolean preview, boolean verboseCode) {
    if (preview /* && toJavaCheckTooBig() */) {
      os = new LineLimitOutputStreamWrapper(os, 1000);
    }
    return toJava(new SBPrintStream(os), preview, verboseCode);
  }

  protected SBPrintStream toJava(SBPrintStream sb, boolean isGeneratingPreview, boolean verboseCode) {
    CodeGeneratorPipeline fileCtx = new CodeGeneratorPipeline();  // preserve file context
    String modelName = JCodeGen.toJavaId(_key.toString());
    // HEADER
    sb.p("/*").nl();
    sb.p("  Licensed under the Apache License, Version 2.0").nl();
    sb.p("    http://www.apache.org/licenses/LICENSE-2.0.html").nl();
    sb.nl();
    sb.p("  AUTOGENERATED BY H2O at ").p(new DateTime().toString()).nl();
    sb.p("  ").p(H2O.ABV.projectVersion()).nl();
    sb.p("  ").nl();
    sb.p("  Standalone prediction code with sample test data for ").p(this.getClass().getSimpleName()).p(" named ").p(modelName)
        .nl();
    sb.nl();
    sb.p("  How to download, compile and execute:").nl();
    sb.p("      mkdir tmpdir").nl();
    sb.p("      cd tmpdir").nl();
    sb.p("      curl http:/").p(H2O.SELF.toString()).p("/3/h2o-genmodel.jar > h2o-genmodel.jar").nl();
    sb.p("      curl http:/").p(H2O.SELF.toString()).p("/3/Models.java/").pobj(_key).p(" > ").p(modelName).p(".java").nl();
    sb.p("      javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m ").p(modelName).p(".java").nl();
    // Intentionally disabled since there is no main method in generated code
    // sb.p("//     java -cp h2o-genmodel.jar:. -Xmx2g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m ").p(modelName).nl();
    sb.nl();
    sb.p("     (Note:  Try java argument -XX:+PrintCompilation to show runtime JIT compiler behavior.)").nl();
    if (_parms._offset_column != null) {
      sb.nl();
      sb.nl();
      sb.nl();
      sb.p("  NOTE:  Java model export does not support offset_column.").nl();
      sb.nl();
      Log.warn("Java model export does not support offset_column.");
    }
    if (isGeneratingPreview && toJavaCheckTooBig()) {
      sb.nl();
      sb.nl();
      sb.nl();
      sb.p("  NOTE:  Java model is too large to preview, please download as shown above.").nl();
      sb.nl();
      return sb;
    }
    sb.p("*/").nl();
    sb.p("import java.util.Map;").nl();
    sb.p("import hex.genmodel.GenModel;").nl();
    sb.p("import hex.genmodel.annotations.ModelPojo;").nl();
    sb.nl();
    String algo = this.getClass().getSimpleName().toLowerCase().replace("model", "");
    sb.p("@ModelPojo(name=\"").p(modelName).p("\", algorithm=\"").p(algo).p("\")").nl();
    sb.p("public class ").p(modelName).p(" extends GenModel {").nl().ii(1);
    sb.ip("public hex.ModelCategory getModelCategory() { return hex.ModelCategory." + _output
        .getModelCategory() + "; }").nl();
    toJavaInit(sb, fileCtx).nl();
    toJavaNAMES(sb, fileCtx);
    toJavaNCLASSES(sb);
    toJavaDOMAINS(sb, fileCtx);
    toJavaPROB(sb);
    toJavaSuper(modelName, sb); //
    sb.p("  public String getUUID() { return Long.toString("+checksum()+"L); }").nl();
    toJavaPredict(sb, fileCtx, verboseCode);
    sb.p("}").nl().di(1);
    fileCtx.generate(sb); // Append file context
    sb.nl();
    return sb;
  }

  /** Generate implementation for super class. */
  protected SBPrintStream toJavaSuper(String modelName, SBPrintStream sb) {
    return sb.nl().ip("public " + modelName + "() { super(NAMES,DOMAINS); }").nl();
  }

  private SBPrintStream toJavaNAMES(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    final String modelName = JCodeGen.toJavaId(_key.toString());
    final String namesHolderClassName = "NamesHolder_"+modelName;
    sb.i().p("// ").p("Names of columns used by model.").nl();
    sb.i().p("public static final String[] NAMES = "+namesHolderClassName+".VALUES;").nl();
    // Generate class which fills the names into array
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.i().p("// The class representing training column names").nl();
        JCodeGen.toClassWithArray(out, null, namesHolderClassName,
                                  Arrays.copyOf(_output._names, _output.nfeatures()));
      }
    });

    return sb;
  }

  protected SBPrintStream toJavaNCLASSES(SBPrintStream sb ) {
    return _output.isClassifier() ? JCodeGen.toStaticVar(sb, "NCLASSES",
                                                         _output.nclasses(),
                                                         "Number of output classes included in training data response column.")
                                  : sb;
  }

  private SBPrintStream toJavaDOMAINS(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    String modelName = JCodeGen.toJavaId(_key.toString());
    sb.nl();
    sb.ip("// Column domains. The last array contains domain of response column.").nl();
    sb.ip("public static final String[][] DOMAINS = new String[][] {").nl();
    String [][] domains = scoringDomains();
    for (int i=0; i< domains.length; i++) {
      final int idx = i;
      final String[] dom = domains[i];
      final String colInfoClazz = modelName+"_ColInfo_"+i;
      sb.i(1).p("/* ").p(_output._names[i]).p(" */ ");
      if (dom != null) sb.p(colInfoClazz).p(".VALUES"); else sb.p("null");
      if (i!=domains.length-1) sb.p(',');
      sb.nl();
      // Right now do not generate the class representing column
      // since it does not hold any interesting information except String array holding domain
      if (dom != null) {
        fileCtx.add(new CodeGenerator() {
                      @Override
                      public void generate(JCodeSB out) {
                        out.ip("// The class representing column ").p(_output._names[idx]).nl();
                        JCodeGen.toClassWithArray(out, null, colInfoClazz, dom);
                      }
                    }
        );
      }
    }
    return sb.ip("};").nl();
  }

  protected SBPrintStream toJavaPROB(SBPrintStream sb) {
    if(isSupervised()) {
      JCodeGen.toStaticVar(sb, "PRIOR_CLASS_DISTRIB", _output._priorClassDist, "Prior class distribution");
      JCodeGen.toStaticVar(sb, "MODEL_CLASS_DISTRIB", _output._modelClassDist, "Class distribution used for model building");
    }
    return sb;
  }

  protected boolean toJavaCheckTooBig() {
    Log.warn("toJavaCheckTooBig must be overridden for this model type to render it in the browser");
    return true;
  }

  // Override in subclasses to provide some top-level model-specific goodness
  protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileContext) { return sb; }

  // Override in subclasses to provide some inside 'predict' call goodness
  // Method returns code which should be appended into generated top level class after
  // predict method.
  protected void toJavaPredictBody(SBPrintStream body,
                                   CodeGeneratorPipeline classCtx,
                                   CodeGeneratorPipeline fileCtx,
                                   boolean verboseCode) {
    throw new IllegalArgumentException("This model type does not support conversion to Java");
  }

  // Wrapper around the main predict call, including the signature and return value
  private SBPrintStream toJavaPredict(SBPrintStream ccsb,
                                      CodeGeneratorPipeline fileCtx,
                                      boolean verboseCode) { // ccsb = classContext
    ccsb.nl();
    ccsb.ip("// Pass in data in a double[], pre-aligned to the Model's requirements.").nl();
    ccsb.ip("// Jam predictions into the preds[] array; preds[0] is reserved for the").nl();
    ccsb.ip("// main prediction (class for classifiers or value for regression),").nl();
    ccsb.ip("// and remaining columns hold a probability distribution for classifiers.").nl();
    ccsb.ip("public final double[] score0( double[] data, double[] preds ) {").nl();
    CodeGeneratorPipeline classCtx = new CodeGeneratorPipeline(); //new SB().ii(1);
    toJavaPredictBody(ccsb.ii(1), classCtx, fileCtx, verboseCode);
    ccsb.ip("return preds;").nl();
    ccsb.di(1).ip("}").nl();
    // Output class context
    classCtx.generate(ccsb.ii(1));
    ccsb.di(1);
    return ccsb;
  }

  // Convenience method for testing: build Java, convert it to a class &
  // execute it: compare the results of the new class's (JIT'd) scoring with
  // the built-in (interpreted) scoring on this dataset.  Returns true if all
  // is well, false is there are any mismatches.  Throws if there is any error
  // (typically an AssertionError or unable to compile the POJO).
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon) {
    return testJavaScoring(data, model_predictions, rel_epsilon, 1e-15, 0.1);
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon, double abs_epsilon) {
    return testJavaScoring(data, model_predictions, rel_epsilon, abs_epsilon, 0.1);
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon, double abs_epsilon, double fraction) {
    ModelBuilder mb = ModelBuilder.make(_parms.algoName().toLowerCase(), null, null);
    boolean havePojo = mb.havePojo();
    boolean haveMojo = mb.haveMojo();

    Random rnd = RandomUtils.getRNG(data.byteSize());
    assert data.numRows() == model_predictions.numRows();
    Frame fr = new Frame(data);
    boolean computeMetrics = data.vec(_output.responseName()) != null && !data.vec(_output.responseName()).isBad();
    try {
      String[] warns = adaptTestForTrain(fr,true, computeMetrics);
      if( warns.length > 0 )
        System.err.println(Arrays.toString(warns));

      // Output is in the model's domain, but needs to be mapped to the scored
      // dataset's domain.
      int[] omap = null;
      if( _output.isClassifier() ) {
        Vec actual = fr.vec(_output.responseName());
        String[] sdomain = actual == null ? null : actual.domain(); // Scored/test domain; can be null
        String[] mdomain = model_predictions.vec(0).domain(); // Domain of predictions (union of test and train)
        if( sdomain != null && !Arrays.equals(mdomain, sdomain)) {
          omap = CategoricalWrappedVec.computeMap(mdomain,sdomain); // Map from model-domain to scoring-domain
        }
      }

      String modelName = JCodeGen.toJavaId(_key.toString());
      boolean preview = false;
      GenModel genmodel = null;
      Vec[] dvecs = fr.vecs();
      Vec[] pvecs = model_predictions.vecs();
      double[] features = null;
      int num_errors = 0;
      int num_total = 0;

      // First try internal POJO via fast double[] API
      if (havePojo) {
        try {
          String java_text = toJava(preview, true);
          Class clz = JCodeGen.compile(modelName,java_text);
          genmodel = (GenModel)clz.newInstance();
        } catch (Exception e) {
          e.printStackTrace();
          throw H2O.fail("Internal POJO compilation failed",e);
        }

        features = MemoryManager.malloc8d(genmodel._names.length);
        double[] predictions = MemoryManager.malloc8d(genmodel.nclasses() + 1);

        // Compare predictions, counting mis-predicts
        for (int row=0; row<fr.numRows(); row++) { // For all rows, single-threaded
          if (rnd.nextDouble() >= fraction) continue;
          num_total++;

          // Native Java API
          for (int col = 0; col < features.length; col++) // Build feature set
            features[col] = dvecs[col].at(row);
          genmodel.score0(features, predictions);            // POJO predictions
          for (int col = _output.isClassifier() ? 1 : 0; col < pvecs.length; col++) { // Compare predictions
            double d = pvecs[col].at(row);                  // Load internal scoring predictions
            if (col == 0 && omap != null) d = omap[(int) d];  // map categorical response to scoring domain
            if (!MathUtils.compare(predictions[col], d, abs_epsilon, rel_epsilon)) {
              if (num_errors++ < 10)
                System.err.println("Predictions mismatch, row " + row + ", col " + model_predictions._names[col] + ", internal prediction=" + d + ", POJO prediction=" + predictions[col]);
              break;
            }
          }
        }
      }

      // EasyPredict API with POJO and/or MOJO
      for (int i = 0; i < 2; ++i) {
        if (i == 0 && !havePojo) continue;
        if (i == 1 && !haveMojo) continue;
        if (i == 1) {  // MOJO
          final String filename = modelName + ".zip";
          StreamingSchema ss = new StreamingSchema(getMojo(), filename);
          try {
            FileOutputStream os = new FileOutputStream(ss.getFilename());
            ss.getStreamWriter().writeTo(os);
            os.close();
            genmodel = MojoModel.load(filename);
            features = MemoryManager.malloc8d(genmodel._names.length);
          } catch (IOException e1) {
            e1.printStackTrace();
            throw H2O.fail("Internal MOJO loading failed", e1);
          } finally {
            boolean deleted = new File(filename).delete();
            if (!deleted) Log.warn("Failed to delete the file");
          }
        }

        EasyPredictModelWrapper epmw = new EasyPredictModelWrapper(
                new EasyPredictModelWrapper.Config().setModel(genmodel).setConvertUnknownCategoricalLevelsToNa(true)
        );
        RowData rowData = new RowData();
        BufferedString bStr = new BufferedString();
        for (int row = 0; row < fr.numRows(); row++) { // For all rows, single-threaded
          if (rnd.nextDouble() >= fraction) continue;
          if (genmodel.getModelCategory() == ModelCategory.AutoEncoder) continue;

          // Generate input row
          for (int col = 0; col < features.length; col++) {
            if (dvecs[col].isString()) {
              rowData.put(genmodel._names[col], dvecs[col].atStr(bStr, row).toString());
            } else {
              double val = dvecs[col].at(row);
              rowData.put(
                  genmodel._names[col],
                  genmodel._domains[col] == null ? (Double) val
                      : Double.isNaN(val) ? val  // missing categorical values are kept as NaN, the score0 logic passes it on to bitSetContains()
                      : (int) val < genmodel._domains[col].length ? genmodel._domains[col][(int) val] : "UnknownLevel"); //unseen levels are treated as such
            }
          }

          // Make a prediction
          AbstractPrediction p;
          try {
            p = epmw.predict(rowData);
          } catch (PredictException e) {
            num_errors++;
            if (num_errors < 20) {
              System.err.println("EasyPredict threw an exception when predicting row " + rowData);
              e.printStackTrace();
            }
            continue;
          }

          // Convert model predictions and "internal" predictions into the same shape
          double[] expected_preds = new double[pvecs.length];
          double[] actual_preds = new double[pvecs.length];
          for (int col = 0; col < pvecs.length; col++) { // Compare predictions
            double d = pvecs[col].at(row); // Load internal scoring predictions
            if (col == 0 && omap != null) d = omap[(int) d]; // map categorical response to scoring domain
            double d2 = Double.NaN;
            switch (genmodel.getModelCategory()) {
              case Clustering:
                d2 = ((ClusteringModelPrediction) p).cluster;
                break;
              case Regression:
                d2 = ((RegressionModelPrediction) p).value;
                break;
              case Binomial:
                BinomialModelPrediction bmp = (BinomialModelPrediction) p;
                d2 = (col == 0) ? bmp.labelIndex : bmp.classProbabilities[col - 1];
                break;
              case Multinomial:
                MultinomialModelPrediction mmp = (MultinomialModelPrediction) p;
                d2 = (col == 0) ? mmp.labelIndex : mmp.classProbabilities[col - 1];
                break;
              case DimReduction:
                d2 = ((DimReductionModelPrediction) p).dimensions[col];
                break;
            }
            expected_preds[col] = d;
            actual_preds[col] = d2;
          }

          // Verify the correctness of the prediction
          num_total++;
          for (int col = genmodel.isClassifier() ? 1 : 0; col < pvecs.length; col++) {
            if (!MathUtils.compare(actual_preds[col], expected_preds[col], abs_epsilon, rel_epsilon)) {
              num_errors++;
              if (num_errors < 20) {
                System.err.println( (i == 0 ? "POJO" : "MOJO") + " EasyPredict Predictions mismatch for row " + rowData);
                System.err.println("  Expected predictions: " + Arrays.toString(expected_preds));
                System.err.println("  Actual predictions:   " + Arrays.toString(actual_preds));
              }
              break;
            }
          }
        }
      }
      if (num_errors != 0)
        System.err.println("Number of errors: " + num_errors + (num_errors > 20 ? " (only first 20 are shown)": "") +
                           " out of " + num_total + " rows tested.");
      return num_errors == 0;
    } finally {
      cleanup_adapt(fr, data);  // Remove temp keys.
    }
  }

  public void deleteCrossValidationModels( ) {
    if (_output._cross_validation_models != null) {
      for (Key k : _output._cross_validation_models) {
        Model m = DKV.getGet(k);
        if (m!=null) m.delete(); //delete all subparts
      }
    }
  }

  @Override public String toString() {
    return _output.toString();
  }

  /** Model stream writer - output Java code representation of model. */
  public class JavaModelStreamWriter extends StreamWriter {
    /** Show only preview */
    private final boolean preview;

    public JavaModelStreamWriter(boolean preview) {
      this.preview = preview;
    }

    @Override
    public void writeTo(OutputStream os) {
      toJava(os, preview, true);
    }
  }

  @Override public Class<KeyV3.ModelKeyV3> makeSchema() { return KeyV3.ModelKeyV3.class; }

  public static Frame makeInteractions(Frame fr, boolean valid, InteractionPair[] interactions, boolean useAllFactorLevels, boolean skipMissing, boolean standardize) {
    Vec anyTrainVec = fr.anyVec();
    Vec[] interactionVecs = new Vec[interactions.length];
    String[] interactionNames  = new String[interactions.length];
    int idx = 0;
    for (InteractionPair ip : interactions) {
      interactionNames[idx] = fr.name(ip._v1) + "_" + fr.name(ip._v2);
      InteractionWrappedVec iwv =new InteractionWrappedVec(anyTrainVec.group().addVec(), anyTrainVec._rowLayout, ip._v1Enums, ip._v2Enums, useAllFactorLevels, skipMissing, standardize, fr.vec(ip._v1)._key, fr.vec(ip._v2)._key);
//      if(!valid) ip.setDomain(iwv.domain());
      interactionVecs[idx++] = iwv;
    }
    return new Frame(interactionNames, interactionVecs);
  }

  public static InteractionWrappedVec[] makeInteractions(Frame fr, InteractionPair[] interactions, boolean useAllFactorLevels, boolean skipMissing, boolean standardize) {
    Vec anyTrainVec = fr.anyVec();
    InteractionWrappedVec[] interactionVecs = new InteractionWrappedVec[interactions.length];
    int idx = 0;
    for (InteractionPair ip : interactions)
      interactionVecs[idx++] = new InteractionWrappedVec(anyTrainVec.group().addVec(), anyTrainVec._rowLayout, ip._v1Enums, ip._v2Enums, useAllFactorLevels, skipMissing, standardize, fr.vec(ip._v1)._key, fr.vec(ip._v2)._key);
    return interactionVecs;
  }

  public static InteractionWrappedVec makeInteraction(Frame fr, InteractionPair ip, boolean useAllFactorLevels, boolean skipMissing, boolean standardize) {
    Vec anyVec = fr.anyVec();
    return new InteractionWrappedVec(anyVec.group().addVec(), anyVec._rowLayout, ip._v1Enums, ip._v2Enums, useAllFactorLevels, skipMissing, standardize, fr.vec(ip._v1)._key, fr.vec(ip._v2)._key);
  }

  /**
   * This class represents a pair of interacting columns plus some additional data
   * about specific enums to be interacted when the vecs are categorical. The question
   * naturally arises why not just use something like an ArrayList of int[2] (as is done,
   * for example, in the Interaction/CreateInteraction classes) and the answer essentially
   * boils down a desire to specify these specific levels.
   *
   * Another difference with the CreateInteractions class:
   *  1. do not interact on NA (someLvl_NA  and NA_somLvl are actual NAs)
   *     this does not appear here, but in the InteractionWrappedVec class
   *  TODO: refactor the CreateInteractions to be useful here and in InteractionWrappedVec
   */
  public static class InteractionPair extends Iced {
    public int vecIdx;
    private int _v1,_v2;

    private String[] _domain; // not null for enum-enum interactions
    private String[] _v1Enums;
    private String[] _v2Enums;
    private int _hash;
    private InteractionPair() {}
    private InteractionPair(int v1, int v2, String[] v1Enums, String[] v2Enums) {
      _v1=v1;_v2=v2;_v1Enums=v1Enums;_v2Enums=v2Enums;
      // hash is column ints; Item 9 p.47 of Effective Java
      _hash=17;
      _hash = 31*_hash + _v1;
      _hash = 31*_hash + _v2;
      if( _v1Enums==null ) _hash = 31*_hash;
      else
        for( String s:_v1Enums ) _hash = 31*_hash + s.hashCode();
      if( _v2Enums==null ) _hash = 31*_hash;
      else
        for( String s:_v2Enums ) _hash = 31*_hash + s.hashCode();
    }

    /**
     * Generate all pairwise combinations of ints in the range [from,to).
     * @param from Start index
     * @param to End index (exclusive)
     * @return An array of interaction pairs.
     */
    public static InteractionPair[] generatePairwiseInteractions(int from, int to) {
      if( 1==(to-from) )
        throw new IllegalArgumentException("Illegal range of values, must be greater than a single value. Got: " + from + "<" + to);
      InteractionPair[] res = new InteractionPair[ ((to-from-1)*(to-from)) >> 1];  // n*(n+1) / 2
      int idx=0;
      for(int i=from;i<to;++i)
        for(int j=i+1;j<to;++j)
          res[idx++] = new InteractionPair(i,j,null,null);
      return res;
    }

    /**
     * Generate all pairwise combinations of the arguments.
     * @param indexes An array of column indices.
     * @return An array of interaction pairs
     */
    public static InteractionPair[] generatePairwiseInteractionsFromList(int... indexes) {
      if( null==indexes ) return null;
      if( indexes.length < 2 ) {
        if( indexes.length==1 && indexes[0]==-1 ) return null;
        throw new IllegalArgumentException("Must supply 2 or more columns.");
      }
      InteractionPair[] res = new InteractionPair[ (indexes.length-1)*(indexes.length)>>1]; // n*(n+1) / 2
      int idx=0;
      for(int i=0;i<indexes.length;++i)
        for(int j=i+1;j<indexes.length;++j)
          res[idx++] = new InteractionPair(indexes[i],indexes[j],null,null);
      return res;
    }

    /**
     * Set the domain; computed in an MRTask over the two categorical vectors that make
     * up this interaction pair
     * @param dom The domain retrieved by the CombineDomainTask in InteractionWrappedVec
     */
    public void setDomain(String[] dom) { _domain=dom; }

    /**
     * Check to see if any of the vecIdx values is the desired value.
     */
    public static int isInteraction(int i, InteractionPair[] ips) {
      int idx = 0;
      for (InteractionPair ip: ips) {
        if (i == ip.vecIdx) return idx;
        else               idx++;
      }
      return -1;
    }

    // parser stuff
    private int _p;
    private String _str;
    public static InteractionPair[] read(String interaction) {
      String[] interactions=interaction.split("\n");
      HashSet<InteractionPair> res = new HashSet<>();
      for (String i: interactions)
        res.addAll(new InteractionPair().parse(i));
      return res.toArray(new InteractionPair[res.size()]);
    }

    private HashSet<InteractionPair> parse(String i) { // v1[E8,E9]:v2,v3,v8,v90,v128[E1,E22]
      _p=0;
      _str=i;
      HashSet<InteractionPair> res=new HashSet<>();
      int v1 = parseNum();    // parse the first int
      String[] v1Enums=parseEnums();  // shared
      if( i.charAt(_p)!=':' || _p>=i.length() ) throw new IllegalArgumentException("Error");
      while( _p++<i.length() ) {
        int v2=parseNum();
        String[] v2Enums=parseEnums();
        if( v1 == v2 ) continue; // don't interact on self!
        res.add(new InteractionPair(v1,v2,v1Enums,v2Enums));
      }
      return res;
    }

    private int parseNum() {
      int start=_p++;
      while( _p<_str.length() && '0' <= _str.charAt(_p) && _str.charAt(_p) <= '9') _p++;
      try {
        return Integer.valueOf(_str.substring(start,_p));
      } catch(NumberFormatException ex) {
        throw new IllegalArgumentException("No number could be parsed. Interaction: " + _str);
      }
    }

    private String[] parseEnums() {
      if( _p>=_str.length() || _str.charAt(_p)!='[' ) return null;
      ArrayList<String> enums = new ArrayList<>();
      while( _str.charAt(_p++)!=']' ) {
        int start=_p++;
        while(_str.charAt(_p)!=',' && _str.charAt(_p)!=']') _p++;
        enums.add(_str.substring(start,_p));
      }
      return enums.toArray(new String[enums.size()]);
    }

    @Override public int hashCode() { return _hash; }
    @Override public String toString() { return _v1+(_v1Enums==null?"":Arrays.toString(_v1Enums))+":"+_v2+(_v2Enums==null?"":Arrays.toString(_v2Enums)); }
    @Override public boolean equals( Object o ) {
      boolean res = o instanceof InteractionPair;
      if (res) {
        InteractionPair ip = (InteractionPair) o;
        return (_v1 == ip._v1) && (_v2 == ip._v2) && Arrays.equals(_v1Enums, ip._v1Enums) && Arrays.equals(_v2Enums, ip._v2Enums);
      }
      return false;
    }
  }
}
