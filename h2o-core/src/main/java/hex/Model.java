package hex;

import hex.genmodel.*;
import hex.genmodel.algos.glrm.GlrmMojoModel;
import hex.genmodel.algos.tree.ContributionComposer;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.*;
import hex.genmodel.utils.DistributionFamily;
import hex.quantile.QuantileModel;
import org.joda.time.DateTime;
import water.*;
import water.api.ModelsHandler;
import water.api.StreamWriteOption;
import water.api.StreamWriter;
import water.api.StreamingSchema;
import water.api.schemas3.KeyV3;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.*;
import water.parser.BufferedString;
import water.persist.Persist;
import water.udf.CFuncRef;
import water.util.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static water.util.FrameUtils.categoricalEncoder;
import static water.util.FrameUtils.cleanUp;

/**
 * A Model models reality (hopefully).
 * A model can be used to 'score' a row (make a prediction), or a collection of
 * rows on any compatible dataset - meaning the row has all the columns with the
 * same names as used to build the mode and any categorical columns can
 * be adapted.
 */
public abstract class Model<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output>
        extends DefaultPojoWriter<M>
        implements StreamWriter {

  public static final String EVAL_AUTO_PARAMS_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "algos.evaluate_auto_model_parameters";

  public P _parms;   // TODO: move things around so that this can be protected
  public P _input_parms;
  public O _output;  // TODO: move things around so that this can be protected
  public String[] _warnings = new String[0];  // warning associated with model building
  public transient String[] _warningsP;     // warnings associated with prediction only (transient, not persisted)
  public Distribution _dist;
  protected ScoringInfo[] scoringInfo;
  public IcedHashMap<Key, String> _toDelete = new IcedHashMap<>();
  public boolean evalAutoParamsEnabled;


  public static Model[] fetchAll() {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, Model.class) && !Value.isSubclassOf(k._type, QuantileModel.class);
      }
    }).keys();

    Model[] models = new Model[modelKeys.length];
    for (int i = 0; i < modelKeys.length; i++) {
      Model model = ModelsHandler.getFromDKV("(none)", modelKeys[i]);
      models[i] = model;
    }

    return models;
  }

  /**
   * Whether to evaluate input parameters of value AUTO.
   */
  public static boolean evaluateAutoModelParameters() {
    return Boolean.parseBoolean(System.getProperty(EVAL_AUTO_PARAMS_ENABLED, "true"));
  }

  public void setInputParms(P _input_parms) {
    this._input_parms = _input_parms;
  }

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
    enum LeafNodeAssignmentType {Path, Node_ID}
    Frame scoreLeafNodeAssignment(Frame frame, LeafNodeAssignmentType type, Key<Frame> destination_key);
  }

  public interface FeatureFrequencies {
    Frame scoreFeatureFrequencies(Frame frame, Key<Frame> destination_key);
  }

  public interface StagedPredictions {
    Frame scoreStagedPredictions(Frame frame, Key<Frame> destination_key);
  }

  public interface UpdateAuxTreeWeights {
    UpdateAuxTreeWeightsReport updateAuxTreeWeights(Frame frame, String weightsColumn);

    class UpdateAuxTreeWeightsReport {
      public int[] _warn_trees;
      public int[] _warn_classes;
      public boolean hasWarnings() {
        return _warn_trees != null && _warn_trees.length > 0;
      }
    }
  }

  public interface Contributions {
    enum ContributionsOutputFormat {Original, Compact}

    class ContributionsOptions {
      public ContributionsOutputFormat _outputFormat = ContributionsOutputFormat.Original;
      public int _topN;
      public int _bottomN;
      public boolean _compareAbs;

      public ContributionsOptions setOutputFormat(ContributionsOutputFormat outputFormat) {
        _outputFormat = outputFormat;
        return this;
      }

      public ContributionsOptions setTopN(int topN) {
        _topN = topN;
        return this;
      }

      public ContributionsOptions setBottomN(int bottomN) {
        _bottomN = bottomN;
        return this;
      }

      public ContributionsOptions setCompareAbs(boolean compareAbs) {
        _compareAbs = compareAbs;
        return this;
      }

      public boolean isSortingRequired() {
        return _topN != 0 || _bottomN != 0;
      }
    }

    Frame scoreContributions(Frame frame, Key<Frame> destination_key);

    default Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
      return scoreContributions(frame, destination_key, j, new ContributionsOptions());
    }
    default Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options) {
      return scoreContributions(frame, destination_key);
    }

    default void composeScoreContributionTaskMetadata(final String[] names, final byte[] types, final String[][] domains, final String[] originalFrameNames, final Contributions.ContributionsOptions options) {
      final String[] contribNames = hex.genmodel.utils.ArrayUtils.append(originalFrameNames, "BiasTerm");

      final ContributionComposer contributionComposer = new ContributionComposer();
      int topNAdjusted = contributionComposer.checkAndAdjustInput(options._topN, originalFrameNames.length);
      int bottomNAdjusted = contributionComposer.checkAndAdjustInput(options._bottomN, originalFrameNames.length);

      int outputSize = Math.min((topNAdjusted+bottomNAdjusted)*2, originalFrameNames.length*2);

      for (int i = 0; i < outputSize; i+=2) {
        types[i] = Vec.T_CAT;
        domains[i] = Arrays.copyOf(contribNames, contribNames.length);
        domains[i+1] = null;
        types[i+1] = Vec.T_NUM;
      }

      int topFeatureIterator = 1;
      for (int i = 0; i < topNAdjusted*2; i+=2) {
        names[i] = "top_feature_" + topFeatureIterator;
        names[i+1] = "top_value_" + topFeatureIterator;
        topFeatureIterator++;
      }

      int bottomFeatureIterator = 1;
      for (int i = topNAdjusted*2; i < outputSize; i+=2) {
        names[i] = "bottom_feature_" + bottomFeatureIterator;
        names[i+1] = "bottom_value_" + bottomFeatureIterator;
        bottomFeatureIterator++;
      }

      names[outputSize] = "BiasTerm";
      types[outputSize] = Vec.T_NUM;
      domains[outputSize] = null;
    }
  }

  public interface RowToTreeAssignment {

    Frame rowToTreeAssignment(Frame frame, Key<Frame> destination_key, Job<Frame> j);
  }

  public interface ExemplarMembers {
    Frame scoreExemplarMembers(Key<Frame> destination_key, int exemplarIdx);
  }

  public interface GetMostImportantFeatures {
    String[] getMostImportantFeatures(int n);
  }

  public interface GetNTrees {
    int getNTrees();
  }

  /**
   * Default threshold for assigning class labels to the target class (for binomial models)
   * @return threshold in 0...1
   */
  public double defaultThreshold() {
    return _output.defaultThreshold();
  }
  
  public void resetThreshold(double value){
    _output.resetThreshold(value);
  }

  /**
   * @deprecated use {@link Output#defaultThreshold()} instead.
   */
  @Deprecated
  public static <O extends Model.Output> double defaultThreshold(O output) {
    return output.defaultThreshold();
  }

  public final boolean isSupervised() { return _output.isSupervised(); }

  public boolean havePojo() {
    if (_parms._preprocessors != null) return false; // TE processor not included to current POJO (see PUBDEV-8508 for potential fix)
    final String algoName = _parms.algoName();
    return ModelBuilder.getRegisteredBuilder(algoName)
            .map(ModelBuilder::havePojo)
            .orElseGet(() -> {
              Log.warn("Model Builder for algo = " + algoName + " is not registered. " +
                      "Unable to determine if Model has a POJO. Please override method havePojo().");
              return false;
            });
  }

  public boolean haveMojo() {
    if (_parms._preprocessors != null) return false; // until PUBDEV-7799, disable model MOJO if it was trained with embedded TE.
    final String algoName = _parms.algoName();
    return ModelBuilder.getRegisteredBuilder(algoName)
            .map(ModelBuilder::haveMojo)
            .orElseGet(() -> {
              Log.warn("Model Builder for algo = " + algoName + " is not registered. " +
                      "Unable to determine if Model has a MOJO. Please override method haveMojo().");
              return false;
            });
  }
  
  /**
   * Identifies the default ordering method for models returned from Grid Search
   * @return default sort-by
   */
  public GridSortBy getDefaultGridSortBy() {
    if (! isSupervised())
      return null;
    else if (_output.nclasses() > 1)
      return GridSortBy.LOGLOSS;
    else
      return GridSortBy.RESDEV;
  }

  public static class GridSortBy { // intentionally not an enum to allow 3rd party extensions
    public static final GridSortBy LOGLOSS = new GridSortBy("logloss", false);
    public static final GridSortBy RESDEV = new GridSortBy("residual_deviance", false);
    public static final GridSortBy R2 = new GridSortBy("r2", true);

    public final String _name;
    public final boolean _decreasing;

    GridSortBy(String name, boolean decreasing) { _name = name; _decreasing = decreasing; }
  }

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
  public abstract static class Parameters extends Iced<Parameters> implements AdaptFrameParameters {
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
    public boolean _keep_cross_validation_models = true;
    public boolean _keep_cross_validation_predictions = false;
    /**
     * What precision to use for storing holdout predictions (the number of decimal places stored)?
     * Special values:
     *  -1 == AUTO; use precision=8 for classification, precision=unlimited for everything else
     *  0; disabled
     *
     *  for classification problems consider eg.:
     *     4 to keep only first 4 decimal places (consumes 75% less memory)
     *  or 8 to keep 8 decimal places (consumes 50% less memory)
     */
    public int _keep_cross_validation_predictions_precision = -1;
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
      AUTO(false),
      OneHotInternal(false),
      OneHotExplicit(false),
      Enum(false),
      Binary(false),
      Eigen(false),
      LabelEncoder(false),
      SortByResponse(true),
      EnumLimited(false)
      ;

      CategoricalEncodingScheme(boolean needResponse) { _needResponse = needResponse; }
      final boolean _needResponse;
      boolean needsResponse() { return _needResponse; }
      public static CategoricalEncodingScheme fromGenModel(CategoricalEncoding encoding) {
        if (encoding == null)
          return null;
        try {
            return Enum.valueOf(CategoricalEncodingScheme.class, encoding.name());
        } catch (IllegalArgumentException iae) {
            throw new UnsupportedOperationException("Unknown encoding " + encoding);
        }
      }
    }

    public Key<ModelPreprocessor>[] _preprocessors;

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
    public int _max_categorical_levels = 10;

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
    public String _treatment_column;

    // Check for constant response
    public boolean _check_constant_response = true;

    public boolean _is_cv_model; //internal helper
    public int _cv_fold = -1; //internal use

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

    /** Using _main_model_time_budget_factor to determine if and how we should restrict the time for the main model.
     *  Value 0 means do not use time constraint for the main model.
     *  More details in {@link ModelBuilder#setMaxRuntimeSecsForMainModel()}.
     */
    public double _main_model_time_budget_factor = 0;

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

    /**
     * Reference to custom metric function.
     */
    public String _custom_metric_func = null;


    /**
     * Reference to custom distribution function.
     */
    public String _custom_distribution_func = null;

    /**
     * Directory where generated models will be exported
     */
    public String _export_checkpoints_dir;

    /**
     * Bins for Gains/Lift table, if applicable. Ignored if G/L are not calculated.
     */
    public int _gainslift_bins = -1;

    public MultinomialAucType _auc_type = MultinomialAucType.AUTO;

    /**
     * Type to calculate default AUUC value.Ignored for non uplift models.
     */
    public AUUC.AUUCType _auuc_type = AUUC.AUUCType.AUTO;

    /**
     * Bins for calculating AUUC, if applicable. Ignored for non uplift models.
     */
    public int _auuc_nbins = -1;

    // Public no-arg constructor for reflective creation
    public Parameters() { _ignore_const_cols = defaultDropConsCols(); }

    /** @return the training frame instance */
    public final Frame train() { return _train==null ? null : _train.get(); }
    /** @return the validation frame instance, or null
     *  if a validation frame was not specified */
    public final Frame valid() { return _valid==null ? null : _valid.get(); }

    public String[] getNonPredictors() {
        return Arrays.stream(new String[]{_weights_column, _offset_column, _fold_column, _response_column, _treatment_column})
                .filter(Objects::nonNull)
                .toArray(String[]::new);
    }

    /** Read-Lock both training and validation User frames. */
    public void read_lock_frames(Job job) {
      @SuppressWarnings("unchecked")
      Key<Job> jobKey = job._key;
      Frame tr = train();
      if (tr != null)
        read_lock_frame(tr, jobKey);
      if (_valid != null && !_train.equals(_valid))
        read_lock_frame(_valid.get(), jobKey);
    }

    private void read_lock_frame(Frame fr, Key<Job> jobKey) {
      if (_is_cv_model)
        fr.write_lock_to_read_lock(jobKey);
      else
        fr.read_lock(jobKey);
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
    @Override
    public double missingColumnsType() { return Double.NaN; }

    public boolean hasCheckpoint() { return _checkpoint != null; }
    
    public boolean hasCustomMetricFunc() { return _custom_metric_func != null; }

    public long checksum() {
      return checksum(null);
    }
    /**
     * Compute a checksum based on all non-transient non-static ice-able assignable fields (incl. inherited ones) which have @API annotations.
     * Sort the fields first, since reflection gives us the fields in random order and we don't want the checksum to be affected by the field order.
     * NOTE: if a field is added to a Parameters class the checksum will differ even when all the previous parameters have the same value.  If
     * a client wants backward compatibility they will need to compare parameter values explicitly.
     *
     * The method is motivated by standard hash implementation `hash = hash * P + value` but we use high prime numbers in random order.
     * @param ignoredFields A {@link Set} of fields to ignore. Can be empty or null.
     * @return checksum A 64-bit long representing the checksum of the {@link Parameters} object
     */
    public long checksum(final Set<String> ignoredFields) {
      long xs = 0x600DL;
      int count = 0;
      Field[] fields = Weaver.getWovenFields(this.getClass());
      Arrays.sort(fields, Comparator.comparing(Field::getName));
      for (Field f : fields) {
        if (ignoredFields != null && ignoredFields.contains(f.getName())) {
          // Do not include ignored fields in the final hash
          continue;
        }
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
              } else if (c.getComponentType() == Boolean.TYPE){
                boolean[] arr = (boolean[]) f.get(this);
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
            throw H2O.fail("Failed to calculate checksum for the parameter object", t); //no support yet for int[][] etc.
          }
        } else {
          try {
            f.setAccessible(true);
            Object value = f.get(this);
            if (value instanceof Enum) {
              // use string hashcode for enums, otherwise the checksum would be different each run
              xs = xs * P + (long)(value.toString().hashCode());
            } else if (value != null) {
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

    private void addToUsedIfColumn(Set<String> usedColumns, Set<String> allColumns, String value) {
      if (value == null) return;
      if (allColumns.contains(value)) {
        usedColumns.add(value);
      }
    }

    /**
     * Looks for all String parameters with the word 'column' in the parameter name, if
     * the parameter value is present in supplied array of strings, it will be added to the
     * returned set of used columns.
     *
     * @param trainNames names of columns in the training frame
     * @return set of names of columns present in the params as well as the training frame names
     */
    public Set<String> getUsedColumns(final String[] trainNames) {
      final Set<String> trainColumns = new HashSet<>(Arrays.asList(trainNames));
      final Set<String> usedColumns = new HashSet<>();
      final Field[] fields = Weaver.getWovenFields(this.getClass());
      for (Field f : fields) {
        if (f.getName().equals("_ignored_columns") || !f.getName().toLowerCase().contains("column")) continue;
        Class<?> c = f.getType();
        if (c.isArray()) {
          try {
            f.setAccessible(true);
            if (f.get(this) != null && c.getComponentType() == String.class) {
              String[] values = (String[]) f.get(this);
              for (String v : values) {
                addToUsedIfColumn(usedColumns, trainColumns, v);
              }
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        } else {
          try {
            f.setAccessible(true);
            Object value = f.get(this);
            if (value instanceof String) {
              addToUsedIfColumn(usedColumns, trainColumns, (String) value);
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
      }
      return usedColumns;
    }

    @SuppressWarnings("rawtypes")
    public Set<Key<?>> getDependentKeys() {
      Field[] fields = Weaver.getWovenFields(getClass());
      Set<Key<?>> values = new HashSet<>();
      for (Field f : fields) {
        f.setAccessible(true);
        Class<?> c = f.getType();
        try {
          Object value = f.get(this);
          if (value instanceof Key) {
            values.add((Key) value);
          } else if (value != null && c.isArray() && c.getComponentType() == Key.class) {
            Key[] arr = (Key[]) value;
            for (Key k : arr)
              if (k != null) values.add(k);
          }
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
      return values;
    }

    @Override
    public final CategoricalEncodingScheme getCategoricalEncoding() {
      return _categorical_encoding;
    }

    @Override
    public final String getWeightsColumn() {
      return _weights_column;
    }

    @Override
    public final String getOffsetColumn() {
      return _offset_column;
    }

    @Override
    public final String getFoldColumn() {
      return _fold_column;
    }

    @Override
    public final String getResponseColumn() {
      return _response_column;
    }

    @Override
    public final String getTreatmentColumn(){
      return _treatment_column;
    }

    @Override
    public final int getMaxCategoricalLevels() {
      return _max_categorical_levels;
    }

    public void setDistributionFamily(DistributionFamily distributionFamily){
      _distribution = distributionFamily;
    }

    public DistributionFamily getDistributionFamily() {
      return _distribution;
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

  public interface InteractionBuilder {
    Frame makeInteractions(Frame f);
  }

  public static class InteractionSpec extends Iced {
    private final String[] _columns;
    private final StringPair[] _pairs;
    private final String[] _interactionsOnly;
    private String[] _ignored; // list of columns that can be dropped if they are not used in any interaction

    private InteractionSpec(String[] columns, StringPair[] pairs, String[] interactionsOnly, String[] ignored) {
      _columns = columns;
      _pairs = pairs;
      _interactionsOnly = interactionsOnly;
      if (ignored != null) {
        _ignored = ignored.clone();
        Arrays.sort(_ignored);
      }
    }

    public String[] getInteractionsOnly() {
      return _interactionsOnly;
    }

    public static InteractionSpec allPairwise(String[] columns) {
      return columns != null ? new InteractionSpec(columns, null, null, null) : null;
    }

    public static InteractionSpec create(String[] columns, StringPair[] pairs, String[] interactionsOnly, String[] ignored) {
      return columns == null && pairs == null ?
              null : new InteractionSpec(columns, pairs, interactionsOnly, ignored);
    }

    public static InteractionSpec create(String[] columns, StringPair[] pairs, String[] interactionsOnly) {
      return columns == null && pairs == null ?
              null : new InteractionSpec(columns, pairs, interactionsOnly, null);
    }

    public static InteractionSpec create(String[] columns, StringPair[] pairs) {
      return columns == null && pairs == null ?
              null : new InteractionSpec(columns, pairs, null, null);
    }

    public boolean isEmpty() {
      return _columns == null && _pairs == null;
    }

    private boolean isUsed(String col) {
        if (_columns != null) {
          for (String usedCol : _columns) {
            if (usedCol.equals(col))
              return true;
          }
        }
        if (_pairs != null) {
          for (StringPair colPair : _pairs) {
            if (col.equals(colPair._a) || col.equals(colPair._b))
              return true;
          }
        }
        return false;
    }

    /**
     * Reorders columns of a Frame so that columns that only used to make interactions
     * are at the end of the Frame. Only Vecs that will actually be used are kept in the frame.
     * @param f frame to adjust
     * @return reordered frame
     */
    public Frame reorderColumns(Frame f) {
      if ((_interactionsOnly == null) || (f == null))
        return f;
      Vec[] interOnlyVecs = f.vecs(_interactionsOnly);
      f.remove(_interactionsOnly);
      for (int i = 0; i < _interactionsOnly.length; i++) {
        if (isUsed(_interactionsOnly[i])) {
          f.add(_interactionsOnly[i], interOnlyVecs[i]);
        } else if (! isIgnored(_interactionsOnly[i])) {
          Log.warn("Column '" + _interactionsOnly[i] + "' was marked to be used for interactions only " +
                  "but it is not actually required in any interaction.");
        }
      }
      return f;
    }

    private boolean isIgnored(String column) {
      return _ignored != null && Arrays.binarySearch(_ignored, column) >= 0;
    }

    public Frame removeInteractionOnlyColumns(Frame f) {
      if ((_interactionsOnly == null) || (f == null))
        return f;
      return f.remove(_interactionsOnly);
    }

    public Model.InteractionPair[] makeInteractionPairs(Frame f) {
      if (isEmpty())
        return null;
      InteractionPair[] allPairwise = null;
      InteractionPair[] allExplicit = null;
      int[] interactionIDs = new int[0];
      if (_columns != null) {
        interactionIDs = new int[_columns.length];
        for (int i = 0; i < _columns.length; ++i) {
          interactionIDs[i] = f.find(_columns[i]);
          if (interactionIDs[i] == -1)
            throw new IllegalArgumentException("missing column from the dataset, could not make interaction: " + interactionIDs[i]);
        }
        allPairwise =  Model.InteractionPair.generatePairwiseInteractionsFromList(f, interactionIDs);
      }
      if (_pairs != null) {
        Arrays.sort(interactionIDs);
        allExplicit = new InteractionPair[_pairs.length];
        int n = 0;
        for (StringPair p : _pairs) {
          int aIdx = f.find(p._a);
          if (aIdx == -1)
            throw new IllegalArgumentException("Invalid interactions specified (first column is missing): " + p.toJsonString() + " in " + Arrays.toString(f.names()));
          int bIdx = f.find(p._b);
          if (bIdx == -1)
            throw new IllegalArgumentException("Invalid interactions specified (second column is missing): " + p.toJsonString() + " in " + Arrays.toString(f.names()));
          if (Arrays.binarySearch(interactionIDs, aIdx) >= 0 && Arrays.binarySearch(interactionIDs, bIdx) >= 0)
            continue; // This interaction is already included in set of all pairwise interactions
          allExplicit[n++] = new InteractionPair(f, aIdx, bIdx, f.vec(aIdx).domain(), f.vec(bIdx).domain());
        }
        if (n != allExplicit.length) {
          InteractionPair[] resized = new InteractionPair[n];
          System.arraycopy(allExplicit, 0, resized, 0, resized.length);
          allExplicit = resized;
        }
      }
      InteractionPair[] pairs = allExplicit == null ? allPairwise : ArrayUtils.append(allPairwise, allExplicit);
      if (pairs != null) {
        pairs = flagAllFactorInteractionPairs(f, pairs);
      }
      return pairs;
    }

    private InteractionPair[] flagAllFactorInteractionPairs(Frame f, InteractionPair[] pairs) {
      if (_interactionsOnly == null || _interactionsOnly.length == 0)
        return pairs;
      final String[] interOnly = _interactionsOnly.clone();
      Arrays.sort(interOnly);
      for (InteractionPair p : pairs) {
        boolean v1num = f.vec(p._v1).isNumeric();
        boolean v2num = f.vec(p._v2).isNumeric();
        if (v1num == v2num)
          continue;
        // numerical-categorical interaction
        String numVecName = v1num ? f.name(p._v1) : f.name(p._v2);
        boolean needsAllFactorColumns = Arrays.binarySearch(interOnly, numVecName) >= 0;
        p.setNeedsAllFactorLevels(needsAllFactorColumns);
      }
      return pairs;
    }

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
    public String _column_types[];

    /**
     * @deprecated as of March 6, 2019, replaced by (@link #setNames(String[] names, String[] columnTypes))
     * 
     */
    @Deprecated
    public void setNames(String[] names) {
      _names = names;
      _column_types = new String[names.length];
      Arrays.fill(_column_types, "NA");
    }

    public void setNames(String[] names, String[] columntypes) {
      _names = names;
      _column_types = columntypes;
    }

    public String _origNames[]; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame

    /** Categorical/factor mappings, per column.  Null for non-categorical cols.
     *  Columns match the post-init cleanup columns.  The last column holds the
     *  response col categoricals for SupervisedModels.  */
    public String _domains[][];
    public String _origDomains[][]; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame
    
    public double[] _orig_projection_array;// only set if ModelBuilder.encodeFrameCategoricals() changes the training frame
    
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
    public long _total_run_time; // includes building of cv models
    protected void startClock() { _start_time = System.currentTimeMillis(); }
    protected void stopClock()  {
      _end_time   = System.currentTimeMillis();
      _total_run_time = _run_time = _end_time - _start_time;
    }

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
      this(b, b._train);
    }

    protected Output(ModelBuilder b, Frame train) {
      if (b.error_count() > 0)
        throw new IllegalArgumentException(b.validationErrors());
      // Capture the data "shape" the model is valid on
      setNames(train != null ? train.names() : new String[0], train!=null?train.typesStr():new String[0]);
      _domains = train != null ? train.domains() : new String[0][];
      _origNames = b._origNames;
      _origDomains = b._origDomains;
      _orig_projection_array = b._orig_projection_array;
      _isSupervised = b.isSupervised();
      _hasOffset = b.hasOffsetCol();
      _hasWeights = b.hasWeightCol();
      _hasFold = b.hasFoldCol();
      _hasTreatment = b.hasTreatmentCol();
      _distribution = b._distribution;
      _priorClassDist = b._priorClassDist;
      _reproducibility_information_table = createReproducibilityInformationTable(b);
      assert(_job==null);  // only set after job completion
      _defaultThreshold = -1;
    }

    /** Returns number of input features (OK for most supervised methods, need to override for unsupervised!) */
    public int nfeatures() {
      return _names.length - (_hasOffset?1:0)  - (_hasWeights?1:0) - (_hasFold?1:0) - (_hasTreatment ?1:0) - (isSupervised()?1:0);
    }
    /** Returns features used by the model */
    public String[] features() {
      return Arrays.copyOf(_names, nfeatures());
    }

    /** List of all the associated ModelMetrics objects, so we can delete them
     *  when we delete this model. */
    Key<ModelMetrics>[] _model_metrics = new Key[0];

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
     * Reproducibility information describing the current cluster configuration, each node configuration
     * and checksums for each frame used on the input of the algorithm
     */
    public TwoDimTable[] _reproducibility_information_table;
    

    /**
     * User-facing model scoring history - 2D table with modeling accuracy as a function of time/trees/epochs/iterations, etc.
     */
    public TwoDimTable _scoring_history;
    public TwoDimTable[] _cv_scoring_history;

    public double[] _distribution;
    public double[] _modelClassDist;
    public double[] _priorClassDist;


    protected boolean _isSupervised;

    /**
     * Default threshold used to make decision about binomial predictions
     * -1 if is not set by user - than the default threshold is 0.5 if metrics are not set
     * (0, 1> custom default threshold or validation metric threshold or training metric threshold
     */
    public double _defaultThreshold;


    public boolean isSupervised() { return _isSupervised; }
    /** The name of the response column (which is always the last column). */
    protected boolean _hasOffset; // weights and offset are kept at designated position in the names array
    protected boolean _hasWeights;// only need to know if we have them
    protected boolean _hasFold;// only need to know if we have them
    protected boolean _hasTreatment;
    public boolean hasOffset  () { return _hasOffset;}
    public boolean hasWeights () { return _hasWeights;}
    public boolean hasFold () { return _hasFold;}
    public boolean hasTreatment() { return _hasTreatment;}
    public boolean hasResponse() { return isSupervised(); }
    public String responseName() { return isSupervised()?_names[responseIdx()]:null;}
    public String weightsName () { return _hasWeights ?_names[weightsIdx()]:null;}
    public String offsetName  () { return _hasOffset ?_names[offsetIdx()]:null;}
    public String foldName  () { return _hasFold ?_names[foldIdx()]:null;}
    public InteractionBuilder interactionBuilder() { return null; }
    // Vec layout is  [c1,c2,...,cn, w?, o?, f?, u?, r]
    // cn are predictor cols, r is response, w is weights, o is offset, f is fold and t is treatment - these are optional
    protected int lastSpecialColumnIdx() {
      return _names.length - 1 - (isSupervised()?1:0);
    }
    public int weightsIdx() {
      if(!_hasWeights) return -1;
      return lastSpecialColumnIdx() - (hasOffset()?1:0) - (hasFold()?1:0) - (hasTreatment()?1:0);
    }
    public int offsetIdx() {
      if(!_hasOffset) return -1;
      return lastSpecialColumnIdx() - (hasFold()?1:0) - (hasTreatment()?1:0);
    }
    public int foldIdx() {
      if(!_hasFold) return -1;
      return lastSpecialColumnIdx() - (hasTreatment()?1:0);
    }
    
    public int responseIdx() {
      if(!isSupervised()) return -1;
      return _names.length-1;
    }
    public int treatmentIdx() {
      if(!_hasTreatment) return -1;
      return _names.length - (isSupervised()?1:0) - 1;
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
    /**Is this model a multinomial classification model (supervised and nclasses() > 2 */
    public boolean isMultinomialClassifier() { return isSupervised() && nclasses() > 2; }
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

    /**
     * Retrieves variable importances
     * @return instance of TwoDimTable if model supports variable importances, null otherwise
     */
    public TwoDimTable getVariableImportances() {
      return null;
    }
    
    public synchronized Key<ModelMetrics>[] clearModelMetrics(boolean keepModelTrainingMetrics) {
      Key<ModelMetrics>[] removed;
      if (keepModelTrainingMetrics) {
        Key<ModelMetrics>[] kept = new Key[0];
        if (_training_metrics != null) kept = ArrayUtils.append(kept, _training_metrics._key);
        if (_validation_metrics != null) kept = ArrayUtils.append(kept, _validation_metrics._key);
        if (_cross_validation_metrics != null) kept = ArrayUtils.append(kept, _cross_validation_metrics._key);

        removed = new Key[0];
        for (Key<ModelMetrics> k : _model_metrics) {
          if (!ArrayUtils.contains(kept, k))
            removed = ArrayUtils.append(removed, k);
        }
        _model_metrics = kept;
      } else {
        removed = Arrays.copyOf(_model_metrics, _model_metrics.length);
        _model_metrics = new Key[0];
      }
      return removed;
    }

    public synchronized Key<ModelMetrics>[] getModelMetrics() { return Arrays.copyOf(_model_metrics, _model_metrics.length); }

    public synchronized void changeModelMetricsKey(Key modelkey) {
      for (Key<ModelMetrics> modelMetrics : _model_metrics) {
        modelMetrics.get().setModelKey(modelkey);
      }
    }

    protected long checksum_impl() {
      return (null == _names ? 13 : Arrays.hashCode(_names)) *
              (null == _domains ? 17 : Arrays.deepHashCode(_domains)) *
              getModelCategory().ordinal();
    }

    public double defaultThreshold() {
      if (nclasses() != 2 || _training_metrics == null || _training_metrics instanceof ModelMetricsBinomialUplift)
        return 0.5;
      if(_defaultThreshold == -1) {
        if (_validation_metrics != null && ((ModelMetricsBinomial) _validation_metrics)._auc != null)
          return ((ModelMetricsBinomial) _validation_metrics)._auc.defaultThreshold();
        if (((ModelMetricsBinomial) _training_metrics)._auc != null)
          return ((ModelMetricsBinomial) _training_metrics)._auc.defaultThreshold();
      } else {
        return _defaultThreshold;
      }
      return 0.5;
    }
    
    public void resetThreshold(double value){
      assert value > 0 && value <= 1: "Reset threshold should be value from 0 to 1 (included). Got "+value+".";
      _defaultThreshold = value;
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
            Log.err(e);
            sb.append("Failed to print table ").append(f.getName()).append("\n");
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

    private TwoDimTable[] createReproducibilityInformationTable(ModelBuilder modelBuilder) {
      TwoDimTable nodeInformation = ReproducibilityInformationUtils.createNodeInformationTable();
      TwoDimTable clusterConfiguration = ReproducibilityInformationUtils.createClusterConfigurationTable();
      TwoDimTable inputFramesInformation = createInputFramesInformationTable(modelBuilder);
      return new TwoDimTable[] {nodeInformation, clusterConfiguration, inputFramesInformation};
    }

    public TwoDimTable createInputFramesInformationTable(ModelBuilder modelBuilder) {
      String[] colHeaders = new String[] {"Input Frame", "Checksum", "ESPC"};
      String[] colTypes = new String[] {"string", "long", "string"};
      String[] colFormat = new String[] {"%s", "%d", "%d"};

      final int rows = getInformationTableNumRows();
      TwoDimTable table = new TwoDimTable(
              "Input Frames Information", null,
              new String[rows],
              colHeaders,
              colTypes,
              colFormat,
              "");

      table.set(0, 0, "training_frame");
      table.set(1, 0, "validation_frame");
      table.set(0, 1, modelBuilder.train() != null ? modelBuilder.train().checksum() : -1);
      table.set(1, 1, modelBuilder._valid != null ? modelBuilder.valid().checksum() : -1);
      table.set(0, 2, modelBuilder.train() != null ? Arrays.toString(modelBuilder.train().anyVec().espc()) : -1);
      table.set(1, 2, modelBuilder._valid != null ? Arrays.toString(modelBuilder.valid().anyVec().espc()) : -1);

      return table;
    }
    
    public int getInformationTableNumRows() {
      return 2; // 1 row per each input frame (training frame, validation frame)
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
    evalAutoParamsEnabled = evaluateAutoModelParameters();
    if (evalAutoParamsEnabled) {
      initActualParamValues();
    }
    _output = output;  // Output won't be set if we're assert output != null;
    if (_output != null)
      _output.startClock();
    _dist = isSupervised() && _output.nclasses() == 1 ? DistributionFactory.getDistribution(_parms) : null;
    Log.info("Starting model "+ selfKey);
  }

  public void initActualParamValues() {}
  
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

  public double likelihood(double w, double y, double[] f) {
    return 0.0; // place holder.  This function is overridden in GLM.
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
  }

  // return the most up-to-date model metrics
  public ScoringInfo last_scored() {
    return scoringInfo == null ? null : scoringInfo[scoringInfo.length-1];
  }

  // Lower is better
  public float loss() {
    switch (Optional.ofNullable(_parms._stopping_metric).orElse(ScoreKeeper.StoppingMetric.AUTO)) {
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
      case AUCPR:
        return (float)(1-AUCPR());
/*      case r2:
        return (float)(1-r2());*/
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

  public double r2() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._r2 : last_scored().validation ? last_scored().scored_valid._r2 : last_scored().scored_train._r2;

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
    if(mm instanceof ModelMetricsBinomial) {
      return ((ModelMetricsBinomial) mm)._auc._auc;
    } else if(mm instanceof ModelMetricsMultinomial) {
      return ((ModelMetricsMultinomial) mm).auc();
    }
    return Double.NaN;
  }

  public double AUCPR() {
    if (scoringInfo != null)
      return last_scored().cross_validation ? last_scored().scored_xval._pr_auc : last_scored().validation ? last_scored().scored_valid._pr_auc : last_scored().scored_train._pr_auc;

    ModelMetrics mm = _output._cross_validation_metrics != null ? _output._cross_validation_metrics : _output._validation_metrics != null ? _output._validation_metrics : _output._training_metrics;
    if (mm == null) return Double.NaN;
    if(mm instanceof ModelMetricsBinomial) {
      return ((ModelMetricsBinomial) mm)._auc._pr_auc;
    } else if(mm instanceof ModelMetricsMultinomial) {
      return ((ModelMetricsMultinomial) mm).pr_auc();
    }
    return Double.NaN;
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
    return adaptTestForTrain(test, expensive, computeMetrics, false);
  }
  
  public String[] adaptTestForTrain(Frame test, boolean expensive, boolean computeMetrics, boolean catEncoded) {
    return adaptTestForTrain(
            test,
            _output._origNames,
            _output._origDomains,
            _output._names,
            _output._domains,
            makeAdaptFrameParameters(),
            expensive,
            computeMetrics,
            _output.interactionBuilder(),
            getToEigenVec(),
            _toDelete,
            catEncoded
    );
  }

  protected AdaptFrameParameters makeAdaptFrameParameters() {
    return _parms;
  }

  public interface AdaptFrameParameters {
    Parameters.CategoricalEncodingScheme getCategoricalEncoding();
    String getWeightsColumn();
    String getOffsetColumn();
    String getFoldColumn();
    String getResponseColumn();
    String getTreatmentColumn();
    double missingColumnsType();
    int getMaxCategoricalLevels();
    default String[] getNonPredictors() {
      return Arrays.stream(new String[]{getWeightsColumn(), getOffsetColumn(), getFoldColumn(), getResponseColumn(), getTreatmentColumn()})
              .filter(Objects::nonNull)
              .toArray(String[]::new);
    }
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
   * @param interactionBldr Column names to create pairwise interactions with
   * @param catEncoded Whether the categorical columns of the test frame were already transformed via categorical_encoding
   */
  public static String[] adaptTestForTrain(final Frame test, final String[] origNames, final String[][] origDomains,
                                           String[] names, String[][] domains, final AdaptFrameParameters parms,
                                           final boolean expensive, final boolean computeMetrics,
                                           final InteractionBuilder interactionBldr, final ToEigenVec tev,
                                           final IcedHashMap<Key, String> toDelete, final boolean catEncoded)
          throws IllegalArgumentException {
    String[] msg = new String[0];
    if (test == null) return msg;
    if (catEncoded && origNames==null) return msg;

    // test frame matches the training frame (after categorical encoding, if applicable)
    String[][] tdomains = test.domains();
    if (names == test._names && domains == tdomains || (Arrays.equals(names, test._names) && Arrays.deepEquals(domains, tdomains)) )
      return msg;

    String[] backupNames = names;
    String[][] backupDomains = domains;

    final String weights = parms.getWeightsColumn();
    final String offset = parms.getOffsetColumn();
    final String fold = parms.getFoldColumn();
    final String response = parms.getResponseColumn();
    final String treatment = parms.getTreatmentColumn();


    // whether we need to be careful with categorical encoding - the test frame could be either in original state or in encoded state
    // keep in sync with FrameUtils.categoricalEncoder: as soon as a categorical column has been encoded, we should check here.
    final boolean checkCategoricals = !catEncoded && Arrays.asList(
            Parameters.CategoricalEncodingScheme.Binary,
            Parameters.CategoricalEncodingScheme.LabelEncoder,
            Parameters.CategoricalEncodingScheme.Eigen,
            Parameters.CategoricalEncodingScheme.EnumLimited,
            Parameters.CategoricalEncodingScheme.OneHotExplicit
    ).indexOf(parms.getCategoricalEncoding()) >= 0;

    // test frame matches the user-given frame (before categorical encoding, if applicable)
    if (checkCategoricals && origNames != null) {
      boolean match = Arrays.equals(origNames, test.names());
      if (!match) {
        // As soon as the test frame contains at least one original pre-encoding predictor,
        // then we consider the frame as valid for predictions, and we'll later fill missing columns with NA
        Set<String> required = new HashSet<>(Arrays.asList(origNames));
        required.removeAll(Arrays.asList(response, weights, fold, treatment));
        for (String name : test.names()) {
          if (required.contains(name)) {
            match = true;
            break;
          }
        }
      }

      // still have work to do below, make sure we set the names/domains to the original user-given values
      // such that we can do the int->enum mapping and cat. encoding below (from scratch)
      if (match) {
        names = origNames;
        domains = origDomains;
      }
    }

    // create the interactions now and bolt them on to the front of the test Frame
    if (null != interactionBldr) {
      interactionBldr.makeInteractions(test);
    }

    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap categoricals
    ArrayList<String> msgs = new ArrayList<>();
    Vec vvecs[] = new Vec[names.length];
    int good = 0;               // Any matching column names, at all?
    int convNaN = 0;  // count of columns that were replaced with NA
    final Frame.FrameVecRegistry frameVecRegistry = test.frameVecRegistry();
    for (int i = 0; i < names.length; i++) {
      Vec vec = frameVecRegistry.findByColName(names[i]); // Search in the given validation set
      boolean isResponse = response != null && names[i].equals(response);
      boolean isWeights = weights != null && names[i].equals(weights);
      boolean isOffset = offset != null && names[i].equals(offset);
      boolean isFold = fold != null && names[i].equals(fold);
      boolean isTreatment = treatment != null && names[i].equals(treatment);
      // If a training set column is missing in the test set, complain (if it's ok, fill in with NAs (or 0s if it's a fold-column))
      if (vec == null) {
        if (isResponse && computeMetrics)
          throw new IllegalArgumentException("Test/Validation dataset is missing response column '" + response + "'");
        else if (isOffset)
          throw new IllegalArgumentException(H2O.technote(12, "Test/Validation dataset is missing offset column '" + offset + "'. If your intention is to disable the effect of the offset add a zero offset column."));
        else if (isWeights && computeMetrics) {
          if (expensive) {
            vec = test.anyVec().makeCon(1);
            toDelete.put(vec._key, "adapted missing vectors");
            // cross-validation generated weights will not be found in test/validation dataset.  This warning is
            // invalid.  We will suppress this warning.
            if (!names[i].contains("_internal_cv_weights_")) {
              msgs.add(H2O.technote(1, "Test/Validation dataset is missing weights column '" +
                      names[i] + "' (needed because a response was found and metrics are to be computed): " +
                      "substituting in a column of 1s"));
            }
          }
          else if (isTreatment && computeMetrics) {
            throw new IllegalArgumentException("Test/Validation dataset is missing treatment column '" + treatment + "'");
          }
        } else if (expensive) {   // generate warning even for response columns.  Other tests depended on this.
          final double defval;
          if (isWeights) 
            defval = 1; // note: even though computeMetrics is false we should still have sensible weights (GLM skips rows with NA weights)
          else 
            if (isFold && domains[i] == null)
              defval = 0;
          else {
            defval = parms.missingColumnsType();
            convNaN++;
          }
          vec = test.anyVec().makeCon(defval);
          toDelete.put(vec._key, "adapted missing vectors");
          String str = "Test/Validation dataset is missing column '" + names[i] + "': substituting in a column of " + defval;
          if (ArrayUtils.contains(parms.getNonPredictors(), names[i]))
            Log.info(str); // we are doing a "pure" predict (computeMetrics is false), don't complain to the user
          else
            msgs.add(str);
        }
      }
      if( vec != null) {          // I have a column with a matching name
        if( domains[i] != null) { // Model expects an categorical
          if (vec.isString())
            vec = VecUtils.stringToCategorical(vec); //turn a String column into a categorical column (we don't delete the original vec here)
          if( expensive && !Arrays.equals(vec.domain(),domains[i]) ) { // Result needs to be the same categorical
            Vec evec;
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
            if( isTreatment && vec.domain() != null && ds.length == domains[i].length+vec.domain().length)
              throw new IllegalArgumentException("Test/Validation dataset has a categorical treatment column '"+names[i]+"' with no levels in common with the model");
            if (ds.length > domains[i].length)
              msgs.add("Test/Validation dataset column '" + names[i] + "' has levels not trained on: " + ArrayUtils.toStringQuotedElements(Arrays.copyOfRange(ds, domains[i].length, ds.length), 20));
            vec = evec;
          }
        } else if(vec.isCategorical()) {
          throw new IllegalArgumentException("Test/Validation dataset has categorical column '" + names[i] + "' which is real-valued in the training data");
        }
        good++;      // Assumed compatible; not checking e.g. Strings vs UUID
      }
      vvecs[i] = vec;
    }

    if( good == convNaN )
      throw new IllegalArgumentException("Test/Validation dataset has no columns in common with the training set");

    if( good == names.length || (response != null && test.find(response) == -1 && good == names.length - 1) )  // Only update if got something for all columns
      test.restructure(names, vvecs, good);

    if (expensive && checkCategoricals) {
      final boolean hasCategoricalPredictors = hasCategoricalPredictors(test, response, weights, offset, fold, treatment, names, domains);

      // check if we first need to expand categoricals before calling this method again
      if (hasCategoricalPredictors) {
        Frame updated = categoricalEncoder(test, parms.getNonPredictors(), parms.getCategoricalEncoding(), tev, parms.getMaxCategoricalLevels());
        toDelete.put(updated._key, "categorically encoded frame");
        test.restructure(updated.names(), updated.vecs()); //updated in place
        String[] msg2 = adaptTestForTrain(test, origNames, origDomains, backupNames, backupDomains, parms, expensive, computeMetrics, interactionBldr, tev, toDelete, true /*catEncoded*/);
        msgs.addAll(Arrays.asList(msg2));
        return msgs.toArray(new String[msgs.size()]);
      }
    }

    return msgs.toArray(new String[msgs.size()]);
  }

  private static boolean hasCategoricalPredictors(final Frame frame, final String responseName,
                                           final String wieghtsName, final String offsetName,
                                           final String foldName, final String treatmentName, final String[] names,
                                           final String[][] domains) {

    boolean haveCategoricalPredictors = false;
    final Map<String, Integer> namesIndicesMap = new HashMap<>(names.length);

    for (int i = 0; i < names.length; i++) {
      namesIndicesMap.put(names[i], i);
    }

    for (int i = 0; i < frame.numCols(); ++i) {
      if (frame.names()[i].equals(responseName)) continue;
      if (frame.names()[i].equals(wieghtsName)) continue;
      if (frame.names()[i].equals(offsetName)) continue;
      if (frame.names()[i].equals(foldName)) continue;
      if (frame.names()[i].equals(treatmentName)) continue;
      // either the column of the test set is categorical (could be a numeric col that's already turned into a factor)
      if (frame.vec(i).get_type() == Vec.T_CAT) {
        haveCategoricalPredictors = true;
        break;
      }
      // or a equally named column of the training set is categorical, but the test column isn't (e.g., numeric column provided to be converted to a factor)
      final int whichCol = namesIndicesMap.get(frame.name(i));
      if (whichCol >= 0 && domains[whichCol] != null) {
        haveCategoricalPredictors = true;
        break;
      }
    }

    return haveCategoricalPredictors;

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
  
  public Frame result() {
    throw new UnsupportedOperationException("this model doesn't support constant frame results");
  }

  public Frame transform(Frame fr) {
    throw new UnsupportedOperationException("this model doesn't support constant frame results");
  }

  /** Bulk score the frame {@code fr}, producing a Frame result; the 1st
   *  Vec is the predicted class, the remaining Vecs are the probability
   *  distributions.  For Regression (single-class) models, the 1st and only
   *  Vec is the prediction value.  The result is in the DKV; caller is
   *  responsible for deleting.
   *
   * @param fr  frame which should be scored
   * @param destination_key  store prediction frame under give key
   * @param customMetricFunc  function to produce adhoc scoring metrics if actuals are presented
   * @return A new frame containing a predicted values. For classification it
   *         contains a column with prediction and distribution for all
   *         response classes. For regression it contains only one column with
   *         predicted values.
   * @throws IllegalArgumentException
   */
  public Frame score(Frame fr, String destination_key, CFuncRef customMetricFunc) throws IllegalArgumentException {
    return score(fr, destination_key, null, true, customMetricFunc);
  }
  public Frame score(Frame fr, String destination_key) throws IllegalArgumentException {
    return score(fr, destination_key, null, true);
  }

  public Frame score(Frame fr, String destination_key, Job j) throws IllegalArgumentException {
    return score(fr, destination_key, j, true);
  }

  public Frame score(Frame fr, CFuncRef customMetricFunc) throws IllegalArgumentException {
    return score(fr, null, null, true, customMetricFunc);
  }

  /**
   * Adds a scoring-related warning. 
   * 
   * Note: The implementation might lose a warning if scoring is triggered in parallel
   * 
   * @param s warning description
   */
  private void addWarningP(String s) {
    String[] warningsP = _warningsP;
    warningsP = warningsP != null ? Arrays.copyOf(warningsP, warningsP.length + 1) : new String[1];
    warningsP[warningsP.length - 1] = s;
    _warningsP = warningsP;
  }

  public boolean containsResponse(String s, String responseName) {
    Pattern pat = Pattern.compile("'(.*?)'");
    Matcher match = pat.matcher(s);
    if (match.find() && responseName.equals(match.group(1))) {
      return true;
    }
    return false;
  }

  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics) throws IllegalArgumentException {
    return score(fr, destination_key, j, computeMetrics, CFuncRef.NOP);
  }
  
  protected Frame adaptFrameForScore(Frame fr, boolean computeMetrics, List<Frame> tmpFrames) {
    Frame adaptFr = new Frame(fr);
    applyPreprocessors(adaptFr, tmpFrames);
    String[] msg = adaptTestForTrain(adaptFr,true, computeMetrics);   // Adapt
    tmpFrames.add(adaptFr);
    if (msg.length > 0) {
      for (String s : msg) {
        if ((_output.responseName() == null) || !containsResponse(s, _output.responseName())) {  // response column missing will not generate warning for prediction
          addWarningP(s);                      // add warning string to model
          Log.warn(s);
        }
      }
    }
    return adaptFr;
  }
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    // Adapt frame, clean up the previous score warning messages
    _warningsP = new String[0];
    computeMetrics = computeMetrics &&
            (!_output.hasResponse() || (fr.vec(_output.responseName()) != null && !fr.vec(_output.responseName()).isBad()));
    List<Frame> tmpFrames = new ArrayList<>();
    Frame adaptFr = adaptFrameForScore(fr, computeMetrics, tmpFrames);

    // Predict & Score
    PredictScoreResult result = predictScoreImpl(fr, adaptFr, destination_key, j, computeMetrics, customMetricFunc); 
    Frame output = result.getPredictions();
    result.makeModelMetrics(fr, adaptFr);

    Vec predicted = output.vecs()[0]; // Modeled/predicted response
    String[] mdomain = predicted.domain(); // Domain of predictions (union of test and train)
    // Output is in the model's domain, but needs to be mapped to the scored
    // dataset's domain.
    if(_output.isClassifier() && computeMetrics && !_output.hasTreatment()) {
      Vec actual = fr.vec(_output.responseName());
      if( actual != null ) {  // Predict does not have an actual, scoring does
        String[] sdomain = actual.domain(); // Scored/test domain; can be null
        if (sdomain != null && mdomain != sdomain && !Arrays.equals(mdomain, sdomain))
          CategoricalWrappedVec.updateDomain(output.vec(0), sdomain);
      }
    }
    for (Frame tmp : tmpFrames) Frame.deleteTempFrameAndItsNonSharedVecs(tmp, fr);
    return output;
  }
  
  private void applyPreprocessors(Frame fr, List<Frame> tmpFrames) {
    if (_parms._preprocessors == null) return;
    
    for (Key<ModelPreprocessor> key : _parms._preprocessors) {
      DKV.prefetch(key);
    }
    Frame result = fr;
    for (Key<ModelPreprocessor> key : _parms._preprocessors) {
      ModelPreprocessor preprocessor = key.get();
      result = preprocessor.processScoring(result, this);
      tmpFrames.add(result);
    }
    fr.restructure(result.names(), result.vecs()); //inplace
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
    if (myDist != null && myDist._family == DistributionFamily.huber) {
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
            if (myDist!=null && myDist._family == DistributionFamily.huber) {
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

  protected String[] makeScoringNames(){
    return makeScoringNames(_output);
  }

  protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
    String[][] domains = new String[names.length][];
    Vec response = adaptFrm.lastVec();
    domains[0] = names.length == 1 || _output.hasTreatment() ? null : ! computeMetrics ? _output._domains[_output._domains.length - 1] : response.domain();
    if (_parms._distribution == DistributionFamily.quasibinomial) {
      domains[0] = new VecUtils.CollectDoubleDomain(null,2).doAll(response).stringDomain(response.isInt());
    }
    return domains;
  }

  public static <O extends Model.Output> String [] makeScoringNames(O output){
    final int nc = output.nclasses();
    final int ncols = nc==1?1:nc+1; // Regression has 1 predict col; classification also has class distribution
    String [] names = new String[ncols];
    if(output.hasTreatment()){
      names[0] = "uplift_predict";
      names[1] = "p_y1_ct1";
      names[2] = "p_y1_ct0";
    } else {
      names[0] = "predict";
      for (int i = 1; i < names.length; ++i) {
        names[i] = output.classNames()[i - 1];
        // turn integer class labels such as 0, 1, etc. into p0, p1, etc.
        try {
          Integer.valueOf(names[i]);
          names[i] = "p" + names[i];
        } catch (Throwable t) {
          // do nothing, non-integer names are fine already
        }
      }
    }
    return names;
  }

  /** Allow subclasses to define their own BigScore class. */
  protected BigScore makeBigScoreTask(String[][] domains, String[] names ,
                                      Frame adaptFrm, boolean computeMetrics,
                                      boolean makePrediction, Job j,
                                      CFuncRef customMetricFunc) {
    return new BigScore(domains[0],
                        names != null ? names.length : 0,
                        adaptFrm.means(),
                        _output.hasWeights() && adaptFrm.find(_output.weightsName()) >= 0,
                        computeMetrics,
                        makePrediction,
                        j,
                        customMetricFunc);
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
  protected PredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    // Build up the names & domains.
    String[] names = makeScoringNames();
    String[][] domains = makeScoringDomains(adaptFrm, computeMetrics, names);

    // Score the dataset, building the class distribution & predictions
    BigScore bs = makeBigScoreTask(domains,
                                   names,
                                   adaptFrm,
                                   computeMetrics,
                                   true,
                                   j,
                                   customMetricFunc).doAll(names.length, Vec.T_NUM, adaptFrm);

    ModelMetrics.MetricBuilder<?> mb = null;
    Frame rawPreds = null;
    if (computeMetrics && bs._mb != null) {
      rawPreds = bs.outputFrame();
      mb = bs._mb;
    }
    Frame predictFr = bs.outputFrame(Key.make(destination_key), names, domains);
    Frame outputPreds = postProcessPredictions(adaptFrm, predictFr, j);
    return new PredictScoreResult(mb, rawPreds, outputPreds);
  }

  protected class PredictScoreResult {
    private final ModelMetrics.MetricBuilder<?> _mb; // metric builder can be null if training was interrupted/cancelled even when metrics were requested
    private final Frame _rawPreds;
    private final Frame _outputPreds;

    public PredictScoreResult(ModelMetrics.MetricBuilder<?> mb, Frame rawPreds, Frame outputPreds) {
      _mb = mb;
      _rawPreds = rawPreds;
      _outputPreds = outputPreds;
    }
    
    public final Frame getPredictions() {
      return _outputPreds;
    }

    public ModelMetrics.MetricBuilder<?> getMetricBuilder() {
      return _mb;
    }

    public ModelMetrics makeModelMetrics(Frame fr, Frame adaptFrm) {
      if (_mb == null)
        return null;
      return _mb.makeModelMetrics(Model.this, fr, adaptFrm, _rawPreds);
    }

  }
  
  /**
   * Post-process prediction frame.
   *
   * @param adaptFrm
   * @param predictFr
   * @return
   */
  protected Frame postProcessPredictions(Frame adaptFrm, Frame predictFr, Job j) {  
    return predictFr;
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
    Vec response = adaptFrm.lastVec();
    domains[0] = _output.nclasses() == 1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : response.domain();
    if (_parms._distribution == DistributionFamily.quasibinomial) {
      domains[0] = new VecUtils.CollectDoubleDomain(null,2).doAll(response).stringDomain(response.isInt());
    }

    // Score the dataset, building the class distribution & predictions
    BigScore bs = makeBigScoreTask(domains, null, adaptFrm, computeMetrics, false, null, CFuncRef.from(_parms._custom_metric_func)).doAll(adaptFrm);
    return bs._mb;
  }

  protected class BigScore extends CMetricScoringTask<BigScore> implements BigScorePredict, BigScoreChunkPredict {
    final protected String[] _domain; // Prediction domain; union of test and train classes
    final protected int _npredcols;  // Number of columns in prediction; nclasses+1 - can be less than the prediction domain
    final double[] _mean;  // Column means of test frame
    final public boolean _computeMetrics;  // Column means of test frame
    final public boolean _hasWeights;
    final public boolean _makePreds;
    final public Job _j;

    private transient BigScorePredict _localPredict;

    /** Output parameter: Metric builder */
    public ModelMetrics.MetricBuilder _mb;

    public BigScore(String[] domain, int ncols, double[] mean, boolean testHasWeights,
                    boolean computeMetrics, boolean makePreds, Job j, CFuncRef customMetricFunc) {
      super(customMetricFunc);
      _j = j;
      _domain = domain; _npredcols = ncols; _mean = mean; _computeMetrics = computeMetrics; _makePreds = makePreds;
      if(_output._hasWeights && _computeMetrics && !testHasWeights)
        throw new IllegalArgumentException("Missing weights when computing validation metrics.");
      _hasWeights = testHasWeights;
    }

    @Override
    protected void setupLocal() {
      super.setupLocal();
      _localPredict = setupBigScorePredict(this);
      assert _localPredict != null;
    }

    @Override public void map(Chunk chks[], NewChunk cpreds[] ) {
      if (isCancelled() || _j != null && _j.stop_requested()) return;
      Chunk weightsChunk = _hasWeights && _computeMetrics ? chks[_output.weightsIdx()] : null;
      Chunk offsetChunk = _output.hasOffset() ? chks[_output.offsetIdx()] : null;
      Chunk responseChunk = null;
      float [] actual = null;
      _mb = Model.this.makeMetricBuilder(_domain);
      if (_computeMetrics) {
        if (_output.hasResponse()) {
          actual = new float[1];
          responseChunk = chks[_output.responseIdx()];
        } else
          actual = new float[chks.length];
      }
      int len = chks[0]._len;
      try (BigScoreChunkPredict predict = _localPredict.initMap(_fr, chks)) {
        double[] tmp = new double[_output.nfeatures()];
        for (int row = 0; row < len; row++) {
          double weight = weightsChunk != null ? weightsChunk.atd(row) : 1;
          if (weight == 0) {
            if (_makePreds) {
              for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
                cpreds[c].addNum(0);
            }
            continue;
          }
          double offset = offsetChunk != null ? offsetChunk.atd(row) : 0;
          double[] preds = predict.score0(chks, offset, row, tmp, _mb._work);
          if (_computeMetrics) {
            if (responseChunk != null) {
              actual[0] = (float) responseChunk.atd(row);
            } else {
              for (int i = 0; i < actual.length; ++i)
                actual[i] = (float) data(chks, row, i);
            }
            _mb.perRow(preds, actual, weight, offset, Model.this);
            // Handle custom metric
            customMetricPerRow(preds, actual, weight, offset, Model.this);
          }
          if (_makePreds) {
            for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
              cpreds[c].addNum(preds[c]);
          }
        }
      }
    }

    @Override
    public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
      return Model.this.score0(chks, offset, row_in_chunk, tmp, preds);
    }

    @Override
    public BigScoreChunkPredict initMap(final Frame fr, final Chunk[] chks) {
      return this;
    }

    @Override
    public void close() {
      // nothing to do - meant to be overridden
    }

    @Override public void reduce(BigScore bs ) {
      super.reduce(bs);
      if (_mb != null) _mb.reduce(bs._mb);
    }

    @Override protected void postGlobal() {
      super.postGlobal();
      if(_mb != null) {
        _mb.postGlobal(getComputedCustomMetric());
      }
    }
  }

  public interface BigScorePredict {
    BigScoreChunkPredict initMap(final Frame fr, final Chunk chks[]);
  }

  public interface BigScoreChunkPredict extends AutoCloseable {
    double[] score0(Chunk chks[], double offset, int row_in_chunk, double[] tmp, double[] preds);
    @Override
    void close();
  }

  protected BigScorePredict setupBigScorePredict(BigScore bs) { return bs; };

  // OVerride this if your model needs data preprocessing (on the fly standardization, NA handling)
  protected double data(Chunk[] chks, int row, int col) {
    return chks[col].atd(row);
  }


  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  public double[] score0( Chunk chks[], int row_in_chunk, double[] tmp, double[] preds ) {
    return score0(chks, 0, row_in_chunk, tmp, preds);
  }

  public double[] score0( Chunk chks[], double offset, int row_in_chunk, double[] tmp, double[] preds ) {
    assert(_output.nfeatures() == tmp.length);
    for( int i=0; i< tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
    double [] scored = score0(tmp, preds, offset);
    if(needsPostProcess() && isSupervised())
      score0PostProcessSupervised(scored, tmp);
    return scored;
  }

  /**
   * Implementations can disable post-processing of predictions by overriding this method (eg. GLM)
   * @return true, if output of score0 needs post-processing, false if the output is final
   */
  protected boolean needsPostProcess() {
    return true;
  }

  protected final void score0PostProcessSupervised(double[] scored, double[] tmp) {
    // Correct probabilities obtained from training on oversampled data back to original distribution
    // C.f. http://gking.harvard.edu/files/0s.pdf Eq.(27)
    if( _output.isClassifier()) {
      if (_parms._balance_classes)
        GenModel.correctProbabilities(scored, _output._priorClassDist, _output._modelClassDist);
      //assign label at the very end (after potentially correcting probabilities)
      if(!_output.hasTreatment()) {
        scored[0] = hex.genmodel.GenModel.getPrediction(scored, _output._priorClassDist, tmp, defaultThreshold());
      }
    }
  }

  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  */
  protected abstract double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]);
  
  /**Override scoring logic for models that handle weight/offset**/
  protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/], double offset) {
    assert (offset == 0) : "Override this method for non-trivial offset!";
    return score0(data, preds);
  }
  // Version where the user has just ponied-up an array of data to be scored.
  // Data must be in proper order.  Handy for JUnit tests.
  public double score(double[] data){
    double[] pred = score0(data, new double[_output.nclasses()]);
    return _output.nclasses() == 1 ? pred[0] /* regression */ : ArrayUtils.maxIndex(pred) /*classification?*/; 
  }

  @Override protected Futures remove_impl(Futures fs, boolean cascade) {
    if (_output._model_metrics != null)
      for( Key k : _output._model_metrics )
        Keyed.remove(k, fs, true);
    if (cascade) {
      deleteCrossValidationFoldAssignment();
      deleteCrossValidationPreds();
      deleteCrossValidationModels();
    }
    cleanUp(_toDelete);
    return super.remove_impl(fs, cascade);
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
    return _parms.checksum(null) * _output.checksum_impl();
  }

  /**
   * Override this in models that support serialization into the MOJO format.
   * @return a class that inherits from ModelMojoWriter
   */
  public ModelMojoWriter getMojo() {
    throw H2O.unimpl("MOJO format is not available for " + _parms.fullName() + " models.");
  }

  /**
   * Specify categorical encoding that should be applied before running score0 method of POJO/MOJO.
   * Default: AUTO - POJO/MOJO handles encoding or no transformation of input is needed.
   * @return instance of CategoricalEncoding supported by GenModel or null if encoding is not supported.
   */
  protected CategoricalEncoding getGenModelEncoding() {
    return CategoricalEncoding.AUTO;
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
    PojoWriter writer = makePojoWriter();
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
    sb.p("  Standalone prediction code with sample test data for ").p(toJavaModelClassName()).p(" named ").p(modelName)
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
    if (isGeneratingPreview && writer.toJavaCheckTooBig()) {
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
    for (Class<?> clz : getPojoInterfaces())
      sb.p("import ").p(clz.getName()).p(";").nl();
    sb.nl();
    sb.p("@ModelPojo(name=\"").p(modelName).p("\", algorithm=\"").p(toJavaAlgo()).p("\")").nl();
    sb.p("public class ").p(modelName).p(" extends GenModel ").p(makeImplementsClause()).p("{").nl().ii(1);
    sb.ip("public hex.ModelCategory getModelCategory() { return hex.ModelCategory." + _output
        .getModelCategory() + "; }").nl();
    writer.toJavaInit(sb, fileCtx).nl();
    toJavaNAMES(sb, fileCtx);
    CategoricalEncoding encoding = getGenModelEncoding();
    assert encoding != null;
    boolean writeOrigs = encoding != CategoricalEncoding.AUTO; // export orig names & domains if POJO/MOJO doesn't handle encoding itself
    if (writeOrigs && _output._origNames != null)
      toJavaOrigNAMES(sb, fileCtx);
    toJavaNCLASSES(sb);
    toJavaDOMAINS(sb, fileCtx);
    if (writeOrigs && _output._origDomains != null)
      toJavaOrigDOMAINS(sb, fileCtx);
    toJavaPROB(sb);
    toJavaSuper(modelName, sb);
    sb.p("  public String getUUID() { return Long.toString("+toJavaUUID()+"L); }").nl();
    toJavaPredict(writer, sb, fileCtx, verboseCode);
    writer.toJavaTransform(sb, fileCtx, verboseCode);
    sb.p("}").nl().di(1);
    fileCtx.generate(sb); // Append file context
    sb.nl();
    return sb;
  }

  protected PojoWriter makePojoWriter() {
    return new DelegatingPojoWriter(this);
  }

  protected String toJavaModelClassName() {
    return this.getClass().getSimpleName();
  }
  
  protected String toJavaAlgo() {
    return this.getClass().getSimpleName().toLowerCase().replace("model", "");
  }
  
  protected String toJavaUUID() {
    return String.valueOf(checksum());
  }

  protected Class<?>[] getPojoInterfaces() { return new Class<?>[0]; }

  private SB makeImplementsClause() {
    SB sb = new SB();
    Class<?>[] interfaces = getPojoInterfaces();
    if (interfaces.length == 0)
      return sb;
    sb.p("implements ");
    for (int i = 0; i < interfaces.length - 1; i++)
      sb.p(interfaces[i].getSimpleName()).p(", ");
    sb.p(interfaces[interfaces.length - 1].getSimpleName()).p(' ');
    return sb;
  }

  /** Generate implementation for super class. */
  private SBPrintStream toJavaSuper(String modelName, SBPrintStream sb) {
    String responseName = isSupervised() ? '"' + _output.responseName() + '"': null;
    return sb.nl().ip("public " + modelName + "() { super(NAMES,DOMAINS," + responseName + "); }").nl();
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

  private SBPrintStream toJavaOrigNAMES(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    final String modelName = JCodeGen.toJavaId(_key.toString());
    final String namesHolderClassName = "OrigNamesHolder_"+modelName;
    sb.i().p("// ").p("Original names of columns used by model.").nl();
    sb.i().p("public static final String[] ORIG_NAMES = "+namesHolderClassName+".VALUES;").nl();
    // Generate class which fills the names into array
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.i().p("// The class representing original training column names").nl();
        int nResponse = _output._names.length - _output.nfeatures();
        JCodeGen.toClassWithArray(out, null, namesHolderClassName,
                Arrays.copyOf(_output._origNames, _output._origNames.length - nResponse));
      }
    });

    sb.nl();
    sb.ip("@Override").nl();
    sb.ip("public String[] getOrigNames() {").nl();
    sb.ii(1).ip("return ORIG_NAMES;").nl();
    sb.di(1).ip("}").nl();

    return sb;
  }

  private SBPrintStream toJavaNCLASSES(SBPrintStream sb ) {
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

  private SBPrintStream toJavaOrigDOMAINS(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    String modelName = JCodeGen.toJavaId(_key.toString());
    sb.nl();
    sb.ip("// Original column domains. The last array contains domain of response column.").nl();
    sb.ip("public static final String[][] ORIG_DOMAINS = new String[][] {").nl();
    String [][] domains = _output._origDomains;
    for (int i=0; i< domains.length; i++) {
      final int idx = i;
      final String[] dom = domains[i];
      final String colInfoClazz = modelName+"_OrigColInfo_"+i;
      sb.i(1).p("/* ").p(_output._origNames[i]).p(" */ ");
      if (dom != null) sb.p(colInfoClazz).p(".VALUES"); else sb.p("null");
      if (i!=domains.length-1) sb.p(',');
      sb.nl();
      // Right now do not generate the class representing column
      // since it does not hold any interesting information except String array holding domain
      if (dom != null) {
        fileCtx.add(new CodeGenerator() {
                      @Override
                      public void generate(JCodeSB out) {
                        out.ip("// The class representing the original column ").p(_output._names[idx]).nl();
                        JCodeGen.toClassWithArray(out, null, colInfoClazz, dom);
                      }
                    }
        );
      }
    }
    sb.ip("};").nl();

    sb.nl();
    sb.ip("@Override").nl();
    sb.ip("public String[][] getOrigDomainValues() {").nl();
    sb.ii(1).ip("return ORIG_DOMAINS;").nl();
    sb.di(1).ip("}").nl();

    return sb;
  }

  private SBPrintStream toJavaPROB(SBPrintStream sb) {
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
    throw new UnsupportedOperationException("This model type does not support conversion to Java");
  }

  // Generates optional "transform" method, transform method will have a different signature depending on the algo
  // Empty by default - can be overriden by Model implementation
  protected SBPrintStream toJavaTransform(SBPrintStream ccsb,
                                          CodeGeneratorPipeline fileCtx,
                                          boolean verboseCode) { // ccsb = classContext
    return ccsb;
  }

  // Wrapper around the main predict call, including the signature and return value
  private SBPrintStream toJavaPredict(PojoWriter builder,
                                      SBPrintStream ccsb,
                                      CodeGeneratorPipeline fileCtx,
                                      boolean verboseCode) { // ccsb = classContext
    ccsb.nl();
    ccsb.ip("// Pass in data in a double[], pre-aligned to the Model's requirements.").nl();
    ccsb.ip("// Jam predictions into the preds[] array; preds[0] is reserved for the").nl();
    ccsb.ip("// main prediction (class for classifiers or value for regression),").nl();
    ccsb.ip("// and remaining columns hold a probability distribution for classifiers.").nl();
    ccsb.ip("public final double[] score0( double[] data, double[] preds ) {").nl();
    CodeGeneratorPipeline classCtx = new CodeGeneratorPipeline(); //new SB().ii(1);
    builder.toJavaPredictBody(ccsb.ii(1), classCtx, fileCtx, verboseCode);
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
    return testJavaScoring(data, model_predictions, rel_epsilon, 
            JavaScoringOptions.DEFAULT._abs_epsilon, JavaScoringOptions.DEFAULT._fraction);
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon, double abs_epsilon) {
    return testJavaScoring(data, model_predictions, rel_epsilon, abs_epsilon, 
            JavaScoringOptions.DEFAULT._fraction);
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon, double abs_epsilon, double fraction) {
    return testJavaScoring(data, model_predictions, new EasyPredictModelWrapper.Config(), rel_epsilon, abs_epsilon, fraction);
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, EasyPredictModelWrapper.Config config,
                                 double rel_epsilon, double abs_epsilon, double fraction) {
    JavaScoringOptions options = new JavaScoringOptions();
    options._abs_epsilon = abs_epsilon;
    options._fraction = fraction;
    options._config = config;
    return testJavaScoring(data, model_predictions, rel_epsilon, options);
  }
  public static class JavaScoringOptions {
    private static final JavaScoringOptions DEFAULT = new JavaScoringOptions();
    public double _abs_epsilon = 1e-15;
    public double _fraction = 1;
    public boolean _disable_pojo = false;
    public boolean _disable_mojo = false;
    EasyPredictModelWrapper.Config _config = new EasyPredictModelWrapper.Config();
  }
  public boolean testJavaScoring(Frame data, Frame model_predictions, double rel_epsilon, JavaScoringOptions options) {
    ModelBuilder<?, P, ?> mb = ModelBuilder.make(_parms.algoName().toLowerCase(), null, null);
    mb._parms = _parms;
    boolean havePojo = mb.havePojo() && !options._disable_pojo;
    boolean haveMojo = mb.haveMojo() && !options._disable_mojo;

    Random rnd = RandomUtils.getRNG(data.byteSize());
    assert data.numRows() == model_predictions.numRows();
    Frame fr = new Frame(data);
    boolean computeMetrics = data.vec(_output.responseName()) != null && !data.vec(_output.responseName()).isBad();
    try {
      String[] warns = adaptTestForJavaScoring(fr, computeMetrics);
      if( warns.length > 0 )
        System.err.println(Arrays.toString(warns));

      // Output is in the model's domain, but needs to be mapped to the scored
      // dataset's domain.
      int[] omap = null;
      if( _output.isClassifier() && model_predictions.vec(0).domain() != null) {
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
          throw new IllegalStateException("Internal POJO compilation failed",e);
        }

        // Check that POJO has the expected interfaces
        for (Class<?> clz : getPojoInterfaces())
          if (! clz.isInstance(genmodel))
            throw new IllegalStateException("POJO is expected to implement interface " + clz.getName());

        // Check some model metadata
        assert _output.responseName() == null || _output.responseName().equals(genmodel.getResponseName());

        features = MemoryManager.malloc8d(genmodel.nfeatures());
        double[] predictions = MemoryManager.malloc8d(genmodel.nclasses() + 1);

        // Compare predictions, counting mis-predicts
        final int compVecLen = _output.isBinomialClassifier() ? 3 : pvecs.length; // POJO doesn't have calibrated probs
        for (int row=0; row<fr.numRows(); row++) { // For all rows, single-threaded
          if (rnd.nextDouble() >= options._fraction) continue;
          num_total++;

          // Native Java API
          for (int col = 0; col < features.length; col++) // Build feature set
            features[col] = dvecs[col].at(row);
          genmodel.score0(features, predictions);            // POJO predictions
          for (int col = _output.isClassifier() ? 1 : 0; col < compVecLen; col++) { // Compare predictions
            double d = pvecs[col].at(row);                  // Load internal scoring predictions
            if (col == 0 && omap != null) d = omap[(int) d];  // map categorical response to scoring domain
            if (!MathUtils.compare(predictions[col], d, options._abs_epsilon, rel_epsilon)) {
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
            genmodel = MojoModel.load(filename, true);
            checkSerializable((MojoModel) genmodel);
            features = MemoryManager.malloc8d(genmodel._names.length);
          } catch (IOException e1) {
            e1.printStackTrace();
            throw new IllegalStateException("Internal MOJO loading failed", e1);
          } finally {
            boolean deleted = new File(filename).delete();
            if (!deleted) Log.warn("Failed to delete the file");
          }

          if (! Arrays.equals(model_predictions.names(), genmodel.getOutputNames())) {
            if (_parms._distribution == DistributionFamily.quasibinomial) {
              Log.warn("Quasibinomial doesn't correctly return output names in MOJO"); 
            } else if (genmodel.getModelCategory() == ModelCategory.Clustering && Arrays.equals(genmodel.getOutputNames(), new String[]{"cluster"})) {
              Log.warn("Known inconsistency between MOJO output naming and H2O predict - cluster vs predict");
            } else if (genmodel instanceof GlrmMojoModel) {
              Log.trace("GLRM is being tested for 'reconstruct', not the default score0 - dim reduction, unable to compare output names");
            } else if (false) 
              throw new IllegalStateException("GenModel output naming doesn't match provided scored frame. " +
                      "Expected: " + Arrays.toString(model_predictions.names()) +
                      ", Actual: " + Arrays.toString(genmodel.getOutputNames()));
          }
        }
        
        if (genmodel instanceof GlrmMojoModel) {
          try {
            options._config.setModel(genmodel).setEnableGLRMReconstrut(true);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }

        SharedTreeGraph[] trees = null;
        if (genmodel instanceof SharedTreeMojoModel) {
          SharedTreeMojoModel treemodel = (SharedTreeMojoModel) genmodel;
          final int ntrees = treemodel.getNTreeGroups();
          trees = new SharedTreeGraph[ntrees];
          for (int t = 0; t < ntrees; t++)
            trees[t] = treemodel.computeGraph(t);
        }

        EasyPredictModelWrapper epmw;
        try {
          options._config.setModel(genmodel)
                  .setConvertUnknownCategoricalLevelsToNa(true)
                  .setEnableLeafAssignment(genmodel instanceof SharedTreeMojoModel)
                  .setEnableStagedProbabilities(genmodel instanceof SharedTreeMojoModel)
                  .setUseExternalEncoding(true); // input Frame is already adapted!
          epmw = new EasyPredictModelWrapper(options._config);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        RowData rowData = new RowData();
        BufferedString bStr = new BufferedString();
        final int compVecLen = i == 0 && _output.isBinomialClassifier() ? 3 : pvecs.length; // POJO doesn't have calibrated probs
        for (int row = 0; row < fr.numRows(); row++) { // For all rows, single-threaded
          if (rnd.nextDouble() >= options._fraction) continue;

          if (genmodel instanceof GlrmMojoModel)  // enable random seed setting to ensure reproducibility
            ((GlrmMojoModel) genmodel)._rcnt = row;
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
            if (genmodel instanceof GlrmMojoModel)  // enable random seed setting to ensure reproducibility
              ((GlrmMojoModel) genmodel)._rcnt = row;

            if (genmodel._offsetColumn != null) {
              double offset = fr.vec(genmodel._offsetColumn).at(row);
              // TODO: MOJO API is cumbersome in this case - will be fixed in https://0xdata.atlassian.net/browse/PUBDEV-7080
              switch (genmodel.getModelCategory()) {
                case Regression:
                  p = epmw.predictRegression(rowData, offset);
                  break;
                case Binomial:
                  p = epmw.predictBinomial(rowData, offset);
                  break;
                case Multinomial:
                  p = epmw.predictMultinomial(rowData, offset);
                  break;
                case Ordinal:
                  p = epmw.predictOrdinal(rowData, offset);
                  break;
                case KLime:
                  p = epmw.predictKLime(rowData);
                  break;
                case CoxPH:
                  p = epmw.predictCoxPH(rowData, offset);
                  break;
                default:
                  throw new UnsupportedOperationException("Predicting with offset current not supported for " + genmodel.getModelCategory());
              }
            } else {
              p = epmw.predict(rowData);
            }

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
          String[] decisionPath = null;
          int[] nodeIds = null;
          for (int col = 0; col < compVecLen; col++) { // Compare predictions
            double d = pvecs[col].at(row); // Load internal scoring predictions
            if (col == 0 && omap != null) d = omap[(int) d]; // map categorical response to scoring domain
            double d2 = Double.NaN;
            switch (genmodel.getModelCategory()) {
              case AutoEncoder:
                d2 = ((AutoEncoderModelPrediction) p).reconstructed[col];
                break;
              case Clustering:
                d2 = ((ClusteringModelPrediction) p).cluster;
                break;
              case Regression:
                RegressionModelPrediction rmp = (RegressionModelPrediction) p;
                d2 = rmp.value;
                decisionPath = rmp.leafNodeAssignments;
                nodeIds = rmp.leafNodeAssignmentIds;
                break;
              case Binomial:
                BinomialModelPrediction bmp = (BinomialModelPrediction) p;
                d2 = (col == 0) ?
                        bmp.labelIndex
                        :
                        col > bmp.classProbabilities.length && bmp.calibratedClassProbabilities != null ?
                                bmp.calibratedClassProbabilities[col - bmp.classProbabilities.length - 1]
                                :
                                bmp.classProbabilities[col - 1];
                decisionPath = bmp.leafNodeAssignments;
                nodeIds = bmp.leafNodeAssignmentIds;
                break;
              case Ordinal:
                OrdinalModelPrediction orp = (OrdinalModelPrediction) p;
                d2 = (col == 0) ? orp.labelIndex : orp.classProbabilities[col - 1];
                break;
              case Multinomial:
                MultinomialModelPrediction mmp = (MultinomialModelPrediction) p;
                d2 = (col == 0) ? mmp.labelIndex : mmp.classProbabilities[col - 1];
                decisionPath = mmp.leafNodeAssignments;
                nodeIds = mmp.leafNodeAssignmentIds;
                break;
              case AnomalyDetection:
                AnomalyDetectionPrediction adp = (AnomalyDetectionPrediction) p;
                d2 = adp.toPreds()[col];
                decisionPath = adp.leafNodeAssignments;
                nodeIds = adp.leafNodeAssignmentIds;
                break;
              case DimReduction:
                d2 = (genmodel instanceof GlrmMojoModel)?((DimReductionModelPrediction) p).reconstructed[col]:
                        ((DimReductionModelPrediction) p).dimensions[col];    // look at the reconstructed matrix
                break;
              case CoxPH:
                d2 = ((CoxPHModelPrediction) p).value;
                break;
            }
            expected_preds[col] = d;
            actual_preds[col] = d2;
          }

          if (trees != null) {
            for (int t = 0; t < trees.length; t++) {
              SharedTreeGraph tree = trees[t];
              SharedTreeNode node = tree.walkNodes(0, decisionPath[t]);
              if (node == null || node.getNodeNumber() != nodeIds[t]) {
                throw new IllegalStateException("Path to leaf node is inconsistent with predicted node id: path=" + decisionPath[t] + ", nodeId=" + nodeIds[t]);
              }
            }
          }

          // Verify the correctness of the prediction
          num_total++;
          for (int col = genmodel.isClassifier() ? 1 : 0; col < compVecLen; col++) {
            if (!MathUtils.compare(actual_preds[col], expected_preds[col], options._abs_epsilon, rel_epsilon)) {
              num_errors++;
              if (num_errors < 20) {
                System.err.println( (i == 0 ? "POJO" : "MOJO") + " EasyPredict Predictions mismatch for row " + row + ":" + rowData);
                System.err.println("  Expected predictions: " + Arrays.toString(expected_preds));
                System.err.println("  Actual predictions:   " + Arrays.toString(actual_preds));
                System.err.println("Difference: " + Math.abs(expected_preds[expected_preds.length-1]-actual_preds[actual_preds.length-1]));
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
      Frame.deleteTempFrameAndItsNonSharedVecs(fr, data);  // Remove temp keys.
    }
  }
  protected String[] adaptTestForJavaScoring(Frame test, boolean computeMetrics) {
    return adaptTestForTrain(test, true, computeMetrics);
  }

  private static void checkSerializable(MojoModel mojoModel) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
         ObjectOutput out = new ObjectOutputStream(bos)) { 
      out.writeObject(mojoModel);
      out.flush();
    } catch (IOException e) {
      throw new RuntimeException("MOJO cannot be serialized", e);
    }
  }

  static <T extends Lockable<T>> int deleteAll(Key<T>[] keys) {
    int c = 0;
    for (Key k : keys) {
      T t = DKV.getGet(k);
      if (t != null) {
        t.delete(); //delete all subparts
        c++;
      }
    }
    return c;
  }

  /**
   * delete from the output all associated CV models from DKV.
   */
  public void deleteCrossValidationModels() {
    if (_output._cross_validation_models != null) {
      Log.info("Cleaning up CV Models for " + _key);
      int count = deleteAll(_output._cross_validation_models);
      Log.info(count+" CV models were removed");
    }
  }

  /**
   * delete from the output all associated CV predictions from DKV.
   */
  public void deleteCrossValidationPreds() {
    if (_output._cross_validation_predictions != null) {
      Log.info("Cleaning up CV Predictions for " + _key);
      int count = deleteAll(_output._cross_validation_predictions);
      Log.info(count+" CV predictions were removed");
    }
    Keyed.remove(_output._cross_validation_holdout_predictions_frame_id);
  }

  public void deleteCrossValidationFoldAssignment() {
    Keyed.remove(_output._cross_validation_fold_assignment_frame_id);
  }

  @Override public String toString() {
    return _output.toString();
  }

  /** Model stream writer - output Java code representation of model. */
  public class JavaModelStreamWriter implements StreamWriter {
    /** Show only preview */
    private final boolean preview;

    public JavaModelStreamWriter(boolean preview) {
      this.preview = preview;
    }

    @Override
    public void writeTo(OutputStream os, StreamWriteOption... options) {
      toJava(os, preview, true);
    }
  }

  @Override public Class<KeyV3.ModelKeyV3> makeSchema() { return KeyV3.ModelKeyV3.class; }

  public static Frame makeInteractions(Frame fr, boolean valid, InteractionPair[] interactions,
                                       final boolean useAllFactorLevels, final boolean skipMissing, final boolean standardize) {
    Vec anyTrainVec = fr.anyVec();
    Vec[] interactionVecs = new Vec[interactions.length];
    String[] interactionNames  = new String[interactions.length];
    int idx = 0;
    for (InteractionPair ip : interactions) {
      interactionNames[idx] = fr.name(ip._v1) + "_" + fr.name(ip._v2);
      boolean allFactLevels = useAllFactorLevels || ip.needsAllFactorLevels();
      InteractionWrappedVec iwv =new InteractionWrappedVec(anyTrainVec.group().addVec(), anyTrainVec._rowLayout, ip._v1Enums, ip._v2Enums, allFactLevels, skipMissing, standardize, fr.vec(ip._v1)._key, fr.vec(ip._v2)._key);
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
  public static class InteractionPair extends Iced<InteractionPair> {
    public final String  _name1, _name2;
    private int _v1,_v2;

    private String[] _v1Enums;
    private String[] _v2Enums;
    private int _hash;
    private boolean _needsAllFactorLevels;

    private InteractionPair(Frame f, int v1, int v2, String[] v1Enums, String[] v2Enums) {
      _name1 = f.name(v1);
      _name2 = f.name(v2);
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
     * Indicates that Interaction should be created from all factor levels
     * (regardless of the global setting useAllFactorLevels).
     * @return do we need to make all factor levels?
     */
    public boolean needsAllFactorLevels() { return _needsAllFactorLevels; }
    public void setNeedsAllFactorLevels(boolean needsAllFactorLevels) { _needsAllFactorLevels = needsAllFactorLevels; }

    /**
     * Generate all pairwise combinations of the arguments.
     * @param indexes An array of column indices.
     * @return An array of interaction pairs
     */
    public static InteractionPair[] generatePairwiseInteractionsFromList(Frame f, int... indexes) {
      if( null==indexes ) return null;
      if( indexes.length < 2 ) {
        if( indexes.length==1 && indexes[0]==-1 ) return null;
        throw new IllegalArgumentException("Must supply 2 or more columns.");
      }
      InteractionPair[] res = new InteractionPair[ (indexes.length-1)*(indexes.length)>>1]; // n*(n+1) / 2
      int idx=0;
      for(int i=0;i<indexes.length;++i)
        for(int j=i+1;j<indexes.length;++j)
          res[idx++] = new InteractionPair(f, indexes[i],indexes[j],f.vec(indexes[i]).domain(),f.vec(indexes[j]).domain());
      return res;
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
    public int getV1() { return _v1; }
    public int getV2() { return _v2; }

    public boolean isNumeric() {
      return _v1Enums == null && _v2Enums == null;
    }
  }

  /**
   * Imports a binary model from a given location.
   * Note: binary model has to be created by the same version of H2O, import of a model from a different version will fail
   * @param location path to the binary representation of the model on a local filesystem, HDFS, S3...
   * @return instance of an H2O Model
   * @throws IOException when reading fails
   */
  public static <M extends Model<?, ?, ?>> M importBinaryModel(String location) throws IOException {
    InputStream is = null;
    try {
      URI targetUri = FileUtils.getURI(location);
      Persist p = H2O.getPM().getPersistForURI(targetUri);
      is = p.open(targetUri.toString());
      final AutoBuffer ab = new AutoBuffer(is);
      ab.sourceName = targetUri.toString();
      @SuppressWarnings("unchecked")
      M model = (M) Keyed.readAll(ab);
      Keyed.readAll(ab); // CV holdouts frame 
      ab.close();
      is.close();
      return model;
    } finally {
      FileUtils.closeSilently(is);
    }
  }

    /**
     * Uploads a binary model from a given frame.
     * Note: binary model has to be created by the same version of H2O, import of a model from a different version will fail
     * @param destinationFrame key of the frame containing the binary representation of the model on a local filesystem, HDFS, S3...
     * @return instance of an H2O Model
     * @throws IOException when reading fails
     */
    public static <M extends Model<?, ?, ?>> M uploadBinaryModel(String destinationFrame) throws IOException {
      Frame fr = DKV.getGet(destinationFrame);
      ByteVec vec = (ByteVec) fr.vec(0);
      try (InputStream inputStream = vec.openStream(null)) {
          final AutoBuffer ab = new AutoBuffer(inputStream);
          @SuppressWarnings("unchecked")
          M model = (M) Keyed.readAll(ab);
          Keyed.readAll(ab); // CV holdouts frame 
          ab.close();
          return model;
        } 
    }
    
  /**
   * Exports a binary model to a given location.
   * @param location target path, it can be on local filesystem, HDFS, S3...
   * @param force If true, overwrite already existing file
   * @return URI representation of the target location
   * @throws water.api.FSIOException when writing fails
   */
  public final URI exportBinaryModel(String location, boolean force, ModelExportOption... options) throws IOException {
    OutputStream os = null;
    try {
      URI targetUri = FileUtils.getURI(location);
      Persist p = H2O.getPM().getPersistForURI(targetUri);
      os = p.create(targetUri.toString(), force);
      writeTo(os, options);
      os.close();
      return targetUri;
    } finally {
      FileUtils.closeSilently(os);
    }
  }

  @Override
  public final void writeTo(OutputStream os, StreamWriteOption... options) {
    try (AutoBuffer ab = new AutoBuffer(os, true)) {
      writeAll(ab);
      Frame holdoutFrame = null;
      if (ArrayUtils.contains(options, ModelExportOption.INCLUDE_CV_PREDICTIONS) 
              && _output._cross_validation_holdout_predictions_frame_id != null) {
        holdoutFrame = DKV.getGet(_output._cross_validation_holdout_predictions_frame_id);
        if (holdoutFrame == null)
            Log.warn("CV holdout predictions frame is no longer available and won't be exported in the binary model file.");
      }
      if (holdoutFrame != null) {
        holdoutFrame.writeAll(ab);
      } else {
        ab.put(null); // mark no holdout preds
      }
    }
  }
  
  /**
   * Exports a MOJO representation of a model to a given location.
   * @param location target path, it can be on local filesystem, HDFS, S3...
   * @param force If true, overwrite already existing file
   * @return URI representation of the target location
   * @throws IOException when writing fails
   */
  public URI exportMojo(String location, boolean force) throws IOException {
    if (! haveMojo())
      throw new IllegalStateException("Model doesn't support MOJOs.");
    OutputStream os = null;
    try {
      URI targetUri = FileUtils.getURI(location);
      Persist p = H2O.getPM().getPersistForURI(targetUri);
      os = p.create(targetUri.toString(), force);
      ModelMojoWriter mojo = getMojo();
      mojo.writeTo(os);
      os.close();
      return targetUri;
    } finally {
      FileUtils.closeSilently(os);
    }
  }

  /**
   * Convenience method to convert Model to a MOJO representation. Please be aware that converting models
   * to MOJOs using this function will require sufficient memory (to hold the mojo representation and interim
   * serialized representation as well).
   *
   * @return instance of MojoModel
   * @throws IOException when writing MOJO fails
   */
  public MojoModel toMojo() throws IOException {
    MojoReaderBackend mojoReaderBackend = convertToInMemoryMojoReader();
    return MojoModel.load(mojoReaderBackend);
  }

  /**
   * Convenience method to convert Model to a MOJO representation. Please be aware that converting models
   * to MOJOs using this function will require sufficient memory (to hold the mojo representation and interim
   * serialized representation as well).
   *
   * @param readMetadata If true, parses also model metadata (model performance metrics... {@link ModelAttributes})
   *                     Model metadata are not required for scoring, it is advised to leave this option disabled
   *                     if you want to use MOJO for inference only.
   * @return instance of MojoModel
   * @throws IOException when writing MOJO fails
   */
  public MojoModel toMojo(boolean readMetadata) throws IOException {
    MojoReaderBackend mojoReaderBackend = convertToInMemoryMojoReader();
    return ModelMojoReader.readFrom(mojoReaderBackend, readMetadata);
  }
  
  MojoReaderBackend convertToInMemoryMojoReader() throws IOException {
    if (! haveMojo())
      throw new IllegalStateException("Model doesn't support MOJOs.");
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      this.getMojo().writeTo(os);
      return MojoReaderBackendFactory.createReaderBackend(
              new ByteArrayInputStream(os.toByteArray()), MojoReaderBackendFactory.CachingStrategy.MEMORY);
    }
  }

  public ModelDescriptor modelDescriptor() {
    return new H2OModelDescriptor();
  }

  protected class H2OModelDescriptor implements ModelDescriptor {
      @Override
      public String[][] scoringDomains() { return Model.this.scoringDomains(); }
      @Override
      public String projectVersion() { return H2O.ABV.projectVersion(); }
      @Override
      public String algoName() { return _parms.algoName(); }
      @Override
      public String algoFullName() { return _parms.fullName(); }
      @Override
      public String offsetColumn() { return _output.offsetName(); }
      @Override
      public String weightsColumn() { return _output.weightsName(); }
      @Override
      public String foldColumn() { return _output.foldName(); }
      @Override
      public ModelCategory getModelCategory() { return _output.getModelCategory(); }
      @Override
      public boolean isSupervised() { return _output.isSupervised(); }
      @Override
      public int nfeatures() { return _output.nfeatures(); }
      @Override
      public String[] features() { return _output.features(); }
      @Override
      public int nclasses() { return _output.nclasses(); }
      @Override
      public String[] columnNames() { return _output._names; }
      @Override
      public boolean balanceClasses() { return _parms._balance_classes; }
      @Override
      public double defaultThreshold() { return Model.this.defaultThreshold(); }
      @Override
      public double[] priorClassDist() { return _output._priorClassDist; }
      @Override
      public double[] modelClassDist() { return _output._modelClassDist; }
      @Override
      public String uuid() { return String.valueOf(Model.this.checksum()); }
      @Override
      public String timestamp() { return new DateTime().toString(); }
      @Override
      public String[] getOrigNames() { return _output._origNames; }
      @Override
      public String[][] getOrigDomains() { return _output._origDomains; }
  }

  /**
   * Convenience method to find out if featureName is used for prediction, i.e., if it has beta == 0 in GLM,
   * it is not considered to be used.
   * This is mainly intended for optimizing prediction speed in StackedEnsemble.
   * @param featureName
   */
  public boolean isFeatureUsedInPredict(String featureName) {
    if (featureName.equals(_parms._response_column)) return false;
    int featureIdx = ArrayUtils.find(_output._names, featureName);
    if (featureIdx == -1) {
      return false;
    }
    return isFeatureUsedInPredict(featureIdx);
  }

  protected boolean isFeatureUsedInPredict(int featureIdx) {
    return true;
  }

  public boolean isDistributionHuber() {
    return _parms._distribution == DistributionFamily.huber;
  }
}
