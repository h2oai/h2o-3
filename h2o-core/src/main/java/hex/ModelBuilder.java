package hex;

import hex.schemas.ModelBuilderSchema;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
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

  private transient Vec _weights;
  private Key  _weights_key;

  public boolean hasOffset(){return false;}
  public Vec offset(){return null;}
  public boolean hasWeights(){return _weights_key != null;}
  public Vec weights() {
    if(_weights_key == null) return null;
    return _weights == null?DKV.<Vec>getGet(_weights_key):_weights;
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


  // put special vecs at the end
  // return number of special vecs
  protected int reorderVecs() {
    if(_parms._weights_column != null) {
      Vec w = _train.remove(_parms._weights_column);
      if(w == null)
        error("_weights_column","Weights column '" + _parms._weights_column  + "' not found in the training frame");
      else {// add offset to the end
        _weights = w;
        _weights_key = w._key;
        _train.add(_parms._weights_column, w);
        return 1;
      }
    }
    return 0;
  }

  protected void ignoreConstColumns(int npredictors, boolean expensive){
    // Drop all-constant and all-bad columns.
    if( _parms._ignore_const_cols)
      new FilterCols(npredictors) {
        @Override protected boolean filter(Vec v) { return v.isConst() || v.isBad(); }
      }.doIt(_train,"Dropping constant columns: ",expensive);
  }
  /**
   * Override this method to call error() if the model is expected to not fit in memory, and say why
   */
  protected void checkMemoryFootPrint() {}

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



    /*
    We now do this only through Rapids.  There should be an easy way to do it through the Java API for Sparkling Water users.
    if( _parms._drop_na20_cols )
      new FilterCols() {
        @Override protected boolean filter(Vec v) { return ((float)v.naCnt() / v.length()) > 0.2; }
      }.doIt(_train,"Dropping columns with too many missing values: ",expensive);
    */

    // Drop all non-numeric columns (e.g., String and UUID).  No current algo
    // can use them, and otherwise all algos will then be forced to remove
    // them.  Text algos (grep, word2vec) take raw text columns - which are
    // numeric (arrays of bytes).
    ignoreConstColumns(reorderVecs(),expensive);
    // Check that at least some columns are not-constant and not-all-NAs
    if( _train.numCols() == 0 )
      error("_train","There are no usable columns to generate model");


    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap enums
    Frame va = _parms.valid();  // User-given validation set
    if (va != null)
      _valid = new Frame(null /* not putting this into KV */, va._names.clone(), va.vecs().clone());
    try {
      String[] msgs = Model.adaptTestForTrain(_train._names,_parms._weights_column, _parms._offset_column, null,_train.domains(),_valid,_parms.missingColumnsType(),expensive);
      if( expensive ) {
        for( String s : msgs ) {
          Log.info(s);
          info("_valid", s);
        }
      }
    } catch( IllegalArgumentException iae ) {
      error("_valid",iae.getMessage());
    }
    assert !expensive || (_valid == null || Arrays.equals(_train._names,_valid._names));
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
