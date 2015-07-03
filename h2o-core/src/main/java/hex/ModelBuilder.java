package hex;

import hex.schemas.ModelBuilderSchema;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.*;
import water.util.FrameUtils;
import water.util.Log;
import water.util.MRUtils;
import water.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Job<M> {

  /** All the parameters required to build the model. */
  public final P _parms;

  /** Training frame: derived from the parameter's training frame, excluding
   *  all ignored columns, all constant and bad columns, perhaps flipping the
   *  response column to an Categorical, etc.  */
  public final Frame train() { return _train; }
  protected transient Frame _train;

  /** Validation frame: derived from the parameter's validation frame, excluding
   *  all ignored columns, all constant and bad columns, perhaps flipping the
   *  response column to a Categorical, etc.  Is null if no validation key is set.  */
  public final Frame valid() { return _valid; }
  protected transient Frame _valid;

  // TODO: tighten up the type
  // Map the algo name (e.g., "deeplearning") to the builder class (e.g., DeepLearning.class) :
  private static final Map<String, Class<? extends ModelBuilder>> _builders = new HashMap<>();

  // Map the Model class (e.g., DeepLearningModel.class) to the algo name (e.g., "deeplearning"):
  private static final Map<Class<? extends Model>, String> _model_class_to_algo = new HashMap<>();

  // Map the simple algo name (e.g., deeplearning) to the full algo name (e.g., "Deep Learning"):
  private static final Map<String, String> _algo_to_algo_full_name = new HashMap<>();

  // Map the algo name (e.g., "deeplearning") to the Model class (e.g., DeepLearningModel.class):
  private static final Map<String, Class<? extends Model>> _algo_to_model_class = new HashMap<>();

  /** Train response vector. */
  public Vec response(){return _response;}
  /** Validation response vector. */
  public Vec vresponse(){return _vresponse;}

  /**
   * Compute the (weighted) mean of the response (subtracting possible offset terms)
   * @return mean
   */
  protected double responseMean() {
    if (hasWeights() || hasOffset()) {
      return new FrameUtils.WeightedMean().doAll(
              _response,
              hasWeights() ? _weights : _response.makeCon(1),
              hasOffset() ? _offset : _response.makeCon(0)
      ).weightedMean();
    }
    return _response.mean();
  }



  /**
   * Register a ModelBuilder, assigning it an algo name.
   */
  public static void registerModelBuilder(String name, String full_name, Class<? extends ModelBuilder> clz) {
    _builders.put(name, clz);

    Class<? extends Model> model_class = (Class<? extends Model>)ReflectionUtils.findActualClassParameter(clz, 0);
    _model_class_to_algo.put(model_class, name);
    _algo_to_algo_full_name.put(name, full_name);
    _algo_to_model_class.put(name, model_class);
  }

  /** Get a Map of all algo names to their ModelBuilder classes. */
  public static Map<String, Class<? extends ModelBuilder>>getModelBuilders() { return _builders; }

  /** Get the ModelBuilder class for the given algo name. */
  public static Class<? extends ModelBuilder> getModelBuilder(String name) {
    return _builders.get(name);
  }

  /** Get the Model class for the given algo name. */
  public static Class<? extends Model> getModelClass(String name) {
    return _algo_to_model_class.get(name);
  }

  /** Get the algo name for the given Model. */
  public static String getAlgo(Model model) {
    return _model_class_to_algo.get(model.getClass());
  }

  /** Get the algo full name for the given algo. */
  public static String getAlgoFullName(String algo) {
    return _algo_to_algo_full_name.get(algo);
  }

  public String getAlgo() {
    return getAlgo(this.getClass());
  }

  public static String getAlgo(Class<? extends ModelBuilder> clz) {
    // Check for unknown algo names, but if none are registered keep going; we're probably in JUnit.
    if (_builders.isEmpty())
      return "Unknown algo (should only happen under JUnit)";

    if (! _builders.containsValue(clz))
      throw new H2OIllegalArgumentException("Failed to find ModelBuilder class in registry: " + clz, "Failed to find ModelBuilder class in registry: " + clz);

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : _builders.entrySet())
      if (entry.getValue().equals(clz))
        return entry.getKey();
    // Note: unreachable:
    throw new H2OIllegalArgumentException("Failed to find ModelBuilder class in registry: " + clz, "Failed to find ModelBuilder class in registry: " + clz);
  }

  /**
   * Externally visible default schema
   * TODO: this is in the wrong layer: the internals should not know anything about the schemas!!!
   * This puts a reverse edge into the dependency graph.
   */
  public abstract ModelBuilderSchema schema();

  /** Constructor called from an http request; MUST override in subclasses. */
  public ModelBuilder(P ignore) {
    super(Key.make("Failed"),"ModelBuilder constructor needs to be overridden.");
    throw H2O.fail("ModelBuilder subclass failed to override the params constructor: " + this.getClass());
  }

  /** Constructor making a default destination key */
  public ModelBuilder(String desc, P parms) {
    this((parms == null || parms._model_id == null) ? Key.make(desc + "Model_" + Key.rand()) : parms._model_id, desc, parms);
  }

  /** Default constructor, given all arguments */
  public ModelBuilder(Key dest, String desc, P parms) {
    super(dest,desc);
    _parms = parms;
  }

  /** Factory method to create a ModelBuilder instance of the correct class given the algo name. */
  public static ModelBuilder createModelBuilder(String algo) {
    ModelBuilder modelBuilder;

    Class<? extends ModelBuilder> clz = null;
    try {
      clz = ModelBuilder.getModelBuilder(algo);
    }
    catch (Exception ignore) {}

    if (clz == null) {
      throw new H2OIllegalArgumentException("algo", "createModelBuilder", "Algo not known (" + algo + ")");
    }

    try {
      if (! (clz.getGenericSuperclass() instanceof ParameterizedType)) {
        throw H2O.fail("Class is not parameterized as expected: " + clz);
      }

      Type[] handler_type_parms = ((ParameterizedType)(clz.getGenericSuperclass())).getActualTypeArguments();
      // [0] is the Model type; [1] is the Model.Parameters type; [2] is the Model.Output type.
      Class<? extends Model.Parameters> pclz = (Class<? extends Model.Parameters>)handler_type_parms[1];
      Constructor<ModelBuilder> constructor = (Constructor<ModelBuilder>)clz.getDeclaredConstructor(new Class[] { (Class)handler_type_parms[1] });
      Model.Parameters p = pclz.newInstance();
      modelBuilder = constructor.newInstance(p);
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + algo + ": " + e.getCause(), e);
    } catch (Exception e) {
      throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + algo + ": " + e.getCause(), e);
    }

    return modelBuilder;
  }

  /** Method to launch training of a Model, based on its parameters. */
  abstract public Job<M> trainModel();

  /** List containing the categories of models that this builder can
   *  build.  Each ModelBuilder must have one of these. */
  abstract public ModelCategory[] can_build();

  /**
   * Visibility for this algo: is it always visible, is it beta (always visible but with a note in the UI)
   * or is it experimental (hidden by default, visible in the UI if the user gives an "experimental" flag
   * at startup).
   */
  public enum BuilderVisibility {
    Experimental,
    Beta,
    Stable
  }

  /**
   * Visibility for this algo: is it always visible, is it beta (always visible but with a note in the UI)
   * or is it experimental (hidden by default, visible in the UI if the user gives an "experimental" flag
   * at startup).
   */
  abstract public BuilderVisibility builderVisibility();

  /** Clear whatever was done by init() so it can be run again. */
  public void clearInitState() {
    clearValidationErrors();

  }

  public boolean isSupervised(){return false;}

  protected transient Vec _response; // Handy response column
  protected transient Vec _vresponse; // Handy response column
  protected transient Vec _offset; // Handy offset column
  protected transient Vec _weights;

  public boolean hasOffset(){ return _offset != null;}
  public boolean hasWeights(){return _weights != null;}
  // no hasResponse, call isSupervised instead (response is mandatory if isSupervised is true)

  protected int _nclass; // Number of classes; 1 for regression; 2+ for classification

  public int nclasses(){return _nclass;}

  public final boolean isClassifier() { return _nclass > 1; }

  /**
   * Find and set response/weights/offset and put them all in the end,
   * @return number of non-feature vecs
   */
  protected int separateFeatureVecs() {
    int res = 0;
    if(_parms._weights_column != null) {
      Vec w = _train.remove(_parms._weights_column);
      if(w == null)
        error("_weights_column","Offset column '" + _parms._weights_column  + "' not found in the training frame");
      else {
        if(!w.isNumeric())
          error("_weights_column","Invalid weights column '" + _parms._weights_column  + "', weights must be numeric");
        _weights = w;
        if(w.naCnt() > 0)
          error("_weights_columns","Weights cannot have missing values.");
        if(w.min() < 0)
          error("_weights_columns","Weights must be >= 0");
        if(w.max() == 0)
          error("_weights_columns","Max. weight must be > 0");
        _train.add(_parms._weights_column, w);
        ++res;
      }
    }
    if(_parms._offset_column != null) {
      Vec o = _train.remove(_parms._offset_column);
      if(o == null)
        error("_offset_column","Offset column '" + _parms._offset_column  + "' not found in the training frame");
      else {
        if(!o.isNumeric())
          error("_offset_column","Invalid offset column '" + _parms._offset_column  + "', offset must be numeric");
        _offset = o;
        if(o.naCnt() > 0)
          error("_offset_column","Offset cannot have missing values.");
        if(_weights == _offset)
          error("_offset_column", "Offset must be different from weights");
        _train.add(_parms._offset_column, o);
        ++res;
      }
    }
    if(isSupervised() && _parms._response_column != null) {
      _response = _train.remove(_parms._response_column);
      if (_response == null) {
        if (isSupervised())
          error("_response_column", "Response column '" + _parms._response_column + "' not found in the training frame");
      } else {
        _train.add(_parms._response_column, _response);
        ++res;
      }
    }
    return res;
  }

  protected  boolean ignoreStringColumns(){return true;}

  /**
   * Ignore constant columns, columns with all NAs and strings.
   * @param npredictors
   * @param expensive
   */
  protected void ignoreBadColumns(int npredictors, boolean expensive){
    // Drop all-constant and all-bad columns.
    if( _parms._ignore_const_cols)
      new FilterCols(npredictors) {
        @Override protected boolean filter(Vec v) { return v.isConst() || v.isBad() || (ignoreStringColumns() && v.isString()); }
      }.doIt(_train,"Dropping constant columns: ",expensive);
  }
  /**
   * Override this method to call error() if the model is expected to not fit in memory, and say why
   */
  protected void checkMemoryFootPrint() {}


  transient double [] _distribution;
  transient double [] _priorClassDist;

  protected boolean computePriorClassDistribution(){
    return _parms._balance_classes;
  }
  // ==========================================================================
  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made by
   *  the front-end whenever the GUI is clicked, and needs to be fast whenever
   *  {@code expensive} is false; it will be called once again at the start of
   *  model building {@see #trainModel()} with expensive set to true.
   *<p>
   *  The incoming training frame (and validation frame) will have ignored
   *  columns dropped out, plus whatever work the parent init did.
   *<p>
   *  NOTE: The front end initially calls this through the parameters validation
   *  endpoint with no training_frame, so each subclass's {@code init()} method
   *  has to work correctly with the training_frame missing.
   *<p>
   *  @see #updateValidationMessages()
   */
  public void init(boolean expensive) {
    // Log parameters
    if (expensive) {
      Log.info("Building H2O " + this.getClass().getSimpleName().toString() + " model with these parameters:");
      Log.info(new String(_parms.writeJSON(new AutoBuffer()).buf()));
    }


    // NOTE: allow re-init:
    clearInitState();
    assert _parms != null;      // Parms must already be set in
    if( _parms._train == null ) {
      if (expensive)
        error("_train","Missing training frame");
      return;
    }
    Frame tr = _parms.train();
    if( tr == null ) { error("_train","Missing training frame: "+_parms._train); return; }
    _train = new Frame(null /* not putting this into KV */, tr._names.clone(), tr.vecs().clone());

    // Drop explicitly dropped columns
    if( _parms._ignored_columns != null ) {
      _train.remove(_parms._ignored_columns);
      if( expensive ) Log.info("Dropping ignored columns: "+Arrays.toString(_parms._ignored_columns));
    }

    // Drop all non-numeric columns (e.g., String and UUID).  No current algo
    // can use them, and otherwise all algos will then be forced to remove
    // them.  Text algos (grep, word2vec) take raw text columns - which are
    // numeric (arrays of bytes).
    ignoreBadColumns(separateFeatureVecs(), expensive);
    // Check that at least some columns are not-constant and not-all-NAs
    if( _train.numCols() == 0 )
      error("_train","There are no usable columns to generate model");

    if(isSupervised()) {

      if(_response != null) {
        _nclass = _response.isEnum() ? _response.cardinality() : 1;
        if (_response.isConst())
          error("_response","Response cannot be constant.");
      }
      if (! _parms._balance_classes)
        hide("_max_after_balance_size", "Balance classes is false, hide max_after_balance_size");
      else if (_parms._weights_column != null)
        error("_balance_classes", "Balance classes and observation weights are not currently supported together.");
      if( _parms._max_after_balance_size <= 0.0 )
        error("_max_after_balance_size","Max size after balancing needs to be positive, suggest 1.0f");

      if( _train != null ) {
        if (_train.numCols() <= 1)
          error("_train", "Training data must have at least 2 features (incl. response).");
        if( null == _parms._response_column) {
          error("_response_column", "Response column parameter not set.");
          return;
        }

        if(_response != null && computePriorClassDistribution()) {
          if (isClassifier() && isSupervised()) {
            MRUtils.ClassDist cdmt =
                _weights != null ? new MRUtils.ClassDist(nclasses()).doAll(_response, _weights) : new MRUtils.ClassDist(nclasses()).doAll(_response);
            _distribution = cdmt.dist();
            _priorClassDist = cdmt.rel_dist();
          } else {                    // Regression; only 1 "class"
            _distribution = new double[]{ (_weights != null ? _weights.mean() : 1.0) * train().numRows() };
            _priorClassDist = new double[]{1.0f};
          }
        }
      }

      if( !isClassifier() ) {
        hide("_balance_classes", "Balance classes is only applicable to classification problems.");
        hide("_class_sampling_factors", "Class sampling factors is only applicable to classification problems.");
        hide("_max_after_balance_size", "Max after balance size is only applicable to classification problems.");
        hide("_max_confusion_matrix_size", "Max confusion matrix size is only applicable to classification problems.");
      }
      if (_nclass <= 2) {
        hide("_max_hit_ratio_k", "Max K-value for hit ratio is only applicable to multi-class classification problems.");
        hide("_max_confusion_matrix_size", "Only for multi-class classification problems.");
      }
      if( !_parms._balance_classes ) {
        hide("_max_after_balance_size", "Only used with balanced classes");
        hide("_class_sampling_factors", "Class sampling factors is only applicable if balancing classes.");
      }
    }
    else {
      hide("_response_column", "Ignored for unsupervised methods.");
      hide("_balance_classes", "Ignored for unsupervised methods.");
      hide("_class_sampling_factors", "Ignored for unsupervised methods.");
      hide("_max_after_balance_size", "Ignored for unsupervised methods.");
      hide("_max_confusion_matrix_size", "Ignored for unsupervised methods.");
      _response = null;
      _vresponse = null;
      _nclass = 1;
    }

    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap enums
    Frame va = _parms.valid();  // User-given validation set
    if (va != null) {
      _valid = new Frame(null /* not putting this into KV */, va._names.clone(), va.vecs().clone());
      try {
        String[] msgs = Model.adaptTestForTrain(_train._names, _parms._weights_column, _parms._offset_column, null, _train.domains(), _valid, _parms.missingColumnsType(), expensive, true);
        _vresponse = _valid.vec(_parms._response_column);
        if (_vresponse == null && _parms._response_column != null)
          error("_validation_frame", "Validation frame must have a response column '" + _parms._response_column + "'.");
        if (expensive) {
          for (String s : msgs) {
            Log.info(s);
            info("_valid", s);
          }
        }
        assert !expensive || (_valid == null || Arrays.equals(_train._names, _valid._names));
      } catch (IllegalArgumentException iae) {
        error("_valid", iae.getMessage());
      }
    }
  }

  /**
   * init(expensive) is called inside a DTask, not from the http request thread.  If we add validation messages to the
   * ModelBuilder (aka the Job) we want to update it in the DKV so the client can see them when polling and later on
   * after the job completes.
   * <p>
   * NOTE: this should only be called when no other threads are updating the job, for example from init() or after the
   * DTask is stopped and is getting cleaned up.
   * @see #init(boolean)
   */
  public void updateValidationMessages() {
    // Atomically update the validation messages in the Job in the DKV.

    // In some cases we haven't stored to the DKV yet:
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        if( old == null ) throw new H2OKeyNotFoundArgumentException(old._key);

        ModelBuilder builder = (ModelBuilder)old;
        builder._messages = ModelBuilder.this._messages;
        return builder;
      }
      // Run the onCancelled code synchronously, right now
      @Override public void onSuccess( Job old ) { if( isCancelledOrCrashed() ) onCancelled(); }
    }.invoke(_key);
    }

  abstract class FilterCols {
    final int _specialVecs; // special vecs to skip at the end
    public FilterCols(int n) {_specialVecs = n;}

    abstract protected boolean filter(Vec v);

    void doIt( Frame f, String msg, boolean expensive ) {
      boolean any=false;
      for( int i = 0; i < f.vecs().length - _specialVecs; i++ ) {
        if( filter(f.vecs()[i]) ) {
          if( any ) msg += ", "; // Log dropped cols
          any = true;
          msg += f._names[i];
          f.remove(i);
          i--; // Re-run at same iteration after dropping a col
        }
      }
      if( any ) {
        warn("_train", msg);
        if (expensive) Log.info(msg);
      }
    }
  }

  /** A list of field validation issues. */
  public ValidationMessage[] _messages = new ValidationMessage[0];
  private int _error_count = -1; // -1 ==> init not run yet; note, this counts ONLY errors, not WARNs and etc.
  public int error_count() { assert _error_count>=0 : "init() not run yet"; return _error_count; }
  public void hide (String field_name, String message) { message(ValidationMessage.MessageType.HIDE , field_name, message); }
  public void info (String field_name, String message) { message(ValidationMessage.MessageType.INFO , field_name, message); }
  public void warn (String field_name, String message) { message(ValidationMessage.MessageType.WARN , field_name, message); }
  public void error(String field_name, String message) { message(ValidationMessage.MessageType.ERROR, field_name, message); _error_count++; }
  private void clearValidationErrors() {
    _messages = new ValidationMessage[0];
    _error_count = 0;
  }
  private void message(ValidationMessage.MessageType message_type, String field_name, String message) {
    _messages = Arrays.copyOf(_messages, _messages.length + 1);
    _messages[_messages.length - 1] = new ValidationMessage(message_type, field_name, message);
  }
  /** Get a string representation of only the ERROR ValidationMessages (e.g., to use in an exception throw). */
  public String validationErrors() {
    StringBuilder sb = new StringBuilder();
    for( ValidationMessage vm : _messages )
      if( vm.message_type == ValidationMessage.MessageType.ERROR )
        sb.append(vm.toString()).append("\n");
    return sb.toString();
  }

  /** The result of an abnormal Model.Parameter check.  Contains a
   *  level, a field name, and a message.
   *
   *  Can be an ERROR, meaning the parameters can't be used as-is,
   *  a HIDE, which means the specified field should be hidden given
   *  the values of other fields, or a WARN or INFO for informative
   *  messages to the user.
   */
  public static final class ValidationMessage extends Iced {
    public enum MessageType { HIDE, INFO, WARN, ERROR }
    final MessageType message_type;
    final String field_name;
    final String message;

    public ValidationMessage(MessageType message_type, String field_name, String message) {
      this.message_type = message_type;
      this.field_name = field_name;
      this.message = message;
      switch (message_type) {
        case INFO: Log.info(field_name + ": " + message); break;
        case WARN: Log.warn(field_name + ": " + message); break;
        case ERROR: Log.err(field_name + ": " + message); break;
      }
    }

    @Override public String toString() { return message_type + " on field: " + field_name + ": " + message; }
  }

}
