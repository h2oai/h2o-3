package hex;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.rapids.ASTKFold;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MRUtils;
import water.util.VecUtils;

import java.util.*;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Iced {

  public Job _job;     // Job controlling this build
  /** Block till completion, and return the built model from the DKV.  Note the
   *  funny assert: the Job does NOT have to be controlling this model build,
   *  but might, e.g. be controlling a Grid search for which this is just one
   *  of many results.  Calling 'get' means that we are blocking on the Job
   *  which is controlling ONLY this ModelBuilder, and when the Job completes
   *  we can return built Model. */
  public final M get() { assert _job._result == _result; return (M)_job.get(); }
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
  public static Key<? extends Model> defaultKey(String algoName) {
    return Key.make(H2O.calcNextUniqueModelId(algoName));
  }

  /** Default easy constructor: Unique new job and unique new result key */
  protected ModelBuilder(P parms) {
    this(parms, (Key<M>)defaultKey(parms.algoName()));
  }

  /** Unique new job and named result key */
  protected ModelBuilder(P parms, Key<M> key) {
    _job = new Job<>(_result = key, parms.javaName(), parms.algoName());
    _parms = parms;
  }

  /** Shared pre-existing Job and unique new result key */
  protected ModelBuilder(P parms, Job job) {
    _job = job;
    _result = (Key<M>)defaultKey(parms.algoName());
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
    assert startup_once;
    _job = null;
    _result = null;
    _parms = parms;
    init(false); // Default cheap init
    String base = getClass().getSimpleName().toLowerCase();
    if( ArrayUtils.find(ALGOBASES,base) != -1 )
      throw H2O.fail("Only called once at startup per ModelBuilder, and "+base+" has already been called");
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


  /** Factory method to create a ModelBuilder instance for given the algo name.
   *  Shallow clone of both the default ModelBuilder instance and a Parameter. */
  public static <B extends ModelBuilder> B make(String algo, Job job, Key<Model> result) {
    int idx = ArrayUtils.find(ALGOBASES,algo.toLowerCase());
    assert idx != -1 : "Unregistered algorithm "+algo;
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
  }
  
  /** Method to launch training of a Model, based on its parameters. */
  final public Job<M> trainModel() {
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    _start_time = System.currentTimeMillis();
    if( !nFoldCV() )
      return _job.start(trainModelImpl(), progressUnits());

    // cross-validation needs to be forked off to allow continuous (non-blocking) progress bar
    return _job.start(new H2O.H2OCountedCompleter() {
        @Override
        public void compute2() {
          computeCrossValidation();
          tryComplete();
        }
      }, (1/*for all pre-fold work*/+nFoldWork()+1/*for all the post-fold work*/) * progressUnits());
  }

  /** Train a model as part of a larger Job; the Job already exists and has started. */
  final public M trainModelNested() {
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
  abstract protected long progressUnits();

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
  private int nFoldWork() {
    if( _parms._fold_column == null ) return _parms._nfolds;
    Vec f = train().vec(_parms._fold_column);
    Vec fc = VecUtils.toCategoricalVec(f);
    int N = fc.domain().length;
    fc.remove();
    return N;
  }

  /**
   * Default naive (serial) implementation of N-fold cross-validation
   * @return Cross-validation Job
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  public void computeCrossValidation() {
    assert _job.isRunning();    // main Job is still running
    final Integer N = nFoldWork();
    init(false);
    try {
      Scope.enter();

      // Step 1: Assign each row to a fold
      final Vec foldAssignment = cv_AssignFold(N);

      // Step 2: Make 2*N binary weight vectors
      final Vec[] weights = cv_makeWeights(N,foldAssignment);
      _job.update(1);           // Did the major pre-fold work

      // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
      ModelBuilder<M, P, O> cvModelBuilders[] = cv_makeFramesAndBuilders(N,weights);

      // Step 4: Run all the CV models and launch the main model
      H2O.H2OCountedCompleter mainMB = cv_buildModels(N, cvModelBuilders);

      // Step 5: Score the CV models
      ModelMetrics.MetricBuilder mbs[] = cv_scoreCVModels(N, weights, cvModelBuilders);
      
      // wait for completion of the main model
      if (mainMB!=null) mainMB.join();

      // Step 6: Combine cross-validation scores; compute main model x-val
      // scores; compute gains/lifts
      cv_mainModelScores(N, mbs, cvModelBuilders);

    } finally {
      Scope.exit();
    }
  }

  // Step 1: Assign each row to a fold
  // TODO: Implement better splitting algo (with Strata if response is
  // categorical), e.g. http://www.lexjansen.com/scsug/2009/Liang_Xie2.pdf
  public Vec cv_AssignFold(int N) {
    Vec fold = train().vec(_parms._fold_column);
    if( fold != null ) {
      if( !fold.isInt() ||
          (!(fold.min() == 0 && fold.max() == N-1) &&
           !(fold.min() == 1 && fold.max() == N  ) )) // Allow 0 to N-1, or 1 to N
        throw new H2OIllegalArgumentException("Fold column must be either categorical or contiguous integers from 0..N-1 or 1..N");
      return fold;
    }
    final long seed = _parms.nFoldSeed();
    Log.info("Creating " + N + " cross-validation splits with random number seed: " + seed);
    switch( _parms._fold_assignment ) {
    case AUTO:
    case Random:     return ASTKFold.          kfoldColumn(train().anyVec().makeZero(),N,seed);
    case Modulo:     return ASTKFold.    moduloKfoldColumn(train().anyVec().makeZero(),N     );
    case Stratified: return ASTKFold.stratifiedKFoldColumn(response(),N,seed);
    default:         throw H2O.unimpl();
    }
  }

  // Step 2: Make 2*N binary weight vectors
  public Vec[] cv_makeWeights( final int N, Vec foldAssignment ) {
    String origWeightsName = _parms._weights_column;
    Vec origWeight  = origWeightsName != null ? train().vec(origWeightsName) : train().anyVec().makeCon(1.0);
    Frame folds_and_weights = new Frame(new Vec[]{foldAssignment, origWeight});
    Vec[] weights = new MRTask() {
        @Override public void map(Chunk chks[], NewChunk nchks[]) {
          Chunk fold = chks[0], orig = chks[1];
          for( int row=0; row< orig._len; row++ ) {
            int foldAssignment = (int)fold.at8(row) % N;
            double w = orig.atd(row);
            for( int f = 0; f < N; f++ ) {
              boolean holdout = foldAssignment == f;
              nchks[2*f+0].addNum(holdout ? 0 : w);
              nchks[2*f+1].addNum(holdout ? w : 0);
            }
          }
        }
      }.doAll(2*N,Vec.T_NUM,folds_and_weights).outputFrame().vecs();
    if( _parms._fold_column == null ) foldAssignment.remove();
    if( origWeightsName == null ) origWeight.remove(); // Cleanup temp

    for( Vec weight : weights )
      if( weight.isConst() )
        throw new H2OIllegalArgumentException("Not enough data to create " + N + " random cross-validation splits. Either reduce nfolds, specify a larger dataset (or specify another random number seed, if applicable).");
    return weights;
  }

  // Step 3: Build N train & validation frames; build N ModelBuilders; error check them all
  public ModelBuilder<M, P, O>[] cv_makeFramesAndBuilders( int N, Vec[] weights ) {
    final long old_cs = _parms.checksum();
    final String origDest = _job._result.toString();

    final String weightName = "__internal_cv_weights__";
    if (train().find(weightName) != -1) throw new H2OIllegalArgumentException("Frame cannot contain a Vec called '" + weightName + "'.");

    Frame cv_fr = new Frame(train().names(),train().vecs());
    if( _parms._weights_column!=null ) cv_fr.remove( _parms._weights_column ); // The CV frames will have their own private weight column

    ModelBuilder<M, P, O>[] cvModelBuilders = new ModelBuilder[N];
    for( int i=0; i<N; i++ ) {
      String identifier = origDest + "_cv_" + (i+1);
      // Training/Validation share the same data, but will have exclusive weights
      Frame cvTrain = new Frame(Key.make(identifier+"_train"),cv_fr.names(),cv_fr.vecs());
      cvTrain.add(weightName, weights[2*i]);
      DKV.put(cvTrain);
      Frame cvValid = new Frame(Key.make(identifier+"_valid"),cv_fr.names(),cv_fr.vecs());
      cvValid.add(weightName, weights[2*i+1]);
      DKV.put(cvValid);

      // Shallow clone - not everything is a private copy!!!
      ModelBuilder<M, P, O> cv_mb = (ModelBuilder)this.clone();
      cv_mb._result = Key.make(identifier); // Each submodel gets its own key
      cv_mb._parms = (P) _parms.clone();
      // Fix up some parameters of the clone
      cv_mb._parms._weights_column = weightName;// All submodels have a weight column, which the main model does not
      cv_mb._parms._train = cvTrain._key;       // All submodels have a weight column, which the main model does not
      cv_mb._parms._valid = cvValid._key;
      cv_mb._parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
      cv_mb._parms._nfolds = 0; // Each submodel is not itself folded
      cv_mb.init(false);        // Arg check submodels
      // Error-check all the cross-validation Builders before launching any
      if( cv_mb.error_count() > 0 ) // Gather all submodel error messages
        for( ValidationMessage vm : cv_mb._messages )
          message(vm._log_level, vm._field_name, vm._message);
      cvModelBuilders[i] = cv_mb;
    }

    if( error_count() > 0 )     // Error in any submodel
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(this);
    // check that this Job's original _params haven't changed
    assert old_cs == _parms.checksum();
    return cvModelBuilders;
  }

  // Step 4: Run all the CV models and launch the main model
  public H2O.H2OCountedCompleter cv_buildModels(int N, ModelBuilder<M, P, O>[] cvModelBuilders ) {
    H2O.H2OCountedCompleter submodel_tasks[] = new H2O.H2OCountedCompleter[N];
    int nRunning=0;
    for( int i=0; i<N; ++i ) {
      if( _job.stop_requested() ) break; // Stop launching but still must block for all async jobs
      Log.info("Building cross-validation model " + (i + 1) + " / " + N + ".");
      cvModelBuilders[i]._start_time = System.currentTimeMillis();
      submodel_tasks[i] = H2O.submitTask(cvModelBuilders[i].trainModelImpl());
      if(++nRunning == nModelsInParallel()) //piece-wise advance in training the CV models
        while (nRunning>0) submodel_tasks[i+1-nRunning--].join();
    }
    for( int i=0; i<N; ++i ) //all sub-models must be completed before the main model can be built
      submodel_tasks[i].join();
    // Now do the main model
    if( _job.stop_requested() ) return null;
    assert _job.isRunning();
    Log.info("Building main model.");
    _start_time = System.currentTimeMillis();
    modifyParmsForCrossValidationMainModel(cvModelBuilders); //tell the main model that it shouldn't stop early either
    H2O.H2OCountedCompleter mainMB = H2O.submitTask(trainModelImpl()); //non-blocking: start the main model
    return mainMB;
  }

  // Step 5: Score the CV models
  public ModelMetrics.MetricBuilder[] cv_scoreCVModels(int N, Vec[] weights, ModelBuilder<M, P, O>[] cvModelBuilders) {
    if( _job.stop_requested() ) return null;
    ModelMetrics.MetricBuilder[] mbs = new ModelMetrics.MetricBuilder[N];
    Futures fs = new Futures();
    for (int i=0; i<N; ++i) {
      if( _job.stop_requested() ) return null; //don't waste time scoring if the CV run is stopped
      Frame cvValid = cvModelBuilders[i].valid();
      Frame adaptFr = new Frame(cvValid);
      M cvModel = cvModelBuilders[i].dest().get();
      cvModel.adaptTestForTrain(adaptFr, true, !isSupervised());
      mbs[i] = cvModel.scoreMetrics(adaptFr);
      if (nclasses() == 2 /* need holdout predictions for gains/lift table */ || _parms._keep_cross_validation_predictions) {
        String predName = "prediction_" + cvModelBuilders[i]._result.toString();
        cvModel.predictScoreImpl(cvValid, adaptFr, predName);
      }
      // free resources as early as possible
      if (adaptFr != null) {
        Model.cleanup_adapt(adaptFr, cvValid);
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

  // Step 6: Combine cross-validation scores; compute main model x-val scores; compute gains/lifts
  public void cv_mainModelScores(int N, ModelMetrics.MetricBuilder mbs[], ModelBuilder<M, P, O> cvModelBuilders[]) {
    if( _job.stop_requested() ) return;
    assert _job.isRunning();

    M mainModel = _result.get();

    // Compute and put the cross-validation metrics into the main model
    Log.info("Computing " + N + "-fold cross-validation metrics.");
    mainModel._output._cross_validation_models = new Key[N];
    Key<Frame>[] predKeys = new Key[N];
    mainModel._output._cross_validation_predictions = _parms._keep_cross_validation_predictions ? predKeys : null;
    
    for (int i = 0; i < N; ++i) {
      if (i > 0) mbs[0].reduce(mbs[i]);
      Key<M> cvModelKey = cvModelBuilders[i]._result;
      mainModel._output._cross_validation_models[i] = cvModelKey;
      predKeys[i] = Key.make("prediction_" + cvModelKey.toString());
    }
    Frame preds = null;
    //stitch together holdout predictions into one Vec, to compute the Gains/Lift table
    if (nclasses() == 2) {
      Vec[] p1s = new Vec[N];
      for (int i=0;i<N;++i) {
        p1s[i] = ((Frame)DKV.getGet(predKeys[i])).lastVec();
      }
      Frame p1combined = new HoldoutPredictionCombiner().doAll(1,Vec.T_NUM,new Frame(p1s)).outputFrame(new String[]{"p1"},null);
      Vec p1 = p1combined.anyVec();
      preds = new Frame(new Vec[]{p1, p1, p1}); //pretend to have labels,p0,p1, but will only need p1 anyway
    }
    // Keep or toss predictions
    for (Key<Frame> k : predKeys) {
      Frame fr = DKV.getGet(k);
      if( fr != null ) {
        if (_parms._keep_cross_validation_predictions) Scope.untrack(fr.keys());
        else fr.remove();
      }
    }
    mainModel._output._cross_validation_metrics = mbs[0].makeModelMetrics(mainModel, _parms.train(), null, preds);
    if (preds!=null) preds.remove();
    mainModel._output._cross_validation_metrics._description = N + "-fold cross-validation on training data (Metrics computed for combined holdout predictions)";
    Log.info(mainModel._output._cross_validation_metrics.toString());

    // Now, the main model is complete (has cv metrics)
    DKV.put(mainModel);
  }

  // helper to combine multiple holdout prediction Vecs (each only has 1/N-th filled with non-zeros) into 1 Vec
  private static class HoldoutPredictionCombiner extends MRTask<HoldoutPredictionCombiner> {
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      double [] vals = new double[cs[0].len()];
      for (int i=0;i<cs.length;++i)
        for (int row = 0; row < cs[0].len(); ++row)
          vals[row] += cs[i].atd(row);
      nc[0].setDoubles(vals);
    }
  }

  /** Override for model-specific checks / modifications to _parms for the main model during N-fold cross-validation.
   *  For example, the model might need to be told to not do early stopping.
   */
  public void modifyParmsForCrossValidationMainModel(ModelBuilder<M, P, O>[] cvModelBuilders) { }

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
  public enum BuilderVisibility { Experimental, Beta, Stable }
  public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }

  /** Clear whatever was done by init() so it can be run again. */
  public void clearInitState() {
    clearValidationErrors();
  }
  protected boolean logMe() { return true; }

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

  protected  boolean ignoreStringColumns(){return true;}
  protected  boolean ignoreConstColumns(){return _parms._ignore_const_cols;}

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

    // Drop explicitly dropped columns
    if( _parms._ignored_columns != null ) {
      _train.remove(_parms._ignored_columns);
      if( expensive ) Log.info("Dropping ignored columns: "+Arrays.toString(_parms._ignored_columns));
    }
    // Rebalance train and valid datasets
    if (expensive && error_count() == 0) {
      _train = rebalance(_train, false, _result + ".temporary.train");
      _valid = rebalance(_valid, false, _result + ".temporary.valid");
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
        if (expensive) checkDistributions();
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
    if (_parms._max_runtime_secs < 0) {
      error("_max_runtime_secs", "Max runtime (in seconds) must be greater than 0 (or 0 for unlimited).");
    }
  }
  
  /**
   * Rebalance a frame for load balancing
   * @param original_fr Input frame
   * @param local Whether to only create enough chunks to max out all cores on one node only
   * @param name Name of rebalanced frame
   * @return Frame that has potentially more chunks
   */
  
  protected Frame rebalance(final Frame original_fr, boolean local, final String name) {
    if (original_fr == null) return null;
    int chunks = desiredChunks(original_fr, local);
    if (original_fr.anyVec().nChunks() >= chunks) {
      if (chunks>1)
        Log.info(name.substring(name.length()-5)+ " dataset already contains " + original_fr.anyVec().nChunks() +
              " chunks. No need to rebalance.");
      return original_fr;
    }
    Log.info("Rebalancing " + name.substring(name.length()-5)  + " dataset into " + chunks + " chunks.");
    Key newKey = Key.make(name + ".chunks" + chunks);
    RebalanceDataSet rb = new RebalanceDataSet(original_fr, newKey, chunks);
    H2O.submitTask(rb).join();
    Frame rebalanced_fr = DKV.get(newKey).get();
    Scope.track(rebalanced_fr);
    return rebalanced_fr;
  }
  
  /**
   * Find desired number of chunks. If fewer, dataset will be rebalanced.
   * @return Lower bound on number of chunks after rebalancing.
   */
  protected int desiredChunks(final Frame original_fr, boolean local) {
    return Math.min((int) Math.ceil(original_fr.numRows() / 1e3), H2O.NUMCPUS);
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
    } else if (_parms._distribution == Distribution.Family.quantile) {
      if (_parms._quantile_alpha > 1 || _parms._quantile_alpha < 0)
        error("_quantile_alpha", "Quantile (alpha) must be between 0 and 1.");
    }
  }

  protected transient HashSet<String> _removedCols = new HashSet<String>();
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
          _removedCols.add(f._names[i]);
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
