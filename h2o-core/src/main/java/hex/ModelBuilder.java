package hex;

import hex.schemas.ModelBuilderSchema;
import jsr166y.CountedCompleter;
import water.*;
import water.rapids.ASTKFold;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.Log;
import water.util.MRUtils;
import water.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Job<M> {

  /** All the parameters required to build the model. */
  public P _parms;

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

  private Key[] cvModelBuilderKeys;

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
    if (hasWeightCol() || hasOffsetCol()) {
      return new FrameUtils.WeightedMean().doAll(
              _response,
              hasWeightCol() ? _weights : _response.makeCon(1),
              hasOffsetCol() ? _offset : _response.makeCon(0)
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
    super(Key.<M>make("Failed"), "ModelBuilder constructor needs to be overridden.");
    throw H2O.fail("ModelBuilder subclass failed to override the params constructor: " + this.getClass());
  }

  /** Constructor making a default destination key */
  public ModelBuilder(String desc, P parms) {
    this((parms == null || parms._model_id == null) ? Key.make(H2O.calcNextUniqueModelId(desc)) : parms._model_id, desc, parms);
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

  /**
   * Temporary HACK to store the ModelBuilders's state and start/end/run time in the model's output
   * This won't be necessary once both the ModelBuilder and the Model point to a shared Job(State) object in the DKV.
   * Currently, there's a slight delay between setting the ModelBuilder/Job's state and setting the model's state.
   * So there is a race condition when returning a model (e.g., via the REST layer) after the ModelBuilder is DONE, but the model object is not yet updated.
   */
  protected void updateModelOutput() {
    new TAtomic<M>() {
      @Override
      public M atomic(M old) {
        if (old != null) {
          old._output._status = _state;
          old._output._start_time = _start_time;
          old._output._end_time = _end_time;
          old._output._run_time = _end_time - _start_time;
        }
        return old;
      }
    }.invoke(dest());
  }

  /** Method to launch training of a Model, based on its parameters. */
  final public Job<M> trainModel() {
    if (error_count() > 0) {
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    }
    if(!nFoldCV()) {
      return trainModelImpl(progressUnits(), true);
    } else {
      int work;
      if (_parms._fold_column != null) {
        Vec fc = train().vec(_parms._fold_column);
        work = ((int)fc.max()-(int)fc.min()) + 1;
      } else {
        work = _parms._nfolds + 1;
      }
      // cross-validation needs to be forked off to allow continuous (non-blocking) progress bar
      return start(new H2O.H2OCountedCompleter() {
        @Override protected void compute2() {
          computeCrossValidation();
          tryComplete();
        }
        @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
          failed(ex);
          return true;
        }
      }, work * progressUnits(), true);
    }
  }

  /**
   * Model-specific implementation of model training
   * @param progressUnits Number of progress units (each advances the Job's progress bar by a bit)
   * @param restartTimer
   * @return ModelBuilder job
   */
  abstract protected Job<M> trainModelImpl(long progressUnits, boolean restartTimer);
  abstract protected long progressUnits();

  /**
   * Whether the Job is done after building the model itself, or whether there's extra work to be done
   * Override the Job's behavior here
   * N-fold CV jobs should not mark the job as finished, we do this explicitly in computeCrossValidation
   *
   * @return
   */
  @Override
  protected boolean canBeDone() {
    return !nFoldCV();
  }

  @Override
  public void cancel() {
    super.cancel();
    // parent job cancels all running CV child jobs
    if (cvModelBuilderKeys != null) {
      for (int i = 0; i < cvModelBuilderKeys.length; ++i) {
        ModelBuilder<M, P, O> mb = DKV.getGet(cvModelBuilderKeys[i]);
        if (mb != null) {
          assert (mb.cvModelBuilderKeys == null); //prevent infinite recursion
          mb.cancel();
        }
      }
    }
  }

  /**
   * Default naive (serial) implementation of N-fold cross-validation
   * @return Cross-validation Job
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  public Job<M> computeCrossValidation() {
    assert(_state == JobState.RUNNING); //main Job is still running
    final Frame origTrainFrame = train();

    // Step 1: Assign each row to a fold
    // TODO: Implement better splitting algo (with Strata if response is categorical), e.g. http://www.lexjansen.com/scsug/2009/Liang_Xie2.pdf
    Vec foldAssignment;

    final Integer N;
    if (_parms._fold_column != null) {
      foldAssignment = origTrainFrame.vec(_parms._fold_column);
      N = (int)foldAssignment.max() - (int)foldAssignment.min() + 1;
      assert(N>1); //should have been already checked in init();
    } else {
      N = _parms._nfolds;
      long seed = new Random().nextLong();
      for (Field f : _parms.getClass().getFields()) {
        if (f.getName().equals("_seed")) {
          try {
            seed = (long)(f.get(_parms));
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      }
      Log.info("Creating " + N + " cross-validation splits with random number seed: " + seed);
      foldAssignment = origTrainFrame.anyVec().makeZero();
      final Model.Parameters.FoldAssignmentScheme foldAssignmentScheme = _parms._fold_assignment;
      switch(foldAssignmentScheme) {
        case AUTO:
        case Random:
          foldAssignment = ASTKFold.kfoldColumn(foldAssignment,N,seed); break;
        case Modulo:
          foldAssignment = ASTKFold.moduloKfoldColumn(foldAssignment, N); break;
        case Stratified:
          foldAssignment = ASTKFold.stratifiedKFoldColumn(response(),N,seed); break;
        default:
          throw H2O.unimpl();
      }
    }

    final Key[] modelKeys = new Key[N];
    final Key[] predictionKeys = new Key[N];

    // Step 2: Make 2*N binary weight vectors and store the CV train/validation frames
    final String origWeightsName = _parms._weights_column;
    final Vec[] weights = new Vec[2*N];
    final Vec origWeight  = origWeightsName != null ? origTrainFrame.vec(origWeightsName) : origTrainFrame.anyVec().makeCon(1.0);
    final Frame[] cvTrain = new Frame[N];
    final Frame[] cvValid = new Frame[N];
    final String[] identifier = new String[N];
    final String weightName = "weights";

    final Key<M> origDest = dest();
    for (int i=0; i<N; ++i) {
      // Make weights
      weights[2*i]   = origTrainFrame.anyVec().makeZero();
      weights[2*i+1] = origTrainFrame.anyVec().makeZero();

      // Now update the weights in place
      final int whichFold = i;
      new MRTask() {
        @Override
        public void map(Chunk chks[]) {
          Chunk fold = chks[0];
          Chunk orig = chks[1];
          Chunk train = chks[2];
          Chunk valid = chks[3];
          for (int i=0; i< orig._len; ++i) {
            int foldAssignment = (int)fold.at8(i) % N;
            assert(foldAssignment >= 0 && foldAssignment <N);
            boolean holdout = foldAssignment == whichFold;
            double w = orig.atd(i);
            train.set(i, holdout ? 0 : w);
            valid.set(i, holdout ? w : 0);
          }
        }
      }.doAll(new Vec[]{foldAssignment, origWeight, weights[2*i], weights[2*i+1]});
      if (weights[2*i].isConst() || weights[2*i+1].isConst()) {
        String msg = "Not enough data to create " + N + " random cross-validation splits. Either reduce nfolds, specify a larger dataset (or specify another random number seed, if applicable).";
        throw new H2OIllegalArgumentException(msg);
      }

      identifier[i] = origDest.toString() + "_cv_" + (i+1);
      modelKeys[i] = Key.make(identifier[i]);

      // Training/Validation share the same data, but will have exclusive weights
      cvTrain[i] = new Frame(Key.make(identifier[i]+"_"+_parms._train.toString()+"_train"), origTrainFrame.names(), origTrainFrame.vecs());
      if (origWeightsName!=null) cvTrain[i].remove(origWeightsName);
      cvTrain[i].add(weightName, weights[2*i]);
      DKV.put(cvTrain[i]);
      cvValid[i] = new Frame(Key.make(identifier[i]+"_"+_parms._train.toString()+"_valid"), origTrainFrame.names(), origTrainFrame.vecs());
      if (origWeightsName!=null) cvValid[i].remove(origWeightsName);
      cvValid[i].add(weightName, weights[2*i+1]);
      DKV.put(cvValid[i]);
    }

    // clean up memory (mostly small helper vectors and Frame headers)
    if (_parms._fold_column == null) foldAssignment.remove();
    if (origWeightsName == null) origWeight.remove();

    // adapt main Job's progress bar to build N+1 models
    ModelMetrics.MetricBuilder[] mb = new ModelMetrics.MetricBuilder[N];
    _deleteProgressKey = false; // keep the same progress bar for all N+1 jobs

    long cs = _parms.checksum();
    final boolean async = false;
    cvModelBuilderKeys = new Key[N];
    ModelBuilder<M, P, O>[] cvModelBuilders = new ModelBuilder[N];
    for (int i=0; i<N; ++i) {
      if (isCancelledOrCrashed()) break;

      // Shallow clone - not everything is a private copy!!!
      cvModelBuilders[i] = (ModelBuilder<M, P, O>) this.clone();

      // Fix up some parameters of the clone - UGLY - hopefully nothing is missing
      cvModelBuilderKeys[i] = Key.make(_key.toString() + "_cv" + i);
      cvModelBuilders[i]._key = cvModelBuilderKeys[i];
      cvModelBuilders[i].cvModelBuilderKeys = null; //children cannot have children
      cvModelBuilders[i]._dest = modelKeys[i]; // the model_id gets updated as well in modifyParmsForCrossValidationSplits (must be consistent)
      cvModelBuilders[i]._state = JobState.CREATED;
      cvModelBuilders[i]._parms = (P) _parms.clone();
      cvModelBuilders[i]._parms._weights_column = weightName;
      cvModelBuilders[i]._parms._train = cvTrain[i]._key;
      cvModelBuilders[i]._parms._valid = cvValid[i]._key;
      cvModelBuilders[i]._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
      cvModelBuilders[i].modifyParmsForCrossValidationSplits(i, N, _parms._model_id);
    }
    for (int i=0; i<N; ++i) {
      cvModelBuilders[i].init(false);
      if (cvModelBuilders[i].error_count() > 0) {
        _messages = cvModelBuilders[i]._messages; //bail out on first failure -> main job gets the failed N-fold CV job's error message
        updateValidationMessages();
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(cvModelBuilders[i]);
      }
    }
    for (int i=0; i<N; ++i) {
      if (isCancelledOrCrashed()) break;
      Log.info("Building cross-validation model " + (i + 1) + " / " + N + ".");
      cvModelBuilders[i]._start_time = System.currentTimeMillis();
      cvModelBuilders[i].trainModelImpl(-1, true); //non-blocking
      if (!async)
        cvModelBuilders[i].block();
    }
    // check that this Job's original _params haven't changed
    assert(cs == _parms.checksum());

    if (!isCancelledOrCrashed()) {
      Log.info("Building main model.");

      //HACK:
      // Can't use changeJobState (it assumes that state transitions are monotonic)
      assert (DKV.get(_key).get() == this);
      assert(_state == JobState.RUNNING);
      assert (((Job)DKV.getGet(_key))._state == JobState.RUNNING);
      _state = JobState.CREATED;
      assert (((Job)DKV.getGet(_key))._state == JobState.CREATED);
      assert(!_deleteProgressKey);
      _deleteProgressKey = true; //delete progress after the main model is done

      modifyParmsForCrossValidationMainModel(N, async ? null : cvModelBuilderKeys); //tell the main model that it shouldn't stop early either

      trainModelImpl(-1, false); //non-blocking
      if (!async)
        block();
    }
    else {
      DKV.remove(dest()); //remove prior main model (must have been built by a prior job)
    }

    // in async case, the CV models can score while the main model is still building
    Model[] m = new Model[N];
    for (int i=0; i<N; ++i) {
      Frame adaptFr = null;
      try {
        adaptFr = new Frame(cvValid[i]);
        // score CV models
        if (!isCancelledOrCrashed()) { //don't waste time scoring if the CV run is cancelled
          // Since canBeDone() is false for the CV model, we need to explicitly set the job state to DONE here:
          cvModelBuilders[i].block();

          // mark the job as done
          cvModelBuilders[i].done(true);               // mark the model as completed via force flag (otherwise it wouldn't mark it since canBeDone is false)
          cvModelBuilders[i].updateModelOutput();      // mirror the Job state in the model
          m[i] = DKV.getGet(cvModelBuilders[i].dest());   // now the model is ready for consumption
          m[i].adaptTestForTrain(adaptFr, true, !isSupervised());
          mb[i] = m[i].scoreMetrics(adaptFr);

          if (_parms._keep_cross_validation_predictions) {
            String predName = "prediction_" + modelKeys[i].toString();
            predictionKeys[i] = Key.make(predName);
            m[i].predictScoreImpl(cvValid[i], adaptFr, predName);
          }
        }
      } finally {
        // free resources as early as possible
        if (adaptFr != null) {
          Model.cleanup_adapt(adaptFr, cvValid[i]);
          DKV.remove(adaptFr._key);
        }
        if (cvTrain[i] != null) DKV.remove(cvTrain[i]._key);
        if (cvValid[i] != null) DKV.remove(cvValid[i]._key);
        if (weights[2 * i] != null) weights[2 * i].remove();
        if (weights[2 * i + 1] != null) weights[2 * i + 1].remove();
        if (cvModelBuilders[i] != null) cvModelBuilders[i].remove();
      }
    }

    // wait for completion of the main model
    if (!isCancelledOrCrashed()) {
      block();
      Model mainModel = DKV.getGet(dest()); // get the fully trained model, but it's not yet done (still needs cv metrics)

      // Check that both the job and the model are not yet marked as done (canBeDone() looks at whether N-fold CV is done)
      assert (_state == JobState.RUNNING);

      // Compute and put the cross-validation metrics into the main model
      Log.info("Computing " + N + "-fold cross-validation metrics.");
      mainModel._output._cross_validation_models = new Key[N];
      mainModel._output._cross_validation_predictions = _parms._keep_cross_validation_predictions ? new Key[N] : null;
      for (int i = 0; i < N; ++i) {
        if (i > 0) mb[0].reduce(mb[i]);
        mainModel._output._cross_validation_models[i] = modelKeys[i];
        if (_parms._keep_cross_validation_predictions)
          mainModel._output._cross_validation_predictions[i] = predictionKeys[i];
      }
      mainModel._output._cross_validation_metrics = mb[0].makeModelMetrics(mainModel, _parms.train());
      mainModel._output._cross_validation_metrics._description = N + "-fold cross-validation on training data";
      Log.info(mainModel._output._cross_validation_metrics.toString());

      // Now, the main model is complete (has cv metrics)
      DKV.put(mainModel);

      assert (!isDone());
      done(true); //now, we can mark the job as done
      updateModelOutput(); //update the state of the model (tiny race condition here: someone might fetch the model without the updated state/time)
    }
    return this;
  }
  /**
   * Override with model-specific checks / modifications to _parms for N-fold cross-validation splits.
   * For example, the models might need to be told to not do early stopping.
   * @param i which model index [0...N-1]
   * @param N Total number of cross-validation folds
   */
  public void modifyParmsForCrossValidationSplits(int i, int N, Key<Model> model_id) {
    _parms._nfolds = 0;
    if (model_id != null)
      _parms._model_id = Key.make(model_id.toString());
  }

  /**
   * Override for model-specific checks / modifications to _parms for the main model during N-fold cross-validation.
   * For example, the model might need to be told to not do early stopping.
   * @param N Total number of cross-validation folds
   */
  public void modifyParmsForCrossValidationMainModel(int N, Key<Model>[] cvModelKeys) {

  }

  boolean _deleteProgressKey = true;
  @Override
  protected boolean deleteProgressKey() {
    return _deleteProgressKey;
  }

  /**
   * Whether n-fold cross-validation is done
   * @return
   */
  public boolean nFoldCV() {
    return _parms._fold_column != null || _parms._nfolds != 0;
  }

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
  protected transient Vec _weights; // observation weight column
  protected transient Vec _fold; // fold id column

  public boolean hasOffsetCol(){ return _parms._offset_column != null;} // don't look at transient Vec
  public boolean hasWeightCol(){return _parms._weights_column != null;} // don't look at transient Vec
  public boolean hasFoldCol(){return _parms._fold_column != null;} // don't look at transient Vec
  public int numSpecialCols() { return (hasOffsetCol() ? 1 : 0) + (hasWeightCol() ? 1 : 0) + (hasFoldCol() ? 1 : 0); }
  // no hasResponse, call isSupervised instead (response is mandatory if isSupervised is true)

  protected int _nclass; // Number of classes; 1 for regression; 2+ for classification

  public int nclasses(){return _nclass;}

  public final boolean isClassifier() { return nclasses() > 1; }

  /**
   * Find and set response/weights/offset/fold and put them all in the end,
   * @return number of non-feature vecs
   */
  protected int separateFeatureVecs() {
    int res = 0;
    if(_parms._weights_column != null) {
      Vec w = _train.remove(_parms._weights_column);
      if(w == null)
        error("_weights_column","Weights column '" + _parms._weights_column  + "' not found in the training frame");
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
    } else {
      _weights = null;
      assert(!hasWeightCol());
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
    } else {
      _offset = null;
      assert(!hasOffsetCol());
    }
    if(_parms._fold_column != null) {
      Vec f = _train.remove(_parms._fold_column);
      if(f == null)
        error("_fold_column","Fold column '" + _parms._fold_column  + "' not found in the training frame");
      else {
        if(!f.isInt())
          error("_fold_column","Invalid fold column '" + _parms._fold_column  + "', fold must be integer");
        if(f.min() < 0)
          error("_fold_column","Invalid fold column '" + _parms._fold_column  + "', fold must be non-negative");
        if(f.isConst())
          error("_fold_column","Invalid fold column '" + _parms._fold_column  + "', fold cannot be constant");
        _fold = f;
        if(f.naCnt() > 0)
          error("_fold_column","Fold cannot have missing values.");
        if(_fold == _weights)
          error("_fold_column", "Fold must be different from weights");
        if(_fold == _offset)
          error("_fold_column", "Fold must be different from offset");
        _train.add(_parms._fold_column, f);
        ++res;
      }
    } else {
      _fold = null;
      assert(!hasFoldCol());
    }
    if(isSupervised() && _parms._response_column != null) {
      _response = _train.remove(_parms._response_column);
      if (_response == null) {
        if (isSupervised())
          error("_response_column", "Response column '" + _parms._response_column + "' not found in the training frame");
      } else {
        if(_response == _offset)
          error("_response", "Response must be different from offset_column");
        if(_response == _weights)
          error("_response", "Response must be different from weights_column");
        if(_response == _fold)
          error("_response", "Response must be different from fold_column");
        _train.add(_parms._response_column, _response);
        ++res;
      }
    } else {
      _response = null;
    }
    return res;
  }

  protected  boolean ignoreStringColumns(){return true;}
  protected  boolean ignoreConstColumns(){return true;}

  /**
   * Ignore constant columns, columns with all NAs and strings.
   * @param npredictors
   * @param expensive
   */
  protected void ignoreBadColumns(int npredictors, boolean expensive){
    // Drop all-constant and all-bad columns.
    if(_parms._ignore_const_cols)
      new FilterCols(npredictors) {
        @Override protected boolean filter(Vec v) {
          return (ignoreConstColumns() && v.isConst()) || v.isBad() || (ignoreStringColumns() && v.isString()); }
      }.doIt(_train,"Dropping constant columns: ",expensive);
  }
  /**
   * Override this method to call error() if the model is expected to not fit in memory, and say why
   */
  protected void checkMemoryFootPrint() {}


  transient double [] _distribution;
  transient double [] _priorClassDist;

  protected boolean computePriorClassDistribution(){
    return isClassifier();
  }

  @Override
  public int error_count() { assert error_count_or_uninitialized() >= 0 : "init() not run yet"; return super.error_count(); }

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
    if (_parms._nfolds < 0 || _parms._nfolds == 1) {
      error("_nfolds", "nfolds must be either 0 or >1.");
    }
    if (_parms._nfolds > 1 && _parms._nfolds > train().numRows()) {
      error("_nfolds", "nfolds cannot be larger than the number of rows (" + train().numRows() + ").");
    }
    if (_parms._fold_column != null) {
      hide("_fold_assignment", "Fold assignment is ignored when a fold column is specified.");
      if (_parms._nfolds > 1) {
        error("_nfolds", "nfolds cannot be specified at the same time as a fold column.");
      } else {
        hide("_nfolds", "nfolds is ignored when a fold column is specified.");
      }
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO) {
        error("_fold_assignment", "Fold assignment is not allowed in conjunction with a fold column.");
      }
    }
    if (_parms._nfolds > 1) {
      hide("_fold_column", "Fold column is ignored when nfolds > 1.");
    }
    // hide cross-validation parameters unless cross-val is enabled
    if (!nFoldCV()) {
      hide("_keep_cross_validation_predictions", "Only for cross-validation.");
      hide("_fold_assignment", "Only for cross-validation.");
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO) {
        error("_fold_assignment", "Fold assignment is only allowed for cross-validation.");
      }
    }
    if (_parms._distribution != Distribution.Family.tweedie) {
      hide("_tweedie_power", "Only for Tweedie Distribution.");
    }
    if (_parms._tweedie_power <= 1 || _parms._tweedie_power >= 2) {
      error("_tweedie_power", "Tweedie power must be between 1 and 2 (exclusive).");
    }
    if (expensive) {
      checkDistributions();
    }

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
        _nclass = _response.isCategorical() ? _response.cardinality() : 1;
        if (_response.isConst())
          error("_response","Response cannot be constant.");
      }
      if (! _parms._balance_classes)
        hide("_max_after_balance_size", "Balance classes is false, hide max_after_balance_size");
      else if (_parms._weights_column != null && _weights != null && !_weights.isBinary())
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

    if( _nclass > Model.Parameters.MAX_SUPPORTED_LEVELS ) {
      error("_nclass", "Too many levels in response column: " + _nclass + ", maximum supported number of classes is " + Model.Parameters.MAX_SUPPORTED_LEVELS + ".");
    }

    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap categoricals
    Frame va = _parms.valid();  // User-given validation set
    if (va != null) {
      _valid = new Frame(null /* not putting this into KV */, va._names.clone(), va.vecs().clone());
      try {
        String[] msgs = Model.adaptTestForTrain(_train._names, _parms._weights_column, _parms._offset_column, _parms._fold_column, null, _train.domains(), _valid, _parms.missingColumnsType(), expensive, true);
        _vresponse = _valid.vec(_parms._response_column);
        if (_vresponse == null && _parms._response_column != null)
          error("_validation_frame", "Validation frame must have a response column '" + _parms._response_column + "'.");
        if (expensive) {
          for (String s : msgs) {
            Log.info(s);
            warn("_valid", s);
          }
        }
        assert !expensive || (_valid == null || Arrays.equals(_train._names, _valid._names));
      } catch (IllegalArgumentException iae) {
        error("_valid", iae.getMessage());
      }
    } else {
      _valid = null;
      _vresponse = null;
    }

    if (_parms._checkpoint != null && DKV.get(_parms._checkpoint) == null) {
      error("_checkpoint", "Checkpoint has to point to existing model!");
    }

    if (_parms._stopping_tolerance < 0) {
      error("_stopping_tolerance", "Stopping tolerance must be >= 0.");
    }
    if (_parms._stopping_tolerance >= 1) {
      error("_stopping_tolerance", "Stopping tolerance must be < 1.");
    }
    if (_parms._stopping_rounds == 0) {
      if (_parms._stopping_metric != ScoreKeeper.StoppingMetric.AUTO)
        warn("_stopping_metric", "Stopping metric is ignored for _stopping_rounds=0.");
      if (_parms._stopping_tolerance != _parms.defaultStoppingTolerance())
        warn("_stopping_tolerance", "Stopping tolerance is ignored for _stopping_rounds=0.");
    } else if (_parms._stopping_rounds < 0) {
      error("_stopping_rounds", "Stopping rounds must be >= 0.");
    } else {
      if (isClassifier()) {
        if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.deviance) {
          error("_stopping_metric", "Stopping metric cannot be deviance for classification.");
        }
        if (nclasses()!=2 && _parms._stopping_metric == ScoreKeeper.StoppingMetric.AUC) {
          error("_stopping_metric", "Stopping metric cannot be AUC for multinomial classification.");
        }
      } else {
        if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.misclassification ||
                _parms._stopping_metric == ScoreKeeper.StoppingMetric.AUC ||
                _parms._stopping_metric == ScoreKeeper.StoppingMetric.logloss)
        {
          error("_stopping_metric", "Stopping metric cannot be " + _parms._stopping_metric.toString() + " for regression.");
        }
      }
    }
  }

  public void checkDistributions() {
    if (_parms._distribution == Distribution.Family.poisson) {
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Poisson distribution.");
    } else if (_parms._distribution == Distribution.Family.gamma) {
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Gamma distribution.");
    } else if (_parms._distribution == Distribution.Family.tweedie) {
      if (_parms._tweedie_power >= 2 || _parms._tweedie_power <= 1)
        error("_tweedie_power", "Tweedie power must be between 1 and 2.");
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Tweedie distribution.");
    }
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

}
