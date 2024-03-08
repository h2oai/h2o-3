package hex;

import hex.genmodel.MojoModel;
import hex.genmodel.utils.DistributionFamily;
import jsr166y.CountedCompleter;
import water.*;
import water.api.FSIOException;
import water.api.HDFSIOException;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.rapids.ast.prims.advmath.AstKFold;
import water.udf.CFuncRef;
import water.util.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Iced {

  public ToEigenVec getToEigenVec() { return null; }
  public boolean shouldReorder(Vec v) { return _parms._categorical_encoding.needsResponse() && isSupervised(); }

  // initialized to be non-null to provide nicer exceptions when used incorrectly (instead of NPE)
  private transient Workspace _workspace = new Workspace(false);

  public Job<M> _job;     // Job controlling this build
  /** Block till completion, and return the built model from the DKV.  Note the
   *  funny assert: the Job does NOT have to be controlling this model build,
   *  but might, e.g. be controlling a Grid search for which this is just one
   *  of many results.  Calling 'get' means that we are blocking on the Job
   *  which is controlling ONLY this ModelBuilder, and when the Job completes
   *  we can return built Model. */
  public final M get() { assert _job._result == _result; return _job.get(); }
  public final boolean isStopped() { return _job.isStopped(); }
  
  // Key of the model being built; note that this is DIFFERENT from
  // _job._result if the Job is being shared by many sub-models
  // e.g. cross-validation.
  protected Key<M> _result;  // Built Model key
  public final Key<M> dest() { return _result; }

  public String _desc = "Main model";
  
  private Countdown _build_model_countdown;
  private Countdown _build_step_countdown;
  final void startClock() {
    _build_model_countdown = Countdown.fromSeconds(_parms._max_runtime_secs);
    _build_model_countdown.start();
  }
  protected boolean timeout() {
    return _build_step_countdown != null ? _build_step_countdown.timedOut() : _build_model_countdown.timedOut();
  }
  protected boolean stop_requested() {
    return _job.stop_requested() || timeout();
  }

  protected long remainingTimeSecs() {
    return (long) Math.ceil(_build_model_countdown.remainingTime() / 1000.0);
  }

  /** Default model-builder key */
  public static <S extends Model> Key<S> defaultKey(String algoName) {
    return Key.make(H2O.calcNextUniqueModelId(algoName));
  }

  /** Default easy constructor: Unique new job and unique new result key */
  protected ModelBuilder(P parms) {
    this(parms, ModelBuilder.<M>defaultKey(parms.algoName()));
  }

  /** Unique new job and named result key */
  protected ModelBuilder(P parms, Key<M> key) {
    _job = new Job<>(_result = key, parms.javaName(), parms.algoName());
    _parms = parms;
    _input_parms = (P) parms.clone();
  }

  /** Shared pre-existing Job and unique new result key */
  protected ModelBuilder(P parms, Job<M> job) {
    _job = job;
    _result = defaultKey(parms.algoName());
    _parms = parms;
    _input_parms = (P) parms.clone();
  }

  /** List of known ModelBuilders with all default args; endlessly cloned by
   *  the GUI for new private instances, then the GUI overrides some of the
   *  defaults with user args. */
  private static String[] ALGOBASES = new String[0];
  public static String[] algos() { return ALGOBASES; }
  private static String[] SCHEMAS = new String[0];
  private static ModelBuilder[] BUILDERS = new ModelBuilder[0];

  protected boolean _startUpOnceModelBuilder = false;

  /** One-time start-up only ModelBuilder, endlessly cloned by the GUI for the
   *  default settings. */
  protected ModelBuilder(P parms, boolean startup_once) { this(parms,startup_once,"hex.schemas."); }
  protected ModelBuilder(P parms, boolean startup_once, String externalSchemaDirectory ) {
    String base = getName();
    if (!startup_once)
      throw H2O.fail("Algorithm " + base + " registration issue. It can only be called at startup.");
    _startUpOnceModelBuilder = true;
    _job = null;
    _result = null;
    _parms = parms;
    init(false); // Default cheap init
    if( ArrayUtils.find(ALGOBASES,base) != -1 )
      throw H2O.fail("Only called once at startup per ModelBuilder, and "+base+" has already been called");
    // FIXME: this is not thread safe!
    // michalk: this note ^^ is generally true (considering 3rd parties), however, in h2o-3 code base we have a sequential ModelBuilder initialization
    ALGOBASES = Arrays.copyOf(ALGOBASES,ALGOBASES.length+1);
    BUILDERS  = Arrays.copyOf(BUILDERS ,BUILDERS .length+1);
    SCHEMAS   = Arrays.copyOf(SCHEMAS  ,SCHEMAS  .length+1);
    ALGOBASES[ALGOBASES.length-1] = base;
    BUILDERS [BUILDERS .length-1] = this;
    SCHEMAS  [SCHEMAS  .length-1] = externalSchemaDirectory;
  }

  /** gbm -> GBM, deeplearning -> DeepLearning */
  public static String algoName(String urlName) { return BUILDERS[ArrayUtils.find(ALGOBASES,urlName)]._parms.algoName(); }
  /** gbm -> hex.tree.gbm.GBM, deeplearning -> hex.deeplearning.DeepLearning */
  public static String javaName(String urlName) { return BUILDERS[ArrayUtils.find(ALGOBASES,urlName)]._parms.javaName(); }
  /** gbm -> GBMParameters */
  public static String paramName(String urlName) { return algoName(urlName)+"Parameters"; }
  /** gbm -> "hex.schemas." ; custAlgo -> "org.myOrg.schemas." */
  public static String schemaDirectory(String urlName) { return SCHEMAS[ArrayUtils.find(ALGOBASES,urlName)]; }

  @SuppressWarnings("unchecked")
  static <B extends ModelBuilder> Optional<B> getRegisteredBuilder(String urlName) {
    final String formattedName = urlName.toLowerCase();
    int idx = ArrayUtils.find(ALGOBASES, formattedName);
    if (idx < 0)
      return Optional.empty();
    return Optional.of((B) BUILDERS[idx]);
  }

  @SuppressWarnings("unchecked")
  public static <P extends Model.Parameters> P makeParameters(String algo) {
    return (P) make(algo, null, null)._parms;
  }

  /** Factory method to create a ModelBuilder instance for given the algo name.
   *  Shallow clone of both the default ModelBuilder instance and a Parameter. */
  public static <B extends ModelBuilder> B make(String algo, Job job, Key<Model> result) {
    return getRegisteredBuilder(algo)
            .map(prototype -> { 
              @SuppressWarnings("unchecked")
              B mb = (B) prototype.clone();
              mb._job = job;
              mb._result = result;
              mb._parms = prototype._parms.clone();
              mb._input_parms = prototype._parms.clone();
              return mb;
            })
            .orElseThrow(() -> {
              StringBuilder sb = new StringBuilder();
              sb.append("Unknown algo: '").append(algo).append("'; Extension report: ");
              Log.err(ExtensionManager.getInstance().makeExtensionReport(sb));
              return new IllegalStateException("Algorithm '" + algo + "' is not registered. " +
                      "Available algos: [" + StringUtils.join(",", ALGOBASES)  + "]");
            });
  }

  /**
   * Factory method to create a ModelBuilder instance from a clone of a given {@code parms} instance of Model.Parameters.
   */
  public static <B extends ModelBuilder, MP extends Model.Parameters> B make(MP parms) {
    Key<Model> mKey = ModelBuilder.defaultKey(parms.algoName());
    return make(parms, mKey);
  }

  public static <B extends ModelBuilder, MP extends Model.Parameters> B make(MP parms, Key<Model> mKey) {
    Job<Model> mJob = new Job<>(mKey, parms.javaName(), parms.algoName());
    B newMB = ModelBuilder.make(parms.algoName(), mJob, mKey);
    newMB._parms = parms.clone();
    newMB._input_parms = parms.clone();
    return newMB;
  }

  /** All the parameters required to build the model. 
   * The values of this property will be used as actual parameters of the model. */
  public P _parms;              // Not final, so CV can set-after-clone

  /** All the parameters required to build the model conserved in the input form, with AUTO values not evaluated yet. */
  public P _input_parms;

  /** Training frame: derived from the parameter's training frame, excluding
   *  all ignored columns, all constant and bad columns, perhaps flipping the
   *  response column to an Categorical, etc.  */
  public final Frame train() { return _train; }
  protected transient Frame _train;

  protected transient Frame _origTrain;

  public void setTrain(Frame train) {
    _train = train;
  }
  
  public void setValid(Frame valid) {
    _valid = valid;
  }
  
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
  public Vec vresponse(){return _vresponse == null ? _response : _vresponse;}

  abstract protected class Driver extends H2O.H2OCountedCompleter<Driver> {

    protected Driver(){ super(); }
    
    private ModelBuilderListener _callback;

    public void setCallback(ModelBuilderListener callback) {
      this._callback = callback;
    }

    // Pull the boilerplate out of the computeImpl(), so the algo writer doesn't need to worry about the following:
    // 1) Scope (unless they want to keep data, then they must call Scope.untrack(Key<Vec>[]))
    // 2) Train/Valid frame locking and unlocking
    // 3) calling tryComplete()
    public void compute2() {
      try {
        Scope.enter();
        _parms.read_lock_frames(_job); // Fetch & read-lock input frames
        computeImpl();
        computeParameters();
        saveModelCheckpointIfConfigured();
        notifyModelListeners();
      } finally {
        _parms.read_unlock_frames(_job);
        if (_parms._is_cv_model) {
          // CV models get completely cleaned up when the main model is fully trained.
          Key[] keep = _workspace == null ? new Key[0] : _workspace.getToDelete(true).keySet().toArray(new Key[0]);
          Scope.exit(keep);
        } else {
          cleanUp();
          Scope.exit();
        }
      }
      tryComplete();
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
      setFinalState();
      if (_callback != null) {
        _callback.onModelSuccess(_result.get());
      }
    }

    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      setFinalState();
      if (_callback != null) {
        _callback.onModelFailure(ex, _parms);
      }
      return true;
    }

    public abstract void computeImpl();

    public final void computeParameters() {
      M model = _result.get();
      if (model != null) {
        model.write_lock(_job);
        model.setInputParms(_input_parms);
        model.update(_job);
        model.unlock(_job);
      }
    }
  }

  private void setFinalState() {
    Key<M> reskey = dest();
    if (reskey == null) return;
    M res = reskey.get();
    if (res != null && res._output != null) {
      res._output._job = _job;
      res._output.stopClock();
      res.write_lock(_job);
      res.update(_job);
      res.unlock(_job);
    }
    Log.info("Completing model "+ reskey);
  }

  private void saveModelCheckpointIfConfigured() {
    Model model = _result.get();
    if (model != null && !StringUtils.isNullOrEmpty(model._parms._export_checkpoints_dir)) {
      try {
        model.exportBinaryModel(model._parms._export_checkpoints_dir + "/" + model._key.toString(), true);
      } catch (FSIOException | HDFSIOException | IOException e) {
        throw new H2OIllegalArgumentException("export_checkpoints_dir", "saveModelIfConfigured", e);
      }
    }
  }

  private void notifyModelListeners() {
    Model<?, ?, ?> model = _result.get();
    ListenerService.getInstance().report("model_completed", model, _parms);
  }

  /**
   * Start model training using a this ModelBuilder as a template. The MB can be either used directly
   * or if the method was invoked on a regular H2O node. If the method was called on a client node, the model builder
   * will be used as a template only and the actual instance used for training will re-created on a remote H2O node.
   *
   * Warning: the nature of this method prohibits further use of this instance of the model builder after the method
   *          is called.
   *
   * This is intended to reduce training time in client-mode setups, it pushes all computation to a regular H2O node
   * and avoid exchanging data between client and H2O cluster. This also lowers requirements on the H2O client node.
   *
   * @return model job
   */
  public Job<M> trainModelOnH2ONode() {
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    this._input_parms = (P) this._parms.clone();
    TrainModelRunnable trainModel = new TrainModelRunnable(this);
    H2O.runOnH2ONode(trainModel);
    return _job;
  }

  private static class TrainModelRunnable extends H2O.RemoteRunnable<TrainModelRunnable> {
    private transient ModelBuilder _mb;
    private Job<Model> _job;
    private Key<Model> _key;
    private Model.Parameters _parms;
    private Model.Parameters _input_parms;
    @SuppressWarnings("unchecked")
    private TrainModelRunnable(ModelBuilder mb) {
      _mb = mb;
      _job = (Job<Model>) _mb._job;
      _key = _job._result;
      _parms = _mb._parms;
      _input_parms = _mb._input_parms;
    }
    @Override
    public void setupOnRemote() {
      _mb = ModelBuilder.make(_parms.algoName(), _job, _key);
      _mb._parms = _parms;
      _mb._input_parms = _input_parms;
      _mb.init(false); // validate parameters
    }
    @Override
    public void run() {
      _mb.trainModel();
    }
  }

  /** Method to launch training of a Model, based on its parameters. */
  final public Job<M> trainModel() {
    return trainModel(null);
  }

  final public Job<M> trainModel(final ModelBuilderListener callback) {
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    startClock();
    if (!nFoldCV()) {
      Driver driver = trainModelImpl();
      driver.setCallback(callback);
      return _job.start(driver, _parms.progressUnits(), _parms._max_runtime_secs);
    } else {
      // cross-validation needs to be forked off to allow continuous (non-blocking) progress bar
      return _job.start(new H2O.H2OCountedCompleter() {
                          @Override
                          public void compute2() {
                            computeCrossValidation();
                            tryComplete();
                          }

                          @Override
                          public void onCompletion(CountedCompleter caller) {
                            if (callback != null) callback.onModelSuccess(_result.get());
                          }

                          @Override
                          public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
                            Log.warn("Model training job " + _job._description + " completed with exception: " + ex);
                            if (callback != null) callback.onModelFailure(ex, _parms);
                            try {
                              Keyed.remove(_job._result); // ensure there's no incomplete model left for manipulation after crash or cancellation
                            } catch (Exception logged) {
                              Log.warn("Exception thrown when removing result from job " + _job._description, logged);
                            }
                            return true;
                          }
                        },
          (nFoldWork() + 1/*main model*/) * _parms.progressUnits(), _parms._max_runtime_secs);
    }
  }

  /**
   * Train a model as part of a larger Job;
   *
   * @param fr: Input frame override, ignored if null.
   *   In some cases, algos do not work directly with the original frame in the K/V store.
   *   Instead they run on a private anonymous copy (eg: reblanced dataset).
   *   Use this argument if you want nested job to work on the actual working copy rather than the original Frame in the K/V.
   *   Example: Outer job rebalances dataset and then calls nested job. To avoid needless second reblance, pass in the (already rebalanced) working copy.
   * */
  final public M trainModelNested(Frame fr) {
    if(fr != null) // Use the working copy (e.g. rebalanced) instead of the original K/V store version
      setTrain(fr);
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    startClock();
    if( !nFoldCV() ) submitTrainModelTask().join();
    else computeCrossValidation();
    return _result.get();
  }

  /**
   * Train a model as part of a larger job. The model will be built on a non-client node.
   *
   * @param job containing job
   * @param result key of the resulting model
   * @param params model parameters
   * @param fr input frame, ignored if null
   * @param <MP> Model.Parameters
   * @return instance of a Model
   */
  public static <MP extends Model.Parameters> Model trainModelNested(Job<?> job, Key<Model> result, MP params, Frame fr) {
    H2O.runOnH2ONode(new TrainModelNestedRunnable(job, result, params, fr));
    return result.get();
  }

  private static class TrainModelNestedRunnable extends H2O.RemoteRunnable<TrainModelNestedRunnable> {
    private Job<?> _job;
    private Key<Model> _key;
    private Model.Parameters _parms;
    private Frame _fr;
    private TrainModelNestedRunnable(Job<?> job, Key<Model> key, Model.Parameters parms, Frame fr) {
      _job = job;
      _key = key;
      _parms = parms;
      _fr = fr;
    }
    @Override
    public void run() {
      ModelBuilder mb = ModelBuilder.make(_parms.algoName(), _job, _key);
      mb._parms = _parms;
      mb._input_parms = _parms.clone();
      mb.trainModelNested(_fr);
    }
  }

  /** Model-specific implementation of model training
   * @return A F/J Job, which, when executed, does the build.  F/J is NOT started.  */
  abstract protected Driver trainModelImpl();

  private static class Barrier extends CountedCompleter {
    @Override public void compute() { }
  }

  /**
   * Simple wrapper around model task Driver, its main purpose is to make
   * sure onExceptionalCompletion is not called after join method finishes (similarly how Job behaves).
   */
  class TrainModelTaskController {
    private final Driver _driver;
    private final Barrier _barrier;

    TrainModelTaskController(Driver driver, Barrier barrier) {
      _driver = driver;
      _barrier = barrier;
    }

    /**
     * Block for Driver to finish
     */
    void join() {
      _barrier.join();
    }

    void cancel(boolean mayInterruptIfRunning) {
      _driver.cancel(mayInterruptIfRunning);
    }
  }

  /**
   * Submits the model Driver task for execution, blocking on a barrier
   * that is only completed after the Driver is fully finished (including
   * possible calls to onExceptionalCompletion).
   * 
   * @return controller object that can be used to wait for completion or 
   *  to cancel the execution.
   */
  TrainModelTaskController submitTrainModelTask() {
    Driver d = trainModelImpl();
    Barrier b = new Barrier();
    d.setCompleter(b);
    H2O.submitTask(d);
    return new TrainModelTaskController(d, b);
  }

  @Deprecated protected int nModelsInParallel() { return 0; }
  /**
   * How many should be trained in parallel during N-fold cross-validation?
   * Train all CV models in parallel when parallelism is enabled, otherwise train one at a time
   * Each model can override this logic, based on parameters, dataset size, etc.
   * @return How many models to train in parallel during cross-validation
   */
  protected int nModelsInParallel(int folds) {
    int n = nModelsInParallel();
    if (n > 0) return n;
    return nModelsInParallel(folds, 1);
  }

  protected int nModelsInParallel(int folds, int defaultParallelization) {
    if (!_parms._parallelize_cross_validation) return 1; //user demands serial building (or we need to honor the time constraints for all CV models equally)
    int parallelization = defaultParallelization;
    if (_train.byteSize() < smallDataSize())
      parallelization = folds; //for small data, parallelize over CV models
    return Math.min(parallelization, H2O.ARGS.nthreads);
  }

  protected long smallDataSize() {
    return (long) 1e6;
  }

  private double maxRuntimeSecsPerModel(int cvModelsCount, int parallelization) {
    return cvModelsCount > 0
        ? _parms._max_runtime_secs / Math.ceil((double)cvModelsCount / parallelization + 1)
//        ? _parms._max_runtime_secs * cvModelsCount / (cvModelsCount + 1) / Math.ceil((double)cvModelsCount / parallelization)
        : _parms._max_runtime_secs;
  }

  // Work for each requested fold
  protected int nFoldWork() {
    if( _parms._fold_column == null ) 
      return _parms._nfolds;
    Vec fold = _parms._train.get().vec(_parms._fold_column);
    return FoldAssignment.nFoldWork(fold);
  }

  protected transient ModelTrainingEventsPublisher _eventPublisher;
  protected transient ModelTrainingCoordinator _coordinator;

  public class ModelTrainingCoordinator {

    private final BlockingQueue<ModelTrainingEventsPublisher.Event> _events;
    private final ModelBuilder<M, P, O>[] _cvModelBuilders;
    private int _inProgress;


    public ModelTrainingCoordinator(BlockingQueue<ModelTrainingEventsPublisher.Event> events, 
                                    ModelBuilder<M, P, O>[] cvModelBuilders) {
      _events = events;
      _cvModelBuilders = cvModelBuilders;
      _inProgress = _cvModelBuilders.length;
    }

    public void initStoppingParameters() {
      cv_initStoppingParameters();
    }
    
    public void updateParameters() {
      try {
        while (_inProgress > 0) {
          ModelTrainingEventsPublisher.Event e = _events.take();
          switch (e) {
            case ALL_DONE:
              _inProgress--;
              break;
            case ONE_DONE:
              if (cv_updateOptimalParameters(_cvModelBuilders))
                return;
              break;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Failed to update model parameters based on result of CV model training", e);
      }
      cv_updateOptimalParameters(_cvModelBuilders);
    }
  }

  /**
   * Default naive (serial) implementation of N-fold cross-validation
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  public void computeCrossValidation() {
    assert _job.isRunning();    // main Job is still running
    _job.setReadyForView(false); //wait until the main job starts to let the user inspect the main job
    final int N = nFoldWork();
    ModelBuilder<M, P, O>[] cvModelBuilders = null;
    try {
      Scope.enter();
      init(false);

      // Step 1: Assign each row to a fold
      final FoldAssignment foldAssignment = cv_AssignFold(N);

      // Step 2: Make 2*N binary weight vectors
      final Vec[] weights = cv_makeWeights(N, foldAssignment);

      // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
      cvModelBuilders = cv_makeFramesAndBuilders(N, weights);

      // Step 4: Run all the CV models (and optionally train the main model in parallel to the CV training)
      final boolean buildMainModel;
      if (useParallelMainModelBuilding(N)) {
        int parallelization = nModelsInParallel(N);
        Log.info(_desc + " will be trained in parallel to the Cross-Validation models " +
                "(up to " + parallelization + " models running at the same time).");
        BlockingQueue<ModelTrainingEventsPublisher.Event> events = new LinkedBlockingQueue<>();
        for (ModelBuilder<M, P, O> mb : cvModelBuilders) {
          mb._eventPublisher = new ModelTrainingEventsPublisher(events);
        }
        _coordinator = new ModelTrainingCoordinator(events, cvModelBuilders);
        final ModelBuilder<M, P, O>[] builders = Arrays.copyOf(cvModelBuilders, cvModelBuilders.length + 1);
        builders[builders.length - 1] = this;

        new SubModelBuilder(_job, builders, parallelization).bulkBuildModels();
        buildMainModel = false;
      } else {
        cv_buildModels(N, cvModelBuilders);
        buildMainModel = true;
      }
      
      // Step 5: Score the CV models
      ModelMetrics.MetricBuilder mbs[] = cv_scoreCVModels(N, weights, cvModelBuilders);

      if (buildMainModel) {
        // Step 6: Build the main model
        long time_allocated_to_main_model = (long) (maxRuntimeSecsPerModel(N, nModelsInParallel(N)) * 1e3);
        buildMainModel(time_allocated_to_main_model);
      }

      // Step 7: Combine cross-validation scores; compute main model x-val
      // scores; compute gains/lifts
      if (!cvModelBuilders[0].getName().equals("infogram")) // infogram does not support scoring
        cv_mainModelScores(N, mbs, cvModelBuilders);

      _job.setReadyForView(true);
      DKV.put(_job);
    } catch (Exception e) {
      if (cvModelBuilders != null) {
        Futures fs = new Futures();
        // removing keys added during cv_makeFramesAndBuilders and cv_makeFramesAndBuilders
        // need a better solution: part of this is done in cv_makeFramesAndBuilders but partially and only for its method scope
        // also removing the completed CV models as the main model is incomplete anyway
        for (ModelBuilder mb : cvModelBuilders) {
          DKV.remove(mb._parms._train, fs);
          DKV.remove(mb._parms._valid, fs);
          DKV.remove(Key.make(mb.getPredictionKey()), fs);
          Keyed.remove(mb._result, fs, true);
        }
        fs.blockForPending();
      }
      throw e;
    } finally {
      if (cvModelBuilders != null) {
        for (ModelBuilder mb : cvModelBuilders) {
          mb.cleanUp();
        }
      }
      cleanUp();
      Scope.exit();
    }
  }

  // Step 1: Assign each row to a fold
  FoldAssignment cv_AssignFold(int N) {
    assert(N>=2);
    Vec fold = train().vec(_parms._fold_column);
    if (fold != null) {
      return FoldAssignment.fromUserFoldSpecification(N, fold);
    } else {
      final long seed = _parms.getOrMakeRealSeed();
      Log.info("Creating " + N + " cross-validation splits with random number seed: " + seed);
      switch (_parms._fold_assignment) {
        case AUTO:
        case Random:
          fold = AstKFold.kfoldColumn(train().anyVec().makeZero(), N, seed);
          break;
        case Modulo:
          fold = AstKFold.moduloKfoldColumn(train().anyVec().makeZero(), N);
          break;
        case Stratified:
          fold = AstKFold.stratifiedKFoldColumn(response(), N, seed);
          break;
        default:
          throw H2O.unimpl();
      }
      return FoldAssignment.fromInternalFold(N, fold);
    }
  }

  // Step 2: Make 2*N binary weight vectors
  Vec[] cv_makeWeights(final int N, FoldAssignment foldAssignment) {
    String origWeightsName = _parms._weights_column;
    Vec origWeight  = origWeightsName != null ? train().vec(origWeightsName) : train().anyVec().makeCon(1.0);
    Frame folds_and_weights = new Frame(foldAssignment.getAdaptedFold(), origWeight);
    Vec[] weights = new MRTask() {
        @Override public void map(Chunk chks[], NewChunk nchks[]) {
          Chunk fold = chks[0], orig = chks[1];
          for( int row=0; row< orig._len; row++ ) {
            int foldIdx = (int) fold.atd(row);
            double w = orig.atd(row);
            for( int f = 0; f < N; f++ ) {
              boolean holdout = foldIdx == f;
              nchks[2 * f].addNum(holdout ? 0 : w);
              nchks[2*f+1].addNum(holdout ? w : 0);
            }
          }
        }
      }.doAll(2*N,Vec.T_NUM,folds_and_weights).outputFrame().vecs();
    if (origWeightsName == null)
      origWeight.remove(); // Cleanup temp

    if (_parms._keep_cross_validation_fold_assignment)
      DKV.put(foldAssignment.toFrame(Key.make("cv_fold_assignment_" + _result.toString())));
    foldAssignment.remove(_parms._keep_cross_validation_fold_assignment);

    for( Vec weight : weights )
      if( weight.isConst() )
        throw new H2OIllegalArgumentException("Not enough data to create " + N + " random cross-validation splits. Either reduce nfolds, specify a larger dataset (or specify another random number seed, if applicable).");
    return weights;
  }

  // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
  private ModelBuilder<M, P, O>[] cv_makeFramesAndBuilders( int N, Vec[] weights ) {
    final long old_cs = _parms.checksum();
    final String origDest = _result.toString();

    final String weightName = "__internal_cv_weights__";
    if (train().find(weightName) != -1) throw new H2OIllegalArgumentException("Frame cannot contain a Vec called '" + weightName + "'.");

    Frame cv_fr = new Frame(train().names(),train().vecs());
    if( _parms._weights_column!=null ) cv_fr.remove( _parms._weights_column ); // The CV frames will have their own private weight column

    ModelBuilder<M, P, O>[] cvModelBuilders = new ModelBuilder[N];
    List<Frame> cvFramesForFailedModels = new ArrayList<>();
    double cv_max_runtime_secs = maxRuntimeSecsPerModel(N, nModelsInParallel(N));
    for( int i=0; i<N; i++ ) {
      String identifier = origDest + "_cv_" + (i+1);
      // Training/Validation share the same data, but will have exclusive weights
      Frame cvTrain = new Frame(Key.make(identifier + "_train"), cv_fr.names(), cv_fr.vecs());
      cvTrain.write_lock(_job);
      cvTrain.add(weightName, weights[2*i]);
      cvTrain.update(_job);
      Frame cvValid = new Frame(Key.make(identifier + "_valid"), cv_fr.names(), cv_fr.vecs());
      cvValid.write_lock(_job);
      cvValid.add(weightName, weights[2*i+1]);
      cvValid.update(_job);
      
      // Shallow clone - not everything is a private copy!!!
      ModelBuilder<M, P, O> cv_mb = (ModelBuilder)this.clone();
      cv_mb.setTrain(cvTrain);
      cv_mb._result = Key.make(identifier); // Each submodel gets its own key
      cv_mb._parms = (P) _parms.clone();
      // Fix up some parameters of the clone
      cv_mb._parms._is_cv_model = true;
      cv_mb._parms._cv_fold = i;
      cv_mb._parms._weights_column = weightName;// All submodels have a weight column, which the main model does not
      cv_mb._parms.setTrain(cvTrain._key);       // All submodels have a weight column, which the main model does not
      cv_mb._parms._valid = cvValid._key;
      cv_mb._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
      cv_mb._parms._nfolds = 0; // Each submodel is not itself folded
      cv_mb._parms._max_runtime_secs = cv_max_runtime_secs;
      cv_mb.clearValidationErrors(); // each submodel gets its own validation messages and error_count()
      cv_mb._input_parms = (P) _parms.clone();
      cv_mb._desc = "Cross-Validation model " + (i + 1) + " / " + N;

      // Error-check all the cross-validation Builders before launching any
      cv_mb.init(false);
      if( cv_mb.error_count() > 0 ) { // Gather all submodel error messages
        Log.info("Marking frame for failed cv model for removal: " + cvTrain._key);
        cvFramesForFailedModels.add(cvTrain);
        Log.info("Marking frame for failed cv model for removal: " + cvValid._key);
        cvFramesForFailedModels.add(cvValid);

        for (ValidationMessage vm : cv_mb._messages)
          message(vm._log_level, vm._field_name, vm._message);
      }
      cvModelBuilders[i] = cv_mb;
    }

    if( error_count() > 0 ) {               // Found an error in one or more submodels
      Futures fs = new Futures();
      for (Frame cvf : cvFramesForFailedModels) {
        cvf.vec(weightName).remove(fs);     // delete the Vec's chunks
        DKV.remove(cvf._key, fs);           // delete the Frame from the DKV, leaving its vecs
        Log.info("Removing frame for failed cv model: " + cvf._key);
      }
      fs.blockForPending();
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    }
    // check that this Job's original _params haven't changed
    assert old_cs == _parms.checksum();
    return cvModelBuilders;
  }

  // Step 4: Run all the CV models and launch the main model
  public void cv_buildModels(int N, ModelBuilder<M, P, O>[] cvModelBuilders ) {
    makeCVModelBuilder(cvModelBuilders, nModelsInParallel(N)).bulkBuildModels();
    cv_computeAndSetOptimalParameters(cvModelBuilders);
  }
  
  protected CVModelBuilder makeCVModelBuilder(ModelBuilder<?, ?, ?>[] modelBuilders, int parallelization) {
    return new CVModelBuilder(_job, modelBuilders, parallelization);
  }
  
  
  // Step 5: Score the CV models
  public ModelMetrics.MetricBuilder[] cv_scoreCVModels(int N, Vec[] weights, ModelBuilder<M, P, O>[] cvModelBuilders) {
    if (_job.stop_requested()) {
      Log.info("Skipping scoring of CV models");
      throw new Job.JobCancelledException(_job);
    }
    assert weights.length == 2*N;
    assert cvModelBuilders.length == N;

    Log.info("Scoring the "+N+" CV models");
    ModelMetrics.MetricBuilder[] mbs = new ModelMetrics.MetricBuilder[N];
    Futures fs = new Futures();
    for (int i=0; i<N; ++i) {
      if (_job.stop_requested()) {
        Log.info("Skipping scoring for last "+(N-i)+" out of "+N+" CV models");
        throw new Job.JobCancelledException(_job);
      }
      Frame cvValid = cvModelBuilders[i].valid();
      Frame preds = null;
      try (Scope.Safe s = Scope.safe(cvValid)) {
        Frame adaptFr = new Frame(cvValid);
        if (makeCVMetrics(cvModelBuilders[i])) {
          M cvModel = cvModelBuilders[i].dest().get();
          cvModel.adaptTestForTrain(adaptFr, true, !isSupervised());
          if (nclasses() == 2 /* need holdout predictions for gains/lift table */
                  || _parms._keep_cross_validation_predictions
                  || (cvModel.isDistributionHuber() /*need to compute quantiles on abs error of holdout predictions*/)) {
            String predName = cvModelBuilders[i].getPredictionKey();
            Model.PredictScoreResult result = cvModel.predictScoreImpl(cvValid, adaptFr, predName, _job, true, CFuncRef.from(_parms._custom_metric_func));
            preds = result.getPredictions();
            Scope.untrack(preds);
            result.makeModelMetrics(cvValid, adaptFr);
            mbs[i] = result.getMetricBuilder();
            DKV.put(cvModel);
          } else {
            mbs[i] = cvModel.scoreMetrics(adaptFr);
          }
        }
      } finally {
        Scope.track(preds);
      }
      DKV.remove(cvModelBuilders[i]._parms._train,fs);
      DKV.remove(cvModelBuilders[i]._parms._valid,fs);
      weights[2*i  ].remove(fs);
      weights[2*i+1].remove(fs);
    }
    
    fs.blockForPending();
    return mbs;
  }

  protected boolean makeCVMetrics(ModelBuilder<?, ?, ?> cvModelBuilder) {
    return !cvModelBuilder.getName().equals("infogram");
  }

  private boolean useParallelMainModelBuilding(int nFolds) {
    int parallelizationLevel = nModelsInParallel(nFolds);
    return parallelizationLevel > 1 && _parms._parallelize_cross_validation && cv_canBuildMainModelInParallel();
  }

  protected boolean cv_canBuildMainModelInParallel() {
    return false;
  }

  protected boolean cv_updateOptimalParameters(ModelBuilder<M, P, O>[] cvModelBuilders) {
    throw new UnsupportedOperationException();
  }

  protected boolean cv_initStoppingParameters() {
    throw new UnsupportedOperationException();
  }

  // Step 6: build the main model
  private void buildMainModel(long max_runtime_millis) {
    if (_job.stop_requested()) {
      Log.info("Skipping main model");
      throw new Job.JobCancelledException(_job);
    }
    assert _job.isRunning();
    Log.info("Building main model.");
    Log.info("Remaining time for main model (ms): " + max_runtime_millis);
    _build_step_countdown = new Countdown(max_runtime_millis, true);
    submitTrainModelTask().join();
    _build_step_countdown = null;
  }

  // Step 7: Combine cross-validation scores; compute main model x-val scores; compute gains/lifts
  public void cv_mainModelScores(int N, ModelMetrics.MetricBuilder mbs[], ModelBuilder<M, P, O> cvModelBuilders[]) {
    //never skipping CV main scores: we managed to reach last step and this should not be an expensive one, so let's offer this model
    M mainModel = _result.get();

    // Compute and put the cross-validation metrics into the main model
    Log.info("Computing "+N+"-fold cross-validation metrics.");
    Key<M>[] cvModKeys = new Key[N];
    mainModel._output._cross_validation_models = _parms._keep_cross_validation_models ? cvModKeys : null;
    Key<Frame>[] predKeys = new Key[N];
    mainModel._output._cross_validation_predictions = _parms._keep_cross_validation_predictions ? predKeys : null;
    
    for (int i = 0; i < N; ++i) {
      cvModKeys[i] = cvModelBuilders[i]._result;
      predKeys[i] = Key.make(cvModelBuilders[i].getPredictionKey());
    }
    
    cv_makeAggregateModelMetrics(mbs);
    
    Frame holdoutPreds = null;
    if (_parms._keep_cross_validation_predictions || (nclasses()==2 /*GainsLift needs this*/ || mainModel.isDistributionHuber())) {
      Key<Frame> cvhp = Key.make("cv_holdout_prediction_" + mainModel._key.toString());
      if (_parms._keep_cross_validation_predictions) //only show the user if they asked for it
        mainModel._output._cross_validation_holdout_predictions_frame_id = cvhp;
      holdoutPreds = combineHoldoutPredictions(predKeys, cvhp);
    }
    if (_parms._keep_cross_validation_fold_assignment) {
      mainModel._output._cross_validation_fold_assignment_frame_id = Key.make("cv_fold_assignment_" + _result.toString());
      Frame xvalidation_fold_assignment_frame = mainModel._output._cross_validation_fold_assignment_frame_id.get();
      if (xvalidation_fold_assignment_frame != null)
        Scope.untrack(xvalidation_fold_assignment_frame.keysList());
    }
    // Keep or toss predictions
    if (_parms._keep_cross_validation_predictions) {
      for (Key<Frame> k : predKeys) {
        Frame fr = DKV.getGet(k);
        if (fr != null) Scope.untrack(fr);
      }
    } else {
      int count = Model.deleteAll(predKeys);
      Log.info(count+" CV predictions were removed");
    }
    mainModel._output._cross_validation_metrics = mbs[0].makeModelMetrics(mainModel, _parms.train(), null, holdoutPreds);
    if (holdoutPreds != null) {
      if (_parms._keep_cross_validation_predictions) Scope.untrack(holdoutPreds);
      else holdoutPreds.remove();
    }
    mainModel._output._cross_validation_metrics._description = N + "-fold cross-validation on training data (Metrics computed for combined holdout predictions)";
    Log.info(mainModel._output._cross_validation_metrics.toString());
    mainModel._output._cross_validation_metrics_summary = makeCrossValidationSummaryTable(cvModKeys);

    // Put cross-validation scoring history to the main model
    if (mainModel._output._scoring_history != null) { // check if scoring history is supported (e.g., NaiveBayes doesn't)
      mainModel._output._cv_scoring_history = new TwoDimTable[cvModKeys.length];
      for (int i = 0; i < cvModKeys.length; i++) {
        TwoDimTable sh = cvModKeys[i].get()._output._scoring_history;
        String[] rowHeaders = sh.getRowHeaders();
        String[] colTypes = sh.getColTypes();
        int tableSize = rowHeaders.length;
        int colSize = colTypes.length;
        TwoDimTable copiedScoringHistory = new TwoDimTable(
                sh.getTableHeader(),
                sh.getTableDescription(),
                sh.getRowHeaders(),
                sh.getColHeaders(),
                sh.getColTypes(),
                sh.getColFormats(),
                sh.getColHeaderForRowHeaders());
        for (int rowIndex = 0; rowIndex < tableSize; rowIndex++)  {
          for (int colIndex = 0; colIndex < colSize; colIndex++) {
            copiedScoringHistory.set(rowIndex, colIndex,sh.get(rowIndex, colIndex));
          }
        }
        mainModel._output._cv_scoring_history[i] = copiedScoringHistory;
      }
    }

    if (!_parms._keep_cross_validation_models) {
      int count = Model.deleteAll(cvModKeys);
      Log.info(count+" CV models were removed");
    }

    mainModel._output._total_run_time = _build_model_countdown.elapsedTime();
    // Now, the main model is complete (has cv metrics)
    DKV.put(mainModel);
  }
  
  public void cv_makeAggregateModelMetrics(ModelMetrics.MetricBuilder[] mbs){
    for (int i = 1; i < mbs.length; ++i) {
      mbs[0].reduceForCV(mbs[i]);
    }
  }

  private String getPredictionKey() {
    return "prediction_"+_result.toString();
  }

  /** Set max_runtime_secs for the main model.
   * Using _main_model_time_budget_factor to determine if and how we should restrict the time for the main model.
   * In general, we should use 0 or > 1 to be reasonably certain that the main model will have time to converge.
   * if _main_model_time_budget_factor < 0, main_model_time_budget_factor is applied on remaining time to get max runtime secs.
   * if _main_model_time_budget_factor == 0, do not restrict time for the main model.
   * if _main_model_time_budget_factor > 0, use max_runtime_secs estimate using nfolds (doesn't depend on the remaining time).
   */
  protected void setMaxRuntimeSecsForMainModel() {
    if (_parms._max_runtime_secs == 0) return;
    if (_parms._main_model_time_budget_factor < 0) {
      // strict version that uses the actual remaining time or 1 sec in case we ran out of time
      _parms._max_runtime_secs = Math.max(1, -_parms._main_model_time_budget_factor * remainingTimeSecs());
    } else {
      int nFolds = nFoldWork();
      // looser version that uses max of remaining time and estimated remaining time based on number of folds
      _parms._max_runtime_secs = Math.max(remainingTimeSecs(),
              _parms._main_model_time_budget_factor * maxRuntimeSecsPerModel(nFolds, nModelsInParallel(nFolds)) * nFolds /((double) nFolds - 1));
    }
  }

  /** Override for model-specific checks / modifications to _parms for the main model during N-fold cross-validation.
   *  Also allow the cv models to be modified after all of them have been built.
   *  For example, the model might need to be told to not do early stopping. CV models might have their lambda value modified, etc.
   */
  public void cv_computeAndSetOptimalParameters(ModelBuilder<M, P, O>[] cvModelBuilders) { }

  /** @return Whether n-fold cross-validation is done  */
  public boolean nFoldCV() {
    return _parms._fold_column != null || _parms._nfolds != 0;
  }

  /** List containing the categories of models that this builder can
   *  build.  Each ModelBuilder must have one of these. */
  abstract public ModelCategory[] can_build();


  /** Visibility for this algo: is it always visible, is it beta (always
   *  visible but with a note in the UI) or is it experimental (hidden by
   *  default, visible in the UI if the user gives an "experimental" flag at
   *  startup); test-only builders are "experimental"  */
  public enum BuilderVisibility {
    Experimental, Beta, Stable;
    /**
     * @param value A value to search for among {@link BuilderVisibility}'s values
     * @return A member of {@link BuilderVisibility}, if found.
     * @throws IllegalArgumentException If given value is not found among members of {@link BuilderVisibility} enum.
     */
    public static BuilderVisibility valueOfIgnoreCase(final String value) throws IllegalArgumentException {
      final BuilderVisibility[] values = values();
      for (int i = 0; i < values.length; i++) {
        if (values[i].name().equalsIgnoreCase(value)) return values[i];
      }
      throw new IllegalArgumentException(String.format("Algorithm availability level of '%s' is not known. Available levels: %s",
              value, Arrays.toString(values)));
    }
  }
  public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }

  /** Clear whatever was done by init() so it can be run again. */
  public void clearInitState() {
    clearValidationErrors();
  }
  protected boolean logMe() { return true; }

  abstract public boolean isSupervised();

  public boolean isResponseOptional() {
    return false;
  }
  
  protected transient Vec _response; // Handy response column
  protected transient Vec _vresponse; // Handy response column
  protected transient Vec _offset; // Handy offset column
  protected transient Vec _weights; // observation weight column
  protected transient Vec _fold; // fold id column
  protected transient Vec _treatment;
  protected transient String[] _origNames; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame
  protected transient String[][] _origDomains; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame
  protected transient double[] _orig_projection_array; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame

  public boolean hasOffsetCol(){ return _parms._offset_column != null;} // don't look at transient Vec
  public boolean hasWeightCol(){ return _parms._weights_column != null;} // don't look at transient Vec
  public boolean hasFoldCol()  { return _parms._fold_column != null;} // don't look at transient Vec
  public boolean hasTreatmentCol() { return _parms._treatment_column != null;}
  public int numSpecialCols()  { return (hasOffsetCol() ? 1 : 0) + (hasWeightCol() ? 1 : 0) + (hasFoldCol() ? 1 : 0) + (hasTreatmentCol() ? 1 : 0); }

  public boolean havePojo() { return false; }
  public boolean haveMojo() { return false; }

  protected int _nclass; // Number of classes; 1 for regression; 2+ for classification

  public int nclasses(){return _nclass;}

  public final boolean isClassifier() { return nclasses() > 1; }

  protected boolean validateStoppingMetric() {
    return true;
  }

  protected boolean validateBinaryResponse() {
    return true;
  }

  protected void checkEarlyStoppingReproducibility() {
    // nothing by default -> meant to be overridden 
  }

  /**
   * Find and set response/weights/offset/fold and put them all in the end,
   * @return number of non-feature vecs
   */
  public int separateFeatureVecs() {
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
        if(!f.isInt() && !f.isCategorical())
          error("_fold_column","Invalid fold column '" + _parms._fold_column  + "', fold must be integer or categorical");
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
    if(_parms._treatment_column != null) {
      Vec u = _train.remove(_parms._treatment_column);
      if(u == null)
        error("_treatment_column","Treatment column '" + _parms._treatment_column + "' not found in the training frame");
      else {
        _treatment = u;
        if(!u.isCategorical())
          error("_treatment_column","Invalid treatment column '" + _parms._treatment_column + "', treatment column must be categorical");
        _weights = u;
        if(u.naCnt() > 0)
          error("_treatment_column","Treatment column cannot have missing values.");
        if(u.isCategorical() && u.domain().length != 2)
          error("_treatment_column","Treatment column must contains only 0 or 1");
        if(u.min() != 0)
          error("_treatment_column","Min. treatment column value must be 0");
        if(u.max() != 1)
          error("_treatment_column","Max. treatment column value must be 1");
        _train.add(_parms._treatment_column, u);
        ++res;
      }
    } else {
      _treatment = null;
      assert(!hasTreatmentCol());
    }
    if(isSupervised() && _parms._response_column != null) {
      _response = _train.remove(_parms._response_column);
      if (_response == null) {
        if (isSupervised())
          error("_response_column", "Response column '" + _parms._response_column + "' not found in the training frame");
      } else {
        if(_response == _offset)
          error("_response_column", "Response column must be different from offset_column");
        if(_response == _weights)
          error("_response_column", "Response column must be different from weights_column");
        if(_response == _fold)
          error("_response_column", "Response column must be different from fold_column");
        if(_response == _treatment)
          error("_response_column", "Response column must be different from treatment_column");
        _train.add(_parms._response_column, _response);
        ++res;
      }
    } else {
      _response = null;
    }
    return res;
  }

  protected boolean ignoreStringColumns() {
    return true;
  }
  protected boolean ignoreConstColumns() {
    return _parms._ignore_const_cols;
  }
  protected boolean ignoreUuidColumns() {
    return true;
  }

  /**
   * Ignore constant columns, columns with all NAs and strings.
   * @param npredictors
   * @param expensive
   */
  protected void ignoreBadColumns(int npredictors, boolean expensive){
    // Drop all-constant and all-bad columns.
    if(_parms._ignore_const_cols)
      new FilterCols(npredictors) {
        @Override protected boolean filter(Vec v, String name) {
          boolean isBad = v.isBad();
          boolean skipConst = ignoreConstColumns() && v.isConst(canLearnFromNAs()); // NAs can have information
          boolean skipString = ignoreStringColumns() && v.isString();
          boolean skipUuid = ignoreUuidColumns() && v.isUUID();
          boolean skip = isBad || skipConst || skipString || skipUuid;
          return skip;
        }
      }.doIt(_train,"Dropping bad and constant columns: ",expensive);
  }

  /**
   * Indicates that the algorithm is able to natively learn from NA values, there is no need
   * to eg. impute missing values or skip rows that have missing values.
   * @return whether model builder natively supports NAs
   */
  protected boolean canLearnFromNAs() {
    return false;
  }
  
  /**
   * Checks response variable attributes and adds errors if response variable is unusable.
   */
  protected void checkResponseVariable() {

    if (_response != null && (!_response.isNumeric() && !_response.isCategorical() && !_response.isTime())) {
      error("_response_column", "Use numerical, categorical or time variable. Currently used " + _response.get_type_str());
    }

  }

  /**
   * Ignore invalid columns (columns that have a very high max value, which can cause issues in DHistogram)
   * @param npredictors
   * @param expensive
   */
  protected void ignoreInvalidColumns(int npredictors, boolean expensive){}

  /**
   * Makes sure the final model will fit in memory.
   *
   * Note: This method should not be overridden (override checkMemoryFootPrint_impl instead). It is
   * not declared 'final' to not to break 3rd party implementations. It might be declared final in the future
   * if necessary.
   */
  protected void checkMemoryFootPrint() {
    if (Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.noMemoryCheck")) return; // skip check if disabled
    checkMemoryFootPrint_impl();
  }

  /**
   * Override this method to call error() if the model is expected to not fit in memory, and say why
   */
  protected void checkMemoryFootPrint_impl() {}

  transient double [] _distribution;
  transient protected double [] _priorClassDist;

  protected boolean computePriorClassDistribution(){
    return isClassifier();
  }

  /** A list of field validation issues. */
  public ValidationMessage[] _messages = new ValidationMessage[0];
  private int _error_count = -1; // -1 ==> init not run yet, for those Jobs that have an init, like ModelBuilder. Note, this counts ONLY errors, not WARNs and etc.
  public int error_count() { assert _error_count >= 0 : "init() not run yet"; return _error_count; }
  public void hide (String field_name, String message) { message(Log.TRACE, field_name, message); }
  public void info (String field_name, String message) { message(Log.INFO , field_name, message); }
  public void warn (String field_name, String message) { message(Log.WARN , field_name, message); }
  public void error(String field_name, String message) { message(Log.ERRR , field_name, message); _error_count++; }
  public void clearValidationErrors() {
    _messages = new ValidationMessage[0];
    _error_count = 0;
  }

  public void message(byte log_level, String field_name, String message) {
    _messages = Arrays.copyOf(_messages, _messages.length + 1);
    _messages[_messages.length - 1] = new ValidationMessage(log_level, field_name, message);

    if (log_level == Log.ERRR) _error_count++;
  }

  public ValidationMessage[] getMessagesByFieldAndSeverity(String fieldName, byte logLevel) {
    return Arrays.stream(_messages)
            .filter((msg) -> msg._field_name.equals(fieldName) && msg._log_level == logLevel)
            .toArray(ValidationMessage[]::new);
  }

 /** Get a string representation of only the ERROR ValidationMessages (e.g., to use in an exception throw). */
  public String validationErrors() {
    return validationMessage(Log.ERRR);
  }

  public String validationWarnings() {
    return validationMessage(Log.WARN);
  }

  private String validationMessage(int level) {
    StringBuilder sb = new StringBuilder();
    for( ValidationMessage vm : _messages )
      if( vm._log_level == level )
        sb.append(vm.toString()).append("\n");
    return sb.toString();
  }

  /** Can be an ERROR, meaning the parameters can't be used as-is,
   *  a TRACE, which means the specified field should be hidden given
   *  the values of other fields, or a WARN or INFO for informative
   *  messages to the user. */
  public static final class ValidationMessage extends Iced {
    final byte _log_level; // See util/Log.java for levels
    final String _field_name;
    final String _message;
    public ValidationMessage(byte log_level, String field_name, String message) {
      _log_level = log_level;
      _field_name = field_name;
      _message = message;
      Log.log(log_level,field_name + ": " + message);
    }
    public int log_level() { return _log_level; }
    public String field() { return _field_name; }
    public String message() { return _message; }
    @Override public String toString() { return Log.LVLS[_log_level] + " on field: " + _field_name + ": " + _message; }
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
   */
  public void init(boolean expensive) {
    // Log parameters
    if( expensive && logMe() ) {
      Log.info("Building H2O " + this.getClass().getSimpleName() + " model with these parameters:");
      Log.info(new String(_parms.writeJSON(new AutoBuffer()).buf()));
    }
    // NOTE: allow re-init:
    clearInitState();
    initWorkspace(expensive);
    assert _parms != null;      // Parms must already be set in

    if( _parms._train == null ) {
      if (expensive)
        error("_train", "Missing training frame");
      return;
    } else {
      // Catch the number #1 reason why a junit test is failing non-deterministically on a missing Vec: forgotten DKV update after a Frame is modified locally
      new ObjectConsistencyChecker(_parms._train).doAllNodes();
    }
    Frame tr = _train != null ? _train : _parms.train();
    if (tr == null) {
      error("_train", "Missing training frame: "+_parms._train); 
      return;
    }
    if (expensive) Scope.protect(_parms.train(), _parms.valid());
    setTrain(new Frame(null /* not putting this into KV */, tr._names.clone(), tr.vecs().clone()));
    if (expensive) {
      _parms.getOrMakeRealSeed();
    }
    if (_parms._categorical_encoding.needsResponse() && !isSupervised()) {
      error("_categorical_encoding", "Categorical encoding scheme cannot be "
          + _parms._categorical_encoding.toString() + " - no response column available.");
    }
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
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO && _parms._fold_assignment != null && _parms != null) {
        error("_fold_assignment", "Fold assignment is not allowed in conjunction with a fold column.");
      }
    }
    if (_parms._nfolds > 1) {
      hide("_fold_column", "Fold column is ignored when nfolds > 1.");
    }
    // hide cross-validation parameters unless cross-val is enabled
    if (!nFoldCV()) {
      hide("_keep_cross_validation_models", "Only for cross-validation.");
      hide("_keep_cross_validation_predictions", "Only for cross-validation.");
      hide("_keep_cross_validation_fold_assignment", "Only for cross-validation.");
      hide("_fold_assignment", "Only for cross-validation.");
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO && _parms._fold_assignment != null) {
        error("_fold_assignment", "Fold assignment is only allowed for cross-validation.");
      }
    }
    if (_parms._distribution == DistributionFamily.modified_huber) {
      error("_distribution", "Modified Huber distribution is not supported yet.");
    }
    if (_parms._distribution != DistributionFamily.tweedie) {
      hide("_tweedie_power", "Only for Tweedie Distribution.");
    }
    if (_parms._tweedie_power <= 1 || _parms._tweedie_power >= 2) {
      error("_tweedie_power", "Tweedie power must be between 1 and 2 (exclusive). " +
              "For tweedie power = 1, use Poisson distribution. For tweedie power = 2, use Gamma distribution.");
    }

    // Drop explicitly dropped columns
    if( _parms._ignored_columns != null ) {
      Set<String> ignoreColumnSet = new HashSet<>(Arrays.asList(_parms._ignored_columns));
      Collection<String> usedColumns = _parms.getUsedColumns(tr._names);
      ignoreColumnSet.removeAll(usedColumns);
      String[] actualIgnoredColumns = ignoreColumnSet.toArray(new String[0]);
      _train.remove(actualIgnoredColumns);
      if (expensive) Log.info("Dropping ignored columns: " + Arrays.toString(actualIgnoredColumns));
    }

    if(_parms._checkpoint != null) {
      if(DKV.get(_parms._checkpoint) == null){
          error("_checkpoint", "Checkpoint has to point to existing model!");
      }
      // Do not ignore bad columns, as only portion of the training data might be supplied (e.g. continue from checkpoint)
      final Model checkpointedModel = _parms._checkpoint.get();
      final String[] warnings = checkpointedModel.adaptTestForTrain(_train, expensive, false);
      for (final String warning : warnings){
          warn("_checkpoint", warning);
      }
      separateFeatureVecs(); // set MB's fields (like response)
    } else {
      // Drop all non-numeric columns (e.g., String and UUID).  No current algo
      // can use them, and otherwise all algos will then be forced to remove
      // them.  Text algos (grep, word2vec) take raw text columns - which are
      // numeric (arrays of bytes).
      ignoreBadColumns(separateFeatureVecs(), expensive);
      ignoreInvalidColumns(separateFeatureVecs(), expensive);
      checkResponseVariable();
    }

    // Rebalance train and valid datasets (after invalid/bad columns are dropped)
    if (expensive && error_count() == 0 && _parms._auto_rebalance) {
      setTrain(rebalance(_train, false, _result + ".temporary.train"));
      separateFeatureVecs(); // need to reset MB's fields (like response) after rebalancing
      _valid = rebalance(_valid, false, _result + ".temporary.valid");
    }

    // Check that at least some columns are not-constant and not-all-NAs
    if (_train.numCols() == 0)
      error("_train", "There are no usable columns to generate model");

    if(isSupervised()) {
      if(_response != null) {
        if (_parms._distribution != DistributionFamily.tweedie) {
          hide("_tweedie_power", "Tweedie power is only used for Tweedie distribution.");
        }
        if (_parms._distribution != DistributionFamily.quantile) {
          hide("_quantile_alpha", "Quantile (alpha) is only used for Quantile regression.");
        }
        if (expensive) checkDistributions();
        _nclass = init_getNClass();
        if (_parms._check_constant_response && _response.isConst()) {
          error("_response", "Response cannot be constant.");
        }
        if (validateBinaryResponse() && _nclass == 1 && _response.isBinary(true)) {
          warn("_response", 
                  "We have detected that your response column has only 2 unique values (0/1). " +
                  "If you wish to train a binary model instead of a regression model, " +
                  "convert your target column to categorical before training."
          );
        }
      }
      if (! _parms._balance_classes)
        hide("_max_after_balance_size", "Balance classes is false, hide max_after_balance_size");
      else if (_parms._weights_column != null && _weights != null && !_weights.isBinary())
        error("_balance_classes", "Balance classes and observation weights are not currently supported together.");
      if( _parms._max_after_balance_size <= 0.0 )
        error("_max_after_balance_size","Max size after balancing needs to be positive, suggest 1.0f");

      if( _train != null ) {
        if (_train.numCols() <= 1 && !getClass().toString().equals("class hex.gam.GAM")) // gam can have zero predictors
          error("_train", "Training data must have at least 2 features (incl. response).");
        if( null == _parms._response_column) {
          error("_response_column", "Response column parameter not set.");
          return;
        }
        
        if(_response != null && computePriorClassDistribution()) {
          if (isClassifier() && isSupervised()) {
            if(_parms.getDistributionFamily() == DistributionFamily.quasibinomial){
              String[] quasiDomains = new VecUtils.CollectDoubleDomain(null,2).doAll(_response).stringDomain(_response.isInt());
              MRUtils.ClassDistQuasibinomial cdmt =
                      _weights != null ? new MRUtils.ClassDistQuasibinomial(quasiDomains).doAll(_response, _weights) : new MRUtils.ClassDistQuasibinomial(quasiDomains).doAll(_response);
              _distribution = cdmt.dist();
              _priorClassDist = cdmt.relDist();
            } else {
              MRUtils.ClassDist cdmt =
                      _weights != null ? new MRUtils.ClassDist(nclasses()).doAll(_response, _weights) : new MRUtils.ClassDist(nclasses()).doAll(_response);
              _distribution = cdmt.dist();
              _priorClassDist = cdmt.relDist();
              
            }
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
        hide("_max_confusion_matrix_size", "Only for multi-class classification problems.");
      }
      if( !_parms._balance_classes ) {
        hide("_max_after_balance_size", "Only used with balanced classes");
        hide("_class_sampling_factors", "Class sampling factors is only applicable if balancing classes.");
      }
    }
    else {
      if (!isResponseOptional()) {
        hide("_response_column", "Ignored for unsupervised methods.");
        _vresponse = null;
      }
      hide("_balance_classes", "Ignored for unsupervised methods.");
      hide("_class_sampling_factors", "Ignored for unsupervised methods.");
      hide("_max_after_balance_size", "Ignored for unsupervised methods.");
      hide("_max_confusion_matrix_size", "Ignored for unsupervised methods.");
      _response = null;
      _nclass = 1;
    }

    if( _nclass > Model.Parameters.MAX_SUPPORTED_LEVELS ) {
      error("_nclass", "Too many levels in response column: " + _nclass + ", maximum supported number of classes is " + Model.Parameters.MAX_SUPPORTED_LEVELS + ".");
    }

    // Build the validation set to be compatible with the training set.
    // Toss out extra columns, complain about missing ones, remap categoricals
    Frame va = _parms.valid();  // User-given validation set
    if (va != null) {
      if (isResponseOptional() && _parms._response_column != null && _response == null) {
        _vresponse = va.vec(_parms._response_column);
      }
      _valid = adaptFrameToTrain(va, "Validation Frame", "_validation_frame", expensive, false);  // see PUBDEV-7785
      if (!isResponseOptional() || (_parms._response_column != null && _valid.find(_parms._response_column) >= 0)) {
        _vresponse = _valid.vec(_parms._response_column);
      }
    } else {
      _valid = null;
      _vresponse = null;
    }

    if (expensive) {
      boolean scopeTrack = !_parms._is_cv_model;
      Frame newtrain = applyPreprocessors(_train, true, scopeTrack);
      newtrain = encodeFrameCategoricals(newtrain, scopeTrack); //we could turn this into a preprocessor later
      if (newtrain != _train) {
        _origTrain = _train;
        _origNames = _train.names();
        _origDomains = _train.domains();
        setTrain(newtrain);
        separateFeatureVecs(); //fix up the pointers to the special vecs
      } else {
        _origTrain = null;
      }
      if (_valid != null) {
        Frame newvalid = applyPreprocessors(_valid, false, scopeTrack);
        newvalid = encodeFrameCategoricals(newvalid, scopeTrack /* for CV, need to score one more time in outer loop */);
        setValid(newvalid);
      }
      boolean restructured = false;
      Vec[] vecs = _train.vecs();
      for (int j = 0; j < vecs.length; ++j) {
        Vec v = vecs[j];
        if (v == _response || v == _fold) continue;
        if (v.isCategorical() && shouldReorder(v)) {
          final int len = v.domain().length;
          Log.info("Reordering categorical column " + _train.name(j) + " (" + len + " levels) based on the mean (weighted) response per level.");
          VecUtils.MeanResponsePerLevelTask mrplt = new VecUtils.MeanResponsePerLevelTask(len).doAll(v,
                  _parms._weights_column != null ? _train.vec(_parms._weights_column) : v.makeCon(1.0),
                  _train.vec(_parms._response_column));
          double[] meanWeightedResponse  = mrplt.meanWeightedResponse;

          // Option 1: Order the categorical column by response to make better splits
          int[] idx=new int[len];
          for (int i=0;i<len;++i) idx[i] = i;
          ArrayUtils.sort(idx, meanWeightedResponse);
          int[] invIdx=new int[len];
          for (int i=0;i<len;++i) invIdx[idx[i]] = i;
          Vec vNew = new VecUtils.ReorderTask(invIdx).doAll(1, Vec.T_NUM, new Frame(v)).outputFrame().anyVec();
          String[] newDomain = new String[len];
          for (int i = 0; i < len; ++i) newDomain[i] = v.domain()[idx[i]];
          vNew.setDomain(newDomain);
          vecs[j] = vNew;
          restructured = true;
        }
      }
      if (restructured)
        _train.restructure(_train.names(), vecs);
    }
    boolean names_may_differ = _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.Binary;
    boolean names_differ = _valid !=null && ArrayUtils.difference(_train._names, _valid._names).length != 0;;
    assert (!expensive || names_may_differ || !names_differ);
    if (names_differ && names_may_differ) {
      for (String name : _train._names)
        assert(ArrayUtils.contains(_valid._names, name)) : "Internal error during categorical encoding: training column " + name + " not in validation frame with columns " + Arrays.toString(_valid._names);
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
    }
    else { // early stopping is enabled
      checkEarlyStoppingReproducibility();
      if (validateStoppingMetric()) {
        if (isClassifier()) {
          if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.deviance && !getClass().getSimpleName().contains("GLM")) {
            error("_stopping_metric", "Stopping metric cannot be deviance for classification.");
          }
        } else {
          if (_parms._stopping_metric.isClassificationOnly()) {
            error("_stopping_metric", "Stopping metric cannot be " + _parms._stopping_metric + " for regression.");
          }
        }
      }
    }
    if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.custom || _parms._stopping_metric == ScoreKeeper.StoppingMetric.custom_increasing) {
      checkCustomMetricForEarlyStopping();
    }
    if (_parms._max_runtime_secs < 0) {
      error("_max_runtime_secs", "Max runtime (in seconds) must be greater than 0 (or 0 for unlimited).");
    }
    if (!StringUtils.isNullOrEmpty(_parms._export_checkpoints_dir)) {
      if (!_parms._is_cv_model) {
      // we do not need to check if the checkpoint directory is writeable on CV-models, it was already checked on the main model
        if (!H2O.getPM().isWritableDirectory(_parms._export_checkpoints_dir)) {
          error("_export_checkpoints_dir", "Checkpoints directory path must point to a writable path.");
        }
      }
    }
  }

  protected void checkCustomMetricForEarlyStopping() {
    if (_parms._custom_metric_func == null) {
      error("_custom_metric_func", "Custom metric function needs to be defined in order to use it for early stopping.");
    }
  }

  /**
   * Adapts a given frame to the same schema as the training frame.
   * This includes encoding of categorical variables (if expensive is enabled).
   *
   * Note: This method should only be used during ModelBuilder initialization - it should be called in init(..) method.
   *
   * @param fr input frame
   * @param frDesc frame description, eg. "Validation Frame" - will be shown in validation error messages
   * @param field name of a field for validation errors
   * @param expensive indicates full ("expensive") processing
   * @return adapted frame
   */
  public Frame init_adaptFrameToTrain(Frame fr, String frDesc, String field, boolean expensive) {
    Frame adapted = adaptFrameToTrain(fr, frDesc, field, expensive, false);
    if (expensive)
      adapted = encodeFrameCategoricals(adapted, true);
    return adapted;
  }

  private Frame adaptFrameToTrain(Frame fr, String frDesc, String field, boolean expensive, boolean catEncoded) {
    if (fr.numRows()==0) error(field, frDesc + " must have > 0 rows.");
    Frame adapted = new Frame(null /* not putting this into KV */, fr._names.clone(), fr.vecs().clone());
    try {
      String[] msgs = Model.adaptTestForTrain(
              adapted, 
              null, 
              null, 
              _train._names, 
              _train.domains(),
              _parms, 
              expensive,
              true, 
              null, 
              getToEigenVec(), 
              _workspace.getToDelete(expensive), 
              catEncoded
      );
      Vec response = adapted.vec(_parms._response_column);
      if (response == null && _parms._response_column != null && !isResponseOptional())
        error(field, frDesc + " must have a response column '" + _parms._response_column + "'.");
      if (expensive) {
        for (String s : msgs) {
          Log.info(s);
          warn(field, s);
        }
      }
    } catch (IllegalArgumentException iae) {
      error(field, iae.getMessage());
    }
    return adapted;
  }

  private Frame applyPreprocessors(Frame fr, boolean isTraining, boolean scopeTrack) {
    if (_parms._preprocessors == null) return fr;

    for (Key<ModelPreprocessor> key : _parms._preprocessors) {
      DKV.prefetch(key);
    }
    Frame result = fr;
    Frame encoded;
    for (Key<ModelPreprocessor> key : _parms._preprocessors) {
      ModelPreprocessor preprocessor = key.get();
      encoded = isTraining ? preprocessor.processTrain(result, _parms) : preprocessor.processValid(result, _parms);
      if (encoded != result) trackEncoded(encoded, scopeTrack);
      result = encoded;
    }
    if (!scopeTrack) Scope.untrack(result); // otherwise encoded frame is fully removed on CV model completion, raising exception when computing CV scores.
    return result;
  }

  private Frame encodeFrameCategoricals(Frame fr, boolean scopeTrack) {
    Frame encoded = FrameUtils.categoricalEncoder(
            fr, 
            _parms.getNonPredictors(),
            _parms._categorical_encoding, 
            getToEigenVec(), 
            _parms._max_categorical_levels
    );
    if (encoded != fr) trackEncoded(encoded, scopeTrack);
    return encoded;
  }
  
  private void trackEncoded(Frame fr, boolean scopeTrack) {
    assert fr._key != null;
    if (scopeTrack)
      Scope.track(fr);
    else
      _workspace.getToDelete(true).put(fr._key, Arrays.toString(Thread.currentThread().getStackTrace()));
  }

  /**
   * Rebalance a frame for load balancing
   * @param original_fr Input frame
   * @param local Whether to only create enough chunks to max out all cores on one node only
   *              WARNING: This behavior is not actually implemented in the methods defined in this class, the default logic
   *              doesn't take this parameter into consideration.
   * @param name Name of rebalanced frame
   * @return Frame that has potentially more chunks
   */
  protected Frame rebalance(final Frame original_fr, boolean local, final String name) {
    if (original_fr == null) return null;
    int chunks = desiredChunks(original_fr, local);
    String dataset = name.substring(name.length()-5);
    double rebalanceRatio = rebalanceRatio();
    int nonEmptyChunks = original_fr.anyVec().nonEmptyChunks();
    if (nonEmptyChunks >= chunks * rebalanceRatio) {
      if (chunks>1)
        Log.info(dataset + " dataset already contains " + nonEmptyChunks + " (non-empty) " +
              " chunks. No need to rebalance. [desiredChunks=" + chunks, ", rebalanceRatio=" + rebalanceRatio + "]");
      return original_fr;
    }
    raiseReproducibilityWarning(dataset, chunks);
    Log.info("Rebalancing " + dataset  + " dataset into " + chunks + " chunks.");
    Key newKey = Key.makeUserHidden(name + ".chunks" + chunks);
    RebalanceDataSet rb = new RebalanceDataSet(original_fr, newKey, chunks);
    H2O.submitTask(rb).join();
    Frame rebalanced_fr = DKV.get(newKey).get();
    Scope.track(rebalanced_fr);
    return rebalanced_fr;
  }

  protected void raiseReproducibilityWarning(String datasetName, int chunks) {
    // for children
  }

  private double rebalanceRatio() {
    String mode = H2O.getCloudSize() == 1 ? "single" : "multi";
    String ratioStr = getSysProperty("rebalance.ratio." + mode, "1.0");
    return Double.parseDouble(ratioStr);
  }

  /**
   * Find desired number of chunks. If fewer, dataset will be rebalanced.
   * @return Lower bound on number of chunks after rebalancing.
   */
  protected int desiredChunks(final Frame original_fr, boolean local) {
    if (H2O.getCloudSize() > 1 && Boolean.parseBoolean(getSysProperty("rebalance.enableMulti", "false")))
      return desiredChunkMulti(original_fr);
    else
      return desiredChunkSingle(original_fr);
  }

  // single-node version (original version)
  private int desiredChunkSingle(final Frame originalFr) {
    return Math.min((int) Math.ceil(originalFr.numRows() / 1e3), H2O.NUMCPUS);
  }

  // multi-node version (experimental version)
  private int desiredChunkMulti(final Frame fr) {
    for (int type : fr.types()) {
      if (type != Vec.T_NUM && type != Vec.T_CAT) {
        Log.warn("Training frame contains columns non-numeric/categorical columns. Using old rebalance logic.");
        return desiredChunkSingle(fr);
      }
    }
    // estimate size of the Frame on disk as if it was represented in a binary _uncompressed_ format with no overhead
    long itemCnt = 0;
    for (Vec v : fr.vecs())
      itemCnt += v.length() - v.naCnt();
    final int itemSize = 4; // magic constant size of both Numbers and Categoricals
    final long size = Math.max(itemCnt * itemSize, fr.byteSize());
    final int desiredChunkSize = FileVec.calcOptimalChunkSize(size, fr.numCols(),
            fr.numCols() * itemSize, H2O.NUMCPUS, H2O.getCloudSize(), false, true);
    final int desiredChunks = (int) ((size / desiredChunkSize) + (size % desiredChunkSize > 0 ? 1 : 0));
    Log.info("Calculated optimal number of chunks = " + desiredChunks);
    return desiredChunks;
  }

  protected String getSysProperty(String name, String def) {
    return System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + name, def);
  }

  protected int init_getNClass() {
    int nclass = _response.isCategorical() ? _response.cardinality() : 1;
    if (_parms._distribution == DistributionFamily.quasibinomial) {
      nclass = 2;
    }
    return nclass;
  }

  public void checkDistributions() {
    if (_parms._distribution == DistributionFamily.poisson) {
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Poisson distribution.");
    } else if (_parms._distribution == DistributionFamily.gamma) {
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Gamma distribution.");
    } else if (_parms._distribution == DistributionFamily.tweedie) {
      if (_parms._tweedie_power >= 2 || _parms._tweedie_power <= 1)
        error("_tweedie_power", "Tweedie power must be between 1 and 2.");
      if (_response.min() < 0)
        error("_response", "Response must be non-negative for Tweedie distribution.");
    } else if (_parms._distribution == DistributionFamily.quantile) {
      if (_parms._quantile_alpha > 1 || _parms._quantile_alpha < 0)
        error("_quantile_alpha", "Quantile alpha must be between 0 and 1.");
    } else if (_parms._distribution == DistributionFamily.huber) {
      if (_parms._huber_alpha <0 || _parms._huber_alpha>1)
        error("_huber_alpha", "Huber alpha must be between 0 and 1.");
    }
  }

  transient public HashSet<String> _removedCols = new HashSet<>();
  public abstract class FilterCols {
    final int _specialVecs; // special vecs to skip at the end
    public FilterCols(int n) {_specialVecs = n;}

    abstract protected boolean filter(Vec v, String name);

    public void doIt( Frame f, String msg, boolean expensive ) {
      List<Integer> rmcolsList = new ArrayList<>();
      for( int i = 0; i < f.vecs().length - _specialVecs; i++ )
        if( filter(f.vec(i), f._names[i])) rmcolsList.add(i);
      if( !rmcolsList.isEmpty() ) {
        _removedCols = new HashSet<>(rmcolsList.size());
        int[] rmcols = new int[rmcolsList.size()];
        for (int i=0;i<rmcols.length;++i) {
          rmcols[i]=rmcolsList.get(i);
          _removedCols.add(f._names[rmcols[i]]);
        }
        f.remove(rmcols); //bulk-remove
        msg += _removedCols.toString();
        warn("_train", msg);
        if (expensive) Log.info(msg);
      }
    }
  }

  //stitch together holdout predictions into one large Frame
  Frame combineHoldoutPredictions(Key<Frame>[] predKeys, Key<Frame> key) {
    int precision = _parms._keep_cross_validation_predictions_precision;
    if (precision < 0) {
      precision = isClassifier() ? 8 : 0;
    }
    return combineHoldoutPredictions(predKeys, key, precision);
  }

  static Frame combineHoldoutPredictions(Key<Frame>[] predKeys, Key<Frame> key, int precision) {
    int N = predKeys.length;
    Frame template = predKeys[0].get();
    Vec[] vecs = new Vec[N*template.numCols()];
    int idx=0;
    for (Key<Frame> predKey : predKeys)
      for (int j = 0; j < predKey.get().numCols(); ++j)
        vecs[idx++] = predKey.get().vec(j);
    HoldoutPredictionCombiner combiner = makeHoldoutPredictionCombiner(N, template.numCols(), precision);
    return combiner.doAll(template.types(),new Frame(vecs))
            .outputFrame(key, template.names(),template.domains());
  }

  static HoldoutPredictionCombiner makeHoldoutPredictionCombiner(int folds, int cols, int precision) {
    if (precision < 0) {
      throw new IllegalArgumentException("Precision cannot be negative, got precision = " + precision);
    } else if (precision == 0) {
      return new HoldoutPredictionCombiner(folds, cols);
    } else {
      return new ApproximatingHoldoutPredictionCombiner(folds, cols, precision);
    }
  }
  
  // helper to combine multiple holdout prediction Vecs (each only has 1/N-th filled with non-zeros) into 1 Vec
  static class HoldoutPredictionCombiner extends MRTask<HoldoutPredictionCombiner> {
    int _folds, _cols;
    public HoldoutPredictionCombiner(int folds, int cols) { _folds=folds; _cols=cols; }
    @Override public final void map(Chunk[] cs, NewChunk[] nc) {
      for (int c = 0; c < _cols; c++) {
        double[] vals = new double[cs[0].len()];
        ChunkVisitor.CombiningDoubleAryVisitor visitor = new ChunkVisitor.CombiningDoubleAryVisitor(vals);
        for (int f = 0; f < _folds; f++) {
          cs[f * _cols + c].processRows(visitor, 0, vals.length);
          visitor.reset();
        }
        populateChunk(nc[c], vals);
      }
    }
    protected void populateChunk(NewChunk nc, double[] vals) {
      nc.setDoubles(vals);
    }
  }

  static class ApproximatingHoldoutPredictionCombiner extends HoldoutPredictionCombiner {
    private final int _precision;
    public ApproximatingHoldoutPredictionCombiner(int folds, int cols, int precision) { 
      super(folds, cols);
      _precision = precision;
    }
    @Override
    protected void populateChunk(NewChunk nc, double[] vals) {
      final long scale = PrettyPrint.pow10i(_precision); 
      for (double val : vals) {
        if (Double.isNaN(val))
          nc.addNA();
        else {
          long approx = Math.round(val * scale);
          nc.addNum(approx, -_precision);
        }
      }
    }
  }

  private TwoDimTable makeCrossValidationSummaryTable(Key[] cvmodels) {
    if (cvmodels == null || cvmodels.length == 0) return null;
    int N = cvmodels.length;
    int extra_length=2; //mean/sigma/cv1/cv2/.../cvN
    String[] colTypes = new String[N+extra_length];
    Arrays.fill(colTypes, "float");
    String[] colFormats = new String[N+extra_length];
    Arrays.fill(colFormats, "%f");
    String[] colNames = new String[N+extra_length];
    colNames[0] = "mean";
    colNames[1] = "sd";
    for (int i=0;i<N;++i)
    colNames[i+extra_length] = "cv_" + (i+1) + "_valid";
    Set<String> excluded = new HashSet<>();
    excluded.add("total_rows");
    excluded.add("makeSchema");
    excluded.add("hr");
    excluded.add("frame");
    excluded.add("model");
    excluded.add("remove");
    excluded.add("cm");
    excluded.add("auc_obj");
    excluded.add("aucpr");
    if (null == _parms._custom_metric_func) {  // hide custom metrics when not available
      excluded.add("custom");
      excluded.add("custom_increasing");
    }
    List<Method> methods = new ArrayList<>();
    {
      Model m = DKV.getGet(cvmodels[0]);
      ModelMetrics mm = m._output._validation_metrics;

      if (mm != null) {

        for (Method meth : mm.getClass().getMethods()) {
          if (excluded.contains(meth.getName())) continue;
          try {
            double c = (double) meth.invoke(mm);
            methods.add(meth);
          } catch (Exception ignored) {}
        }

        ConfusionMatrix cm = mm.cm();
        if (cm != null) {
          for (Method meth : cm.getClass().getMethods()) {
            if (excluded.contains(meth.getName())) continue;
            try {
              double c = (double) meth.invoke(cm);
              methods.add(meth);
            } catch (Exception ignored) {}
          }
        }
      }
    }

    // make unique, and sort alphabetically
    Set<String> rowNames=new TreeSet<>();
    for (Method m : methods) rowNames.add(m.getName());
    List<Method> meths = new ArrayList<>();
    OUTER:
    for (String n : rowNames)
      for (Method m : methods)
        if (m.getName().equals(n)) { //find the first method that has that name
          meths.add(m);
          continue OUTER;
        }

    int numMetrics = rowNames.size();

    TwoDimTable table = new TwoDimTable("Cross-Validation Metrics Summary",
            null,
            rowNames.toArray(new String[0]), colNames, colTypes, colFormats, "");

    MathUtils.BasicStats stats = new MathUtils.BasicStats(numMetrics);
    double[][] vals = new double[N][numMetrics];
    int i = 0;
    for (Key<Model> km : cvmodels) {
      Model m = DKV.getGet(km);
      if (m==null) continue;
      ModelMetrics mm = m._output._validation_metrics;
      int j=0;
      for (Method meth : meths) {
        if (excluded.contains(meth.getName())) continue;
        try {
          double val = (double) meth.invoke(mm);
          vals[i][j] = val;
          table.set(j++, i+extra_length, (float)val);
        } catch (Throwable e) { }
        if (mm.cm()==null) continue;
        try {
          double val = (double) meth.invoke(mm.cm());
          vals[i][j] = val;
          table.set(j++, i+extra_length, (float)val);
        } catch (Throwable e) { }
      }
      i++;
    }
    MathUtils.SimpleStats simpleStats = new MathUtils.SimpleStats(numMetrics);
    for (i=0;i<N;++i)
      simpleStats.add(vals[i],1);
    for (i=0;i<numMetrics;++i) {
      table.set(i, 0, (float)simpleStats.mean()[i]);
      table.set(i, 1, (float)simpleStats.sigma()[i]);
    }
    Log.info(table);
    return table;
  }

  /**
   * Overridable Model Builder name used in generated code, in case the name of the ModelBuilder class is not suitable.
   *
   * @return Name of the builder to be used in generated code
   */
  public String getName() {
    return getClass().getSimpleName().toLowerCase();
  }

  private void cleanUp() {
    _workspace.cleanUp();
  }

  @SuppressWarnings("WeakerAccess") // optionally allow users create workspace directly (instead of relying on init) 
  protected final void initWorkspace(boolean expensive) {
    if (expensive)
      _workspace = new Workspace(true);
  }

  static class Workspace {
    private final IcedHashMap<Key,String> _toDelete;

    private Workspace(boolean expensive) {
      _toDelete = expensive ? new IcedHashMap<>() : null;
    }

    IcedHashMap<Key, String> getToDelete(boolean expensive) {
      if (! expensive)
        return null; // incorrect usages during "inexpensive" initialization will fail
      if (_toDelete == null) {
        throw new IllegalStateException("ModelBuilder was not correctly initialized. " +
                "Expensive phase requires field `_toDelete` to be non-null. " +
                "Does your implementation of init method call super.init(true) or alternatively initWorkspace(true)?");
      }
      return _toDelete;
    }

    /** must be called before Scope.exit() */
    void cleanUp() {
      if (_toDelete == null) return;
      // converting Workspace-tracked keys to Scope-tracked keys
      // much safer than strictly removing everything as frame like training/validation frames are protected in Scope.
      Key[] tracked = _toDelete.keySet().toArray(new Key[0]);
      for (Key k: tracked) {
        Value v = DKV.get(k);
        if (v==null) continue;
        if (v.isFrame()) Scope.track(v.get(Frame.class));
        else if (v.isVec()) Scope.track(v.get(Vec.class));
        else Scope.track_generic(v.get(Keyed.class));
      }
    }
  }

  public PojoWriter makePojoWriter(Model<?, ?, ?> genericModel, MojoModel mojoModel) {
    throw new UnsupportedOperationException("MOJO Model for algorithm '" + mojoModel._algoName +
            "' doesn't support conversion to POJO.");
  }

}
