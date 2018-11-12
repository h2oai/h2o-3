package hex;

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

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Iced {

  public ToEigenVec getToEigenVec() { return null; }
  public boolean shouldReorder(Vec v) { return _parms._categorical_encoding.needsResponse() && isSupervised(); }

  transient private IcedHashMap<Key,String> _toDelete = new IcedHashMap<>();
  void cleanUp() { FrameUtils.cleanUp(_toDelete); }

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

  private long _start_time; //start time in msecs - only used for time-based stopping
  protected boolean timeout() {
    assert(_start_time > 0) : "Must set _start_time for each individual model.";
    return _parms._max_runtime_secs > 0 && System.currentTimeMillis() - _start_time > (long) (_parms._max_runtime_secs * 1e3);
  }
  protected boolean stop_requested() {
    return _job.stop_requested() || timeout();
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
  }

  /** Shared pre-existing Job and unique new result key */
  protected ModelBuilder(P parms, Job<M> job) {
    _job = job;
    _result = defaultKey(parms.algoName());
    _parms = parms;
  }

  /** List of known ModelBuilders with all default args; endlessly cloned by
   *  the GUI for new private instances, then the GUI overrides some of the
   *  defaults with user args. */
  private static String[] ALGOBASES = new String[0];
  public static String[] algos() { return ALGOBASES; }
  private static String[] SCHEMAS = new String[0];
  private static ModelBuilder[] BUILDERS = new ModelBuilder[0];

  /** One-time start-up only ModelBuilder, endlessly cloned by the GUI for the
   *  default settings. */
  protected ModelBuilder(P parms, boolean startup_once) { this(parms,startup_once,"hex.schemas."); }
  protected ModelBuilder(P parms, boolean startup_once, String externalSchemaDirectory ) {
    String base = getClass().getSimpleName().toLowerCase();
    if (!startup_once)
      throw H2O.fail("Algorithm " + base + " registration issue. It can only be called at startup.");
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

  /**
   *
   * @param urlName url name of the algo, for example gbm for Gradient Boosting Machine
   * @return true, if model supports exporting to POJO
   */
  public static boolean havePojo(String urlName) {
    return BUILDERS[ensureBuilderIndex(urlName)].havePojo();
  }

  /**
   *
   * @param urlName url name of the algo, for example gbm for Gradient Boosting Machine
   * @return true, if model supports exporting to MOJO
   */
  public static boolean haveMojo(String urlName) {
    return BUILDERS[ensureBuilderIndex(urlName)].haveMojo();
  }

  /**
   * Returns <strong>valid</strong> index of given url name in {@link #ALGOBASES} or throws an exception.
   * @param urlName url name to return the index for
   * @return valid index, if url name is not present in {@link #ALGOBASES} throws an exception
   */
  private static int ensureBuilderIndex(String urlName) {
    final String formattedName = urlName.toLowerCase();
    int index = ArrayUtils.find(ALGOBASES, formattedName);
    if (index < 0) {
      throw new IllegalArgumentException(String.format("Cannot find Builder for algo url name %s", formattedName));
    }
    return index;
  }


  /** Factory method to create a ModelBuilder instance for given the algo name.
   *  Shallow clone of both the default ModelBuilder instance and a Parameter. */
  public static <B extends ModelBuilder> B make(String algo, Job job, Key<Model> result) {
    int idx = ArrayUtils.find(ALGOBASES,algo.toLowerCase());
    if (idx < 0) {
      StringBuilder sb = new StringBuilder();
      sb.append("Unknown algo: '").append(algo).append("'; Extension report: ");
      Log.err(ExtensionManager.getInstance().makeExtensionReport(sb));
      throw new IllegalStateException("Algorithm '" + algo + "' is not registered. Available algos: [" +
              StringUtils.join(",", ALGOBASES)  + "]");
    }
    B mb = (B)BUILDERS[idx].clone();
    mb._job = job;
    mb._result = result;
    mb._parms = BUILDERS[idx]._parms.clone();
    return mb;
  }


  /** All the parameters required to build the model. */
  public P _parms;              // Not final, so CV can set-after-clone


  /** Training frame: derived from the parameter's training frame, excluding
   *  all ignored columns, all constant and bad columns, perhaps flipping the
   *  response column to an Categorical, etc.  */
  public final Frame train() { return _train; }
  protected transient Frame _train;

  public void setTrain(Frame train) {
    _train = train;
  }
  /** Validation frame: derived from the parameter's validation frame, excluding
   *  all ignored columns, all constant and bad columns, perhaps flipping the
   *  response column to a Categorical, etc.  Is null if no validation key is set.  */
  protected final Frame valid() { return _valid; }
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
    protected Driver(H2O.H2OCountedCompleter completer){ super(completer); }
    // Pull the boilerplate out of the computeImpl(), so the algo writer doesn't need to worry about the following:
    // 1) Scope (unless they want to keep data, then they must call Scope.untrack(Key<Vec>[]))
    // 2) Train/Valid frame locking and unlocking
    // 3) calling tryComplete()
    public void compute2() {
      try {
        Scope.enter();
        _parms.read_lock_frames(_job); // Fetch & read-lock input frames
        computeImpl();
        saveModelCheckpointIfConfigured();
      } finally {
        setFinalState();
        _parms.read_unlock_frames(_job);
        if (!_parms._is_cv_model) cleanUp(); //cv calls cleanUp on its own terms
        Scope.exit();
      }
      tryComplete();
    }
    public abstract void computeImpl();
  }

  private void setFinalState() {
    Key<M> reskey = dest();
    if (reskey == null) return;
    M res = reskey.get();
    if (res != null && res._output != null) {
      res._output._job = _job;
      res._output.stopClock();
    }
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
    if (H2O.ARGS.client) {
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
      RemoteTrainModelTask tmt = new RemoteTrainModelTask(_job, _job._result, _parms);
      H2ONode leader = H2O.CLOUD.leader();
      new RPC<>(leader, tmt).call().get();
      return _job;
    } else {
      return trainModel(); // use directly
    }
  }

  private static class RemoteTrainModelTask extends DTask<RemoteTrainModelTask> {
    private Job<Model> _job;
    private Key<Model> _key;
    private Model.Parameters _parms;
    @SuppressWarnings("unchecked")
    private RemoteTrainModelTask(Job job, Key key, Model.Parameters parms) {
      _job = (Job<Model>) job;
      _key = (Key<Model>) key;
      _parms = parms;
    }
    @Override
    public void compute2() {
      ModelBuilder mb = ModelBuilder.make(_parms.algoName(), _job, _key);
      mb._parms = _parms;
      mb.init(false); // validate parameters
      mb.trainModel();
      tryComplete();
    }
  }

  /** Method to launch training of a Model, based on its parameters. */
  final public Job<M> trainModel() {
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    _start_time = System.currentTimeMillis();
    if( !nFoldCV() )
      return _job.start(trainModelImpl(), _parms.progressUnits(), _parms._max_runtime_secs);

    // cross-validation needs to be forked off to allow continuous (non-blocking) progress bar
    return _job.start(new H2O.H2OCountedCompleter() {
                        @Override
                        public void compute2() {
                          computeCrossValidation();
                          tryComplete();
                        }
                        @Override
                        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
                          Log.warn("Model training job "+_job._description+" completed with exception: "+ex);
                          if (_job._result != null) {
                            try {
                              _job._result.remove(); //ensure there's no incomplete model left for manipulation after crash or cancellation
                            } catch (Exception logged) {
                              Log.warn("Exception thrown when removing result from job "+ _job._description, logged);
                            }
                          }
                          return true;
                        }
                      },
            (nFoldWork()+1/*main model*/) * _parms.progressUnits(), _parms._max_runtime_secs);
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
    _start_time = System.currentTimeMillis();
    if( !nFoldCV() ) trainModelImpl().compute2();
    else computeCrossValidation();
    return _result.get();
  }

  /** Model-specific implementation of model training
   * @return A F/J Job, which, when executed, does the build.  F/J is NOT started.  */
  abstract protected Driver trainModelImpl();

  /**
   * How many should be trained in parallel during N-fold cross-validation?
   * Train all CV models in parallel when parallelism is enabled, otherwise train one at a time
   * Each model can override this logic, based on parameters, dataset size, etc.
   * @return How many models to train in parallel during cross-validation
   */
  protected int nModelsInParallel() {
    if (!_parms._parallelize_cross_validation || _parms._max_runtime_secs != 0) return 1; //user demands serial building (or we need to honor the time constraints for all CV models equally)
    if (_train.byteSize() < 1e6) return _parms._nfolds; //for small data, parallelize over CV models
    return 1; //safe fallback
  }

  // Work for each requested fold
  protected int nFoldWork() {
    if( _parms._fold_column == null ) return _parms._nfolds;
    Vec f = _parms._train.get().vec(_parms._fold_column);
    Vec fc = VecUtils.toCategoricalVec(f);
    int N = fc.domain().length;
    fc.remove();
    return N;
  }

  /**
   * Default naive (serial) implementation of N-fold cross-validation
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  public void computeCrossValidation() {
    assert _job.isRunning();    // main Job is still running
    _job.setReadyForView(false); //wait until the main job starts to let the user inspect the main job
    final Integer N = nFoldWork();
    init(false);
    ModelBuilder<M, P, O>[] cvModelBuilders = null;
    try {
      Scope.enter();

      // Step 1: Assign each row to a fold
      final Vec foldAssignment = cv_AssignFold(N);

      // Step 2: Make 2*N binary weight vectors
      final Vec[] weights = cv_makeWeights(N, foldAssignment);

      // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
      cvModelBuilders = cv_makeFramesAndBuilders(N, weights);

      // Step 4: Run all the CV models
      cv_buildModels(N, cvModelBuilders);

      // Step 5: Score the CV models
      ModelMetrics.MetricBuilder mbs[] = cv_scoreCVModels(N, weights, cvModelBuilders);

      // Step 6: Build the main model
      buildMainModel();

      // Step 7: Combine cross-validation scores; compute main model x-val
      // scores; compute gains/lifts
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
          mb._result.remove(fs);
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
  // TODO: Implement better splitting algo (with Strata if response is
  // categorical), e.g. http://www.lexjansen.com/scsug/2009/Liang_Xie2.pdf
  public Vec cv_AssignFold(int N) {
    assert(N>=2);
    Vec fold = train().vec(_parms._fold_column);
    if( fold != null ) {
      if( !fold.isInt() ||
          (!(fold.min() == 0 && fold.max() == N-1) &&
           !(fold.min() == 1 && fold.max() == N  ) )) // Allow 0 to N-1, or 1 to N
        throw new H2OIllegalArgumentException("Fold column must be either categorical or contiguous integers from 0..N-1 or 1..N");
      return fold;
    }
    final long seed = _parms.getOrMakeRealSeed();
    Log.info("Creating " + N + " cross-validation splits with random number seed: " + seed);
    switch( _parms._fold_assignment ) {
    case AUTO:
    case Random:     return AstKFold.          kfoldColumn(train().anyVec().makeZero(),N,seed);
    case Modulo:     return AstKFold.    moduloKfoldColumn(train().anyVec().makeZero(),N     );
    case Stratified: return AstKFold.stratifiedKFoldColumn(response(),N,seed);
    default:         throw H2O.unimpl();
    }
  }

  // Step 2: Make 2*N binary weight vectors
  public Vec[] cv_makeWeights( final int N, Vec foldAssignment ) {
    String origWeightsName = _parms._weights_column;
    Vec origWeight  = origWeightsName != null ? train().vec(origWeightsName) : train().anyVec().makeCon(1.0);
    Frame folds_and_weights = new Frame(foldAssignment, origWeight);
    Vec[] weights = new MRTask() {
        @Override public void map(Chunk chks[], NewChunk nchks[]) {
          Chunk fold = chks[0], orig = chks[1];
          for( int row=0; row< orig._len; row++ ) {
            int foldIdx = (int)fold.at8(row) % N;
            double w = orig.atd(row);
            for( int f = 0; f < N; f++ ) {
              boolean holdout = foldIdx == f;
              nchks[2 * f].addNum(holdout ? 0 : w);
              nchks[2*f+1].addNum(holdout ? w : 0);
            }
          }
        }
      }.doAll(2*N,Vec.T_NUM,folds_and_weights).outputFrame().vecs();

    if (_parms._keep_cross_validation_fold_assignment)
      DKV.put(new Frame(Key.<Frame>make("cv_fold_assignment_" + _result.toString()), new String[]{"fold_assignment"}, new Vec[]{foldAssignment.makeCopy()}));
    if( _parms._fold_column == null && !_parms._keep_cross_validation_fold_assignment) foldAssignment.remove();
    if( origWeightsName == null ) origWeight.remove(); // Cleanup temp

    for( Vec weight : weights )
      if( weight.isConst() )
        throw new H2OIllegalArgumentException("Not enough data to create " + N + " random cross-validation splits. Either reduce nfolds, specify a larger dataset (or specify another random number seed, if applicable).");
    return weights;
  }

  // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
  public ModelBuilder<M, P, O>[] cv_makeFramesAndBuilders( int N, Vec[] weights ) {
    final long old_cs = _parms.checksum();
    final String origDest = _result.toString();

    final String weightName = "__internal_cv_weights__";
    if (train().find(weightName) != -1) throw new H2OIllegalArgumentException("Frame cannot contain a Vec called '" + weightName + "'.");

    Frame cv_fr = new Frame(train().names(),train().vecs());
    if( _parms._weights_column!=null ) cv_fr.remove( _parms._weights_column ); // The CV frames will have their own private weight column

    ModelBuilder<M, P, O>[] cvModelBuilders = new ModelBuilder[N];
    List<Frame> cvFramesForFailedModels = new ArrayList<>();
    for( int i=0; i<N; i++ ) {
      String identifier = origDest + "_cv_" + (i+1);
      // Training/Validation share the same data, but will have exclusive weights
      Frame cvTrain = new Frame(Key.<Frame>make(identifier+"_train"),cv_fr.names(),cv_fr.vecs());
      cvTrain.add(weightName, weights[2*i]);
      DKV.put(cvTrain);
      Frame cvValid = new Frame(Key.<Frame>make(identifier+"_valid"),cv_fr.names(),cv_fr.vecs());
      cvValid.add(weightName, weights[2*i+1]);
      DKV.put(cvValid);

      // Shallow clone - not everything is a private copy!!!
      ModelBuilder<M, P, O> cv_mb = (ModelBuilder)this.clone();
      cv_mb.setTrain(cvTrain);
      cv_mb._result = Key.make(identifier); // Each submodel gets its own key
      cv_mb._parms = (P) _parms.clone();
      // Fix up some parameters of the clone
      cv_mb._parms._is_cv_model = true;
      cv_mb._parms._weights_column = weightName;// All submodels have a weight column, which the main model does not
      cv_mb._parms.setTrain(cvTrain._key);       // All submodels have a weight column, which the main model does not
      cv_mb._parms._valid = cvValid._key;
      cv_mb._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
      cv_mb._parms._nfolds = 0; // Each submodel is not itself folded
      cv_mb.clearValidationErrors(); // each submodel gets its own validation messages and error_count()

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
    bulkBuildModels("cross-validation", _job, cvModelBuilders, nModelsInParallel(), 0 /*no job updates*/);
    cv_computeAndSetOptimalParameters(cvModelBuilders);
  }

  /**
   * Runs given model builders in bulk.
   *
   * @param modelType text description of group of models being built (for logging purposes)
   * @param job parent job (processing will be stopped if stop of a parent job was requested)
   * @param modelBuilders list of model builders to run in bulk
   * @param parallelization level of parallelization (how many models can be built at the same time)
   * @param updateInc update increment (0 = disable updates)
   */
  public static void bulkBuildModels(String modelType, Job job, ModelBuilder<?, ?, ?>[] modelBuilders,
                                     int parallelization, int updateInc) {
    final int N = modelBuilders.length;
    H2O.H2OCountedCompleter submodel_tasks[] = new H2O.H2OCountedCompleter[N];
    int nRunning=0;
    RuntimeException rt = null;
    for( int i=0; i<N; ++i ) {
      if (job.stop_requested() ) {
        Log.info("Skipping build of last "+(N-i)+" out of "+N+" "+modelType+" CV models");
        stopAll(submodel_tasks);
        throw new Job.JobCancelledException();
      }
      Log.info("Building " + modelType + " model " + (i + 1) + " / " + N + ".");
      modelBuilders[i]._start_time = System.currentTimeMillis();
      submodel_tasks[i] = H2O.submitTask(modelBuilders[i].trainModelImpl());
      if(++nRunning == parallelization) { //piece-wise advance in training the models
        while (nRunning > 0) try {
          submodel_tasks[i + 1 - nRunning--].join();
          if (updateInc > 0) job.update(updateInc); // One job finished
        } catch (RuntimeException t) {
          if (rt == null) rt = t;
        }
        if(rt != null) throw rt;
      }
    }
    for( int i=0; i<N; ++i ) //all sub-models must be completed before the main model can be built
      try {
        final H2O.H2OCountedCompleter task = submodel_tasks[i];
        assert task != null;
        task.join();
      } catch(RuntimeException t){
        if (rt == null) rt = t;
      }
    if(rt != null) throw rt;
  }

  private static void stopAll(H2O.H2OCountedCompleter[] tasks) {
    for (H2O.H2OCountedCompleter task : tasks) {
      if (task != null) {
        task.cancel(true);
      }
    }
  }

  // Step 5: Score the CV models
  public ModelMetrics.MetricBuilder[] cv_scoreCVModels(int N, Vec[] weights, ModelBuilder<M, P, O>[] cvModelBuilders) {
    if (_job.stop_requested()) {
      Log.info("Skipping scoring of CV models");
      throw new Job.JobCancelledException();
    }
    assert weights.length == 2*N;
    assert cvModelBuilders.length == N;

    Log.info("Scoring the "+N+" CV models");
    ModelMetrics.MetricBuilder[] mbs = new ModelMetrics.MetricBuilder[N];
    Futures fs = new Futures();
    for (int i=0; i<N; ++i) {
      if (_job.stop_requested()) {
        Log.info("Skipping scoring for last "+(N-i)+" out of "+N+" CV models");
        throw new Job.JobCancelledException();
      }
      Frame cvValid = cvModelBuilders[i].valid();
      Frame adaptFr = new Frame(cvValid);
      M cvModel = cvModelBuilders[i].dest().get();
      cvModel.adaptTestForTrain(adaptFr, true, !isSupervised());
      mbs[i] = cvModel.scoreMetrics(adaptFr);
      if (nclasses() == 2 /* need holdout predictions for gains/lift table */
              || _parms._keep_cross_validation_predictions
              || (_parms._distribution== DistributionFamily.huber /*need to compute quantiles on abs error of holdout predictions*/)) {
        String predName = cvModelBuilders[i].getPredictionKey();
        cvModel.predictScoreImpl(cvValid, adaptFr, predName, _job, true, CFuncRef.NOP);
        DKV.put(cvModel);
      }
      // free resources as early as possible
      if (adaptFr != null) {
        Frame.deleteTempFrameAndItsNonSharedVecs(adaptFr, cvValid);
        DKV.remove(adaptFr._key,fs);
      }
      DKV.remove(cvModelBuilders[i]._parms._train,fs);
      DKV.remove(cvModelBuilders[i]._parms._valid,fs);
      weights[2*i  ].remove(fs);
      weights[2*i+1].remove(fs);
    }
    fs.blockForPending();
    return mbs;
  }

  // Step 6: build the main model
  private void buildMainModel() {
    if (_job.stop_requested()) {
      Log.info("Skipping main model");
      throw new Job.JobCancelledException();
    }
    assert _job.isRunning();
    Log.info("Building main model.");
    _start_time = System.currentTimeMillis();
    H2O.H2OCountedCompleter mm = H2O.submitTask(trainModelImpl());
    mm.join();  // wait for completion
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
      if (i > 0) mbs[0].reduce(mbs[i]);
      cvModKeys[i] = cvModelBuilders[i]._result;
      predKeys[i] = Key.make(cvModelBuilders[i].getPredictionKey());
    }

    Frame holdoutPreds = null;
    if (_parms._keep_cross_validation_predictions || (nclasses()==2 /*GainsLift needs this*/ || _parms._distribution == DistributionFamily.huber)) {
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
        if (fr != null) Scope.untrack(fr.keysList());
      }
    } else {
      int count = Model.deleteAll(predKeys);
      Log.info(count+" CV predictions were removed");
    }

    mainModel._output._cross_validation_metrics = mbs[0].makeModelMetrics(mainModel, _parms.train(), null, holdoutPreds);
    if (holdoutPreds != null) {
      if (_parms._keep_cross_validation_predictions) Scope.untrack(holdoutPreds.keysList());
      else holdoutPreds.remove();
    }
    mainModel._output._cross_validation_metrics._description = N + "-fold cross-validation on training data (Metrics computed for combined holdout predictions)";
    Log.info(mainModel._output._cross_validation_metrics.toString());
    mainModel._output._cross_validation_metrics_summary = makeCrossValidationSummaryTable(cvModKeys);

    if (!_parms._keep_cross_validation_models) {
      int count = Model.deleteAll(cvModKeys);
      Log.info(count+" CV models were removed");
    }

    // Now, the main model is complete (has cv metrics)
    DKV.put(mainModel);
  }

  private String getPredictionKey() {
    return "prediction_"+_result.toString();
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

  protected transient Vec _response; // Handy response column
  protected transient Vec _vresponse; // Handy response column
  protected transient Vec _offset; // Handy offset column
  protected transient Vec _weights; // observation weight column
  protected transient Vec _fold; // fold id column
  protected transient String[] _origNames; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame
  protected transient String[][] _origDomains; // only set if ModelBuilder.encodeFrameCategoricals() changes the training frame

  public boolean hasOffsetCol(){ return _parms._offset_column != null;} // don't look at transient Vec
  public boolean hasWeightCol(){return _parms._weights_column != null;} // don't look at transient Vec
  public boolean hasFoldCol(){return _parms._fold_column != null;} // don't look at transient Vec
  public int numSpecialCols() { return (hasOffsetCol() ? 1 : 0) + (hasWeightCol() ? 1 : 0) + (hasFoldCol() ? 1 : 0); }
  public String[] specialColNames() {
    String[] n = new String[numSpecialCols()];
    int i=0;
    if (hasOffsetCol()) n[i++]=_parms._offset_column;
    if (hasWeightCol()) n[i++]=_parms._weights_column;
    if (hasFoldCol())   n[i++]=_parms._fold_column;
    return n;
  }
  // no hasResponse, call isSupervised instead (response is mandatory if isSupervised is true)

  public boolean havePojo() { return false; }
  public boolean haveMojo() { return false; }

  protected int _nclass; // Number of classes; 1 for regression; 2+ for classification

  public int nclasses(){return _nclass;}

  public final boolean isClassifier() { return nclasses() > 1; }

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
        @Override protected boolean filter(Vec v) {
          boolean isBad = v.isBad();
          boolean skipConst = ignoreConstColumns() && v.isConst();
          boolean skipString = ignoreStringColumns() && v.isString();
          boolean skipUuid = ignoreUuidColumns() && v.isUUID();
          boolean skip = isBad || skipConst || skipString || skipUuid;
          return skip;
        }
      }.doIt(_train,"Dropping bad and constant columns: ",expensive);
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

 /** Get a string representation of only the ERROR ValidationMessages (e.g., to use in an exception throw). */
  public String validationErrors() {
    StringBuilder sb = new StringBuilder();
    for( ValidationMessage vm : _messages )
      if( vm._log_level == Log.ERRR )
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
    assert _parms != null;      // Parms must already be set in

    if( _parms._train == null ) {
      if (expensive)
        error("_train", "Missing training frame");
      return;
    }
    Frame tr = _train != null?_train:_parms.train();
    if( tr == null ) { error("_train", "Missing training frame: "+_parms._train); return; }
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
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO) {
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
      if (_parms._fold_assignment != Model.Parameters.FoldAssignmentScheme.AUTO) {
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
      error("_tweedie_power", "Tweedie power must be between 1 and 2 (exclusive).");
    }

    // Drop explicitly dropped columns
    if( _parms._ignored_columns != null ) {
      _train.remove(_parms._ignored_columns);
      if( expensive ) Log.info("Dropping ignored columns: "+Arrays.toString(_parms._ignored_columns));
    }

    if(_parms._checkpoint != null){
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
        _nclass = _response.isCategorical() ? _response.cardinality() : 1;
        if (_parms._distribution == DistributionFamily.quasibinomial) {
          _nclass = 2;
        }
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
          if (isClassifier() && isSupervised() && _parms._distribution != DistributionFamily.quasibinomial) {
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
      _valid = adaptFrameToTrain(va, "Validation Frame", "_validation_frame", expensive);
      _vresponse = _valid.vec(_parms._response_column);
    } else {
      _valid = null;
      _vresponse = null;
    }

    if (expensive) {
      Frame newtrain = encodeFrameCategoricals(_train, ! _parms._is_cv_model);
      if (newtrain != _train) {
        _origNames = _train.names();
        _origDomains = _train.domains();
        setTrain(newtrain);
        separateFeatureVecs(); //fix up the pointers to the special vecs
      }
      if (_valid != null) {
        _valid = encodeFrameCategoricals(_valid, ! _parms._is_cv_model /* for CV, need to score one more time in outer loop */);
        _vresponse = _valid.vec(_parms._response_column);
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
//          for (int i=0;i<len;++i)
//            Log.info(v.domain()[i] + " -> " + meanWeightedResponse[i]);

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
//          for (int i=0;i<len;++i)
//            Log.info(vNew.domain()[i] + " -> " + meanWeightedResponse[idx[i]]);
          vecs[j] = vNew;
          restructured = true;
        }
      }
      if (restructured)
        _train.restructure(_train.names(), vecs);
    }
    assert (!expensive || _valid==null || Arrays.equals(_train._names, _valid._names) || _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.Binary);
    if (_valid!=null && !Arrays.equals(_train._names, _valid._names) && _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.Binary) {
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
    } else {
      if (isClassifier()) {
        if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.deviance && !getClass().getSimpleName().contains("GLM")) {
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
    if (_parms._max_runtime_secs < 0) {
      error("_max_runtime_secs", "Max runtime (in seconds) must be greater than 0 (or 0 for unlimited).");
    }
    if (!StringUtils.isNullOrEmpty(_parms._export_checkpoints_dir)) {
      if(!H2O.getPM().isWritableDirectory(_parms._export_checkpoints_dir)) {
        error("_export_checkpoints_dir", "Checpoints directory path must point to a writable path.");
      }
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
  protected Frame init_adaptFrameToTrain(Frame fr, String frDesc, String field, boolean expensive) {
    Frame adapted = adaptFrameToTrain(fr, frDesc, field, expensive);
    if (expensive)
      adapted = encodeFrameCategoricals(adapted, true);
    return adapted;
  }

  private Frame adaptFrameToTrain(Frame fr, String frDesc, String field, boolean expensive) {
    if (fr.numRows()==0) error(field, frDesc + " must have > 0 rows.");
    Frame adapted = new Frame(null /* not putting this into KV */, fr._names.clone(), fr.vecs().clone());
    try {
      String[] msgs = Model.adaptTestForTrain(adapted, null, null, _train._names, _train.domains(), _parms, expensive, true, null, getToEigenVec(), _toDelete, false);
      Vec response = adapted.vec(_parms._response_column);
      if (response == null && _parms._response_column != null)
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

  private Frame encodeFrameCategoricals(Frame fr, boolean scopeTrack) {
    String[] skipCols = new String[]{_parms._weights_column, _parms._offset_column, _parms._fold_column, _parms._response_column};
    Frame encoded = FrameUtils.categoricalEncoder(fr, skipCols, _parms._categorical_encoding, getToEigenVec(), _parms._max_categorical_levels);
    if (encoded != fr) {
      assert encoded._key != null;
      if (scopeTrack)
        Scope.track(encoded);
      else
        _toDelete.put(encoded._key, Arrays.toString(Thread.currentThread().getStackTrace()));
    }
    return encoded;
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
    double rebalanceRatio = rebalanceRatio();
    int nonEmptyChunks = original_fr.anyVec().nonEmptyChunks();
    if (nonEmptyChunks >= chunks * rebalanceRatio) {
      if (chunks>1)
        Log.info(name.substring(name.length()-5)+ " dataset already contains " + nonEmptyChunks + " (non-empty) " +
              " chunks. No need to rebalance. [desiredChunks=" + chunks, ", rebalanceRatio=" + rebalanceRatio + "]");
      return original_fr;
    }
    Log.info("Rebalancing " + name.substring(name.length()-5)  + " dataset into " + chunks + " chunks.");
    Key newKey = Key.makeUserHidden(name + ".chunks" + chunks);
    RebalanceDataSet rb = new RebalanceDataSet(original_fr, newKey, chunks);
    H2O.submitTask(rb).join();
    Frame rebalanced_fr = DKV.get(newKey).get();
    Scope.track(rebalanced_fr);
    return rebalanced_fr;
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

  public void checkDistributions() {
    if (_parms._distribution == DistributionFamily.quasibinomial) {
      if (_response.min() != 0)
        error("_response", "For quasibinomial distribution, response must have a low value of 0 (negative class), but instead has min value of " + _response.min() + ".");
    } else if (_parms._distribution == DistributionFamily.poisson) {
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

    abstract protected boolean filter(Vec v);

    public void doIt( Frame f, String msg, boolean expensive ) {
      List<Integer> rmcolsList = new ArrayList<>();
      for( int i = 0; i < f.vecs().length - _specialVecs; i++ )
        if( filter(f.vec(i)) ) rmcolsList.add(i);
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
  private static Frame combineHoldoutPredictions(Key<Frame>[] predKeys, Key key) {
    int N = predKeys.length;
    Frame template = predKeys[0].get();
    Vec[] vecs = new Vec[N*template.numCols()];
    int idx=0;
    for (int i=0;i<N;++i)
      for (int j=0;j<predKeys[i].get().numCols();++j)
        vecs[idx++]=predKeys[i].get().vec(j);
    return new HoldoutPredictionCombiner(N,template.numCols()).doAll(template.types(),new Frame(vecs)).outputFrame(key, template.names(),template.domains());
  }

  // helper to combine multiple holdout prediction Vecs (each only has 1/N-th filled with non-zeros) into 1 Vec
  private static class HoldoutPredictionCombiner extends MRTask<HoldoutPredictionCombiner> {
    int _folds, _cols;
    public HoldoutPredictionCombiner(int folds, int cols) { _folds=folds; _cols=cols; }
    @Override public void map(Chunk[] cs, NewChunk[] nc) {
      for (int c=0;c<_cols;++c) {
        double [] vals = new double[cs[0].len()];
        for (int f=0;f<_folds;++f)
          for (int row = 0; row < cs[0].len(); ++row)
            vals[row] += cs[f * _cols + c].atd(row);
        nc[c].setDoubles(vals);
      }
    }
  }

  private TwoDimTable makeCrossValidationSummaryTable(Key[] cvmodels) {
    if (cvmodels == null || cvmodels.length == 0) return null;
    int N = cvmodels.length;
    int extra_length=2; //mean/sigma/cv1/cv2/.../cvN
    String[] colTypes = new String[N+extra_length];
    Arrays.fill(colTypes, "string");
    String[] colFormats = new String[N+extra_length];
    Arrays.fill(colFormats, "%s");
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

    for (i=0;i<N;++i)
      stats.add(vals[i],1);
    for (i=0;i<numMetrics;++i) {
      table.set(i, 0, (float)stats.mean()[i]);
      table.set(i, 1, (float)stats.sigma()[i]);
    }

    Log.info(table);
    return table;
  }

}
