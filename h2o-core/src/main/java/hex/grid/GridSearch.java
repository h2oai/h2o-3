package hex.grid;

import hex.*;
import hex.faulttolerance.Recovery;
import hex.grid.HyperSpaceWalker.BaseWalker;
import jsr166y.CountedCompleter;
import water.*;
import water.exceptions.H2OConcurrentModificationException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PojoUtils;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static hex.grid.HyperSpaceWalker.BaseWalker.SUBSPACES;

/**
 * Grid search job.
 *
 * This job represents a generic interface to launch "any" hyper space
 * search. It triggers sub-jobs for each point in hyper space.  It produces
 * <code>Grid</code> object which contains a list of build models. A triggered
 * model builder job can fail!
 *
 * Grid search is parametrized by hyper space walk strategy ({@link
 * HyperSpaceWalker} which defines how the space of hyper parameters
 * is traversed.
 *
 * The job is started by the <code>startGridSearch</code> method which create a new grid search, put
 * representation of Grid into distributed KV store, and for each parameter in hyper space of
 * possible parameters, it launches a separated model building job. The launch of jobs is sequential
 * and blocking. So after finish the last model, whole grid search job is done as well.
 *
 * By default, the grid search invokes cartesian grid search, but it can be
 * modified by passing explicit hyper space walk strategy via the
 * {@link #startGridSearch(Key, HyperSpaceWalker, Recovery, int)} method.
 *
 * If any of forked jobs fails then the failure is ignored, and grid search
 * normally continue in traversing the hyper space.
 *
 * Typical usage from Java is:
 * <pre>{@code
 * // Create initial parameters and fill them by references to data
 * GBMModel.GBMParameters params = new GBMModel.GBMParameters();
 * params._train = fr._key;
 * params._response_column = "cylinders";
 *
 * // Define hyper-space to search
 * HashMap<String,Object[]> hyperParms = new HashMap<>();
 * hyperParms.put("_ntrees", new Integer[]{1, 2});
 * hyperParms.put("_distribution",new DistributionFamily[] {DistributionFamily.multinomial});
 * hyperParms.put("_max_depth",new Integer[]{1,2,5});
 * hyperParms.put("_learn_rate",new Float[]{0.01f,0.1f,0.3f});
 *
 * // Launch grid search job creating GBM models
 * GridSearch gridSearchJob = GridSearch.startGridSearch(params, hyperParms, GBM_MODEL_FACTORY);
 *
 * // Block till the end of the job and get result
 * Grid grid = gridSearchJob.get()
 *
 * // Get built models
 * Model[] models = grid.getModels()
 * }</pre>
 *
 * @see HyperSpaceWalker
 * @see #startGridSearch(Key, HyperSpaceWalker, Recovery, int)
 */
public final class GridSearch<MP extends Model.Parameters> {
  public final Key<Grid> _result;
  public final Job<Grid> _job;
  public final Recovery<Grid> _recovery;
  public final int _parallelism;

  /** Walks hyper space and for each point produces model parameters. It is
   *  used only locally to fire new model builders.  */
  private final transient HyperSpaceWalker<MP, ?> _hyperSpaceWalker;

  private GridSearch(
      final Key<Grid> gkey, 
      final HyperSpaceWalker<MP, ?> hyperSpaceWalker, 
      final Recovery<Grid> recovery, 
      final int parallelism
  ) {
    assert hyperSpaceWalker != null : "Grid search needs to know how to walk around hyper space!";
    _hyperSpaceWalker = hyperSpaceWalker;
    _result = gkey;
    String algoName = hyperSpaceWalker.getParams().algoName();
    _job = new Job<>(gkey, Grid.class.getName(), algoName + " Grid Search");
    _recovery = recovery;
    _parallelism = parallelism;
    // Note: do not validate parameters of created model builders here!
    // Leave it to launch time, and just mark the corresponding model builder job as failed.
  }

  Job<Grid> start() {
    final long gridSize = _hyperSpaceWalker.getMaxHyperSpaceSize();
    Log.info("Starting gridsearch: estimated size of search space = " + gridSize);
    // Create grid object and lock it
    // Creation is done here, since we would like make sure that after leaving
    // this function the grid object is in DKV and accessible.
    final Grid<MP> grid = getOrCreateGrid();  

    HyperSpaceWalker.HyperSpaceIterator<MP> it = _hyperSpaceWalker.iterator();
    long gridWork=0;
    // if total grid space is known, walk it all and count up models to be built (not subject to time-based or converge-based early stopping)
    // skip it if no model limit it specified as the entire hyperspace can be extremely large.
    if (gridSize > 0 && maxModels() > 0) {
      while (it.hasNext()) {
      Model model = null;
        try {
          Model.Parameters parms = it.nextModelParameters();
          gridWork += (parms._nfolds > 0 ? (parms._nfolds+1/*main model*/) : 1) *parms.progressUnits();
        } catch(Throwable ex) {
          //swallow invalid combinations
        }
      }
    } else {
      //TODO: Future totally unbounded search: need a time-based progress bar
      gridWork = Long.MAX_VALUE;
    }

    // Install this as job functions
    return _job.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        beforeGridStart(grid);
        if (_parallelism == 1) {
          gridSearch(grid);
        } else if (_parallelism > 1) {
          parallelGridSearch(grid);
        } else {
          throw new IllegalArgumentException(String.format("Grid search parallelism level must be >= 1. Give value is '%d'.",
                  _parallelism));
        }
        afterGridCompleted(grid);
        tryComplete();
      }

      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        Log.warn("GridSearch job " + _job._description + " completed with exception: " + ex);
        return true;
      }
    }, gridWork, maxRuntimeSecs());
  }

  private Grid getOrCreateGrid() {
    Grid grid = loadFromDKV();
    if (grid == null) {
      grid = createNewGrid();
    }
    return grid;
  }

  private Grid loadFromDKV() {
    Keyed keyed = DKV.getGet(_result);
    if (keyed == null) return null;
    if (!(keyed instanceof Grid))
      throw new H2OIllegalArgumentException("Name conflict: tried to create a Grid using the ID of a non-Grid object that's already in H2O: " + _job._result + "; it is a: " + keyed.getClass());
    Grid grid = (Grid) keyed;
    grid.clearNonRelatedFailures();
    Frame specTrainFrame = _hyperSpaceWalker.getParams().train();
    Frame oldTrainFrame = grid.getTrainingFrame();
    if (oldTrainFrame != null && !specTrainFrame._key.equals(oldTrainFrame._key) ||
        oldTrainFrame != null && specTrainFrame.checksum() != oldTrainFrame.checksum())
      throw new H2OIllegalArgumentException("training_frame", "grid", "Cannot append new models to a grid with different training input");
    grid.write_lock(_job);
    return grid;
  }
  
  private Grid createNewGrid() {
    Grid grid = new Grid<>(_result, _hyperSpaceWalker, _parallelism);
    grid.delete_and_lock(_job);
    return grid;
  }

  /**
   * Returns expected number of models in resulting Grid object.
   *
   * The number can differ from final number of models due to visiting duplicate points in hyper
   * space.
   *
   * @return expected number of models produced by this grid search
   */
  public long getModelCount() {
    return _hyperSpaceWalker.getMaxHyperSpaceSize();
  }

  private long maxModels() {
    return _hyperSpaceWalker.search_criteria().stoppingCriteria() == null ? 0 : _hyperSpaceWalker.search_criteria().stoppingCriteria().getMaxModels();
  }

  private double maxRuntimeSecs() {
    return  _hyperSpaceWalker.search_criteria().stoppingCriteria() == null ? 0 : _hyperSpaceWalker.search_criteria().stoppingCriteria().getMaxRuntimeSecs();
  }

  private double remainingTimeSecs() {
    return _job != null && _job._max_runtime_msecs > 0  // compute only if a time limit was assigned to the job
            ? (_job.start_time() + _job._max_runtime_msecs - System.currentTimeMillis()) / 1000.
            : Double.MAX_VALUE;
  }

  private ScoreKeeper.StoppingMetric sortingMetric() {
    return _hyperSpaceWalker.search_criteria().stoppingCriteria() == null
            ? ScoreKeeper.StoppingMetric.AUTO
            : _hyperSpaceWalker.search_criteria().stoppingCriteria().getStoppingMetric();
  }

  private class ModelFeeder extends ParallelModelBuilder.ParallelModelBuilderCallback<ModelFeeder> {

    private final HyperSpaceWalker.HyperSpaceIterator<MP> hyperspaceIterator;
    private final Grid grid;
    private final Lock parallelSearchGridLock = new ReentrantLock();

    public ModelFeeder(HyperSpaceWalker.HyperSpaceIterator<MP> hyperspaceIterator, Grid grid) {
      this.hyperspaceIterator = hyperspaceIterator;
      this.grid = grid;
    }

    @Override
    public void onBuildSuccess(final Model finishedModel, final ParallelModelBuilder parallelModelBuilder) {
      try {
        parallelSearchGridLock.lock();
        constructScoringInfo(finishedModel);
        onModel(grid, finishedModel._input_parms.checksum(IGNORED_FIELDS_PARAM_HASH), finishedModel._key);

        _job.update(1);
        grid.update(_job);

        attemptGridSave(grid);
      } finally {
        parallelSearchGridLock.unlock();
      }
      attemptBuildNextModel(parallelModelBuilder, finishedModel);
    }

    @Override
    public void onBuildFailure(final ParallelModelBuilder.ModelBuildFailure modelBuildFailure,
                               final ParallelModelBuilder parallelModelBuilder) {
      parallelSearchGridLock.lock();
      try {
        grid.appendFailedModelParameters(null, modelBuildFailure.getParameters(), modelBuildFailure.getThrowable());
      } finally {
        parallelSearchGridLock.unlock();
      }
      attemptBuildNextModel(parallelModelBuilder, null);
    }

    private void attemptBuildNextModel(final ParallelModelBuilder parallelModelBuilder, final Model previousModel) {
      // Attempt to train next model
      try {
        parallelSearchGridLock.lock();
        final MP nextModelParams = getNextModelParams(hyperspaceIterator, previousModel, grid);
        if (nextModelParams != null
                && isThereEnoughTime()
                && !_job.stop_requested()
                && !_hyperSpaceWalker.stopEarly(previousModel, grid.getScoringInfos())
        ) {
          reconcileMaxRuntime(grid._key, nextModelParams);
          parallelModelBuilder.run(Collections.singletonList(ModelBuilder.make(nextModelParams)));
        }
      } finally {
        parallelSearchGridLock.unlock();
      }
    }

    private void constructScoringInfo(final Model model) {
      ScoringInfo scoringInfo = new ScoringInfo();
      scoringInfo.time_stamp_ms = System.currentTimeMillis();

      model.fillScoringInfo(scoringInfo);
      grid.setScoringInfos(ScoringInfo.prependScoringInfo(scoringInfo, grid.getScoringInfos()));
      ScoringInfo.sort(grid.getScoringInfos(), sortingMetric());
    }

    private boolean isThereEnoughTime() {
      final boolean enoughTime = remainingTimeSecs() > 0;
      if (!enoughTime) {
        Log.info("Grid max_runtime_secs of " + maxRuntimeSecs() + " secs has expired; stopping early.");
      }
      return enoughTime;
    }

    private MP getNextModelParams(final HyperSpaceWalker.HyperSpaceIterator<MP> hyperSpaceIterator, final Model model, final Grid grid){
      MP params = null;

      while (params == null) {
        if (hyperSpaceIterator.hasNext()) {
          params = hyperSpaceIterator.nextModelParameters();
          final Key modelKey = grid.getModelKey(params.checksum(IGNORED_FIELDS_PARAM_HASH));
          if (modelKey != null) {
            params = null;
          }
        } else {
          break;
        }
      }

      return params;
    }
  }

  /**
   * Searches the hyperspace and builds models in a parallel way - building the models in parallel.
   *
   * @param grid Grid to add models to
   */
  private void parallelGridSearch(final Grid<MP> grid) {
    final HyperSpaceWalker.HyperSpaceIterator<MP> iterator = _hyperSpaceWalker.iterator();
    final ModelFeeder modelFeeder = new ModelFeeder(iterator, grid);
    final ParallelModelBuilder parallelModelBuilder = new ParallelModelBuilder(modelFeeder);

    List<ModelBuilder> startModels = new ArrayList<>();

    while (startModels.size() < _parallelism && iterator.hasNext()) {
      final MP nextModelParameters = iterator.nextModelParameters();
      final long checksum = nextModelParameters.checksum(IGNORED_FIELDS_PARAM_HASH);
      if (grid.getModelKey(checksum) == null) {
        startModels.add(ModelBuilder.make(nextModelParameters));
      }
    }

    if(!startModels.isEmpty()) {
      parallelModelBuilder.run(startModels);
      parallelModelBuilder.join();
    }
    grid.update(_job);

    attemptGridSave(grid);
    grid.unlock(_job);
  }


  /**
   * Invokes grid search based on specified hyper space walk strategy.
   *
   * It updates passed grid object in distributed store.
   *
   * @param grid grid object to save results; grid already locked
   */
  private void gridSearch(Grid<MP> grid) {
    Model model = null;
    // Prepare nice model key and override default key by appending model counter
    //String protoModelKey = _hyperSpaceWalker.getParams()._model_id == null
    //                       ? grid._key + "_model_"
    //                       : _hyperSpaceWalker.getParams()._model_id.toString() + H2O.calcNextUniqueModelId("") + "_";
    String protoModelKey = grid._key + "_model_";

    try {
      // Get iterator to traverse hyper space
      HyperSpaceWalker.HyperSpaceIterator<MP> it = _hyperSpaceWalker.iterator();
      // Number of traversed model parameters
      int counter = grid.getModelCount();
      while (it.hasNext()) {
        if (_job.stop_requested()) throw new Job.JobCancelledException();  // Handle end-user cancel request

        try {
          // Get parameters for next model
          MP params = it.nextModelParameters();

          // Sequential model building, should never propagate
          // exception up, just mark combination of model parameters as wrong

          reconcileMaxRuntime(grid._key, params);

          Model currentModel = null;
          try {
            ScoringInfo scoringInfo = new ScoringInfo();
            scoringInfo.time_stamp_ms = System.currentTimeMillis();

            //// build the model!
            currentModel = buildModel(params, grid, ++counter, protoModelKey);
            model = currentModel;
            if (model != null) {
              model.fillScoringInfo(scoringInfo);
              grid.setScoringInfos(ScoringInfo.prependScoringInfo(scoringInfo, grid.getScoringInfos()));

              ScoringInfo.sort(grid.getScoringInfos(), sortingMetric());
            }
          } catch (RuntimeException e) { // Catch everything
            if (Job.isCancelledException(e)) {
              assert currentModel == null;
              final long checksum = params.checksum(IGNORED_FIELDS_PARAM_HASH);
              final Key<Model>[] modelKeys = findModelsByChecksum(checksum);
              if (modelKeys.length == 1) {
                Keyed.removeQuietly(modelKeys[0]);
              } else if (modelKeys.length > 1) {
                Log.warn("Checksum " + checksum + " " +
                        "identified more than one model to clean-up, keeping all: " + Arrays.toString(modelKeys) + 
                        ". This could lead to a memory leak.");
              } else
                Log.debug("Model with param checksum " + checksum + " was cancelled before it was installed in DKV.");
            } else {
              Log.warn("Grid search: model builder for parameters " + params + " failed! Exception: ", e);
            }

            grid.appendFailedModelParameters(currentModel != null ? currentModel._key : null, params, e);
          }
        } catch (IllegalArgumentException e) {
          Log.warn("Grid search: construction of model parameters failed! Exception: ", e);
          // Model parameters cannot be constructed for some reason
          final Model failedModel = model; // FIXME: Is this really the failed model? It can also be the _previus_ successful model.
          it.onModelFailure(failedModel, failedHyperParams -> grid.appendFailedModelParameters(failedModel != null ? failedModel._key : null, failedHyperParams, e));
        } finally {
          // Update progress by 1 increment
          _job.update(1);
          // Always update grid in DKV after model building attempt
          grid.update(_job);
          attemptGridSave(grid);
        } // finally

        if (model != null && grid.getScoringInfos() != null && // did model build and scoringInfo creation succeed?
                _hyperSpaceWalker.stopEarly(model, grid.getScoringInfos())) {
          Log.info("Convergence detected based on simple moving average of the loss function. Grid building completed.");
          break;
        }
      } // while (it.hasNext(model))
      Log.info("For grid: " + grid._key + " built: " + grid.getModelCount() + " models.");
    } finally {
      grid.unlock(_job);
    }
  }

  /**
   * see {@code RandomDiscreteValueSearchCriteria.max_runtime_secs} for reconciliation logic
   */
  private void reconcileMaxRuntime(Key<Grid<MP>> gridKey, Model.Parameters params) {
    double grid_max_runtime_secs = _job._max_runtime_msecs / 1000.;
    double time_remaining_secs = remainingTimeSecs();
    if (grid_max_runtime_secs > 0) {
      Log.info("Grid time is limited to: " + grid_max_runtime_secs + " for grid: " + gridKey + ". Remaining time is: " + time_remaining_secs);
      if (time_remaining_secs < 0) {
        Log.info("Grid max_runtime_secs of " + grid_max_runtime_secs + " secs has expired; stopping early.");
        throw new Job.JobCancelledException();
      }
    }

    if (params._max_runtime_secs > 0 ) {
      double was = params._max_runtime_secs;
      params._max_runtime_secs = Math.min(params._max_runtime_secs, time_remaining_secs);
      Log.info("Due to the grid time limit, changing model max runtime from: " + was + " secs to: " + params._max_runtime_secs + " secs.");
    } else { // params._max_runtime_secs == 0
      params._max_runtime_secs = time_remaining_secs;
      Log.info("Due to the grid time limit, changing model max runtime to: " + params._max_runtime_secs + " secs.");
    }
  }
  
  private void beforeGridStart(Grid grid) {
    if (_recovery != null) {
      _recovery.onStart(grid);
    }
  }
  
  private void afterGridCompleted(Grid grid) {
    if (_recovery != null) {
      _recovery.onDone(grid);
    }
  }
  
  private void onModel(Grid grid, long checksum, Key<Model> modelKey) {
    grid.putModel(checksum, modelKey);
    if (_recovery != null) {
      _recovery.onModel(grid, modelKey);
    }
  }
  
  /**
   * Saves the grid, if folder for export is defined, otherwise does nothing.
   *
   * @param grid Grid to save.
   */
  private void attemptGridSave(final Grid grid) {
    final String checkpointsDir = _hyperSpaceWalker.getParams()._export_checkpoints_dir;
    if (checkpointsDir == null) return;
    grid.exportBinary(checkpointsDir, false);
  }

  static final Set<String> IGNORED_FIELDS_PARAM_HASH = Collections.singleton("_export_checkpoints_dir");

  /**
   * Build a model based on specified parameters and save it to resulting Grid object.
   *
   * Returns a model run with these parameters, typically built on demand and cached - expected to
   * be an expensive operation.  If the model in question is "in progress", a 2nd build will NOT be
   * kicked off. This is a blocking call.
   *
   * If a new model is created, then the Grid object is updated in distributed store. If a model for
   * given parameters already exists, it is directly returned without updating the Grid object. If
   * model building fails then the Grid object is not updated and the method returns
   * <code>null</code>.
   *
   * @param params parameters for a new model
   * @param grid   grid object holding created models
   * @param paramsIdx  index of generated model parameter
   * @param protoModelKey  prototype of model key
   * @return return a new model if it does not exist
   */
  private Model buildModel(final MP params, Grid<MP> grid, int paramsIdx, String protoModelKey) {
    // Make sure that the model is not yet built (can be case of duplicated hyper parameters).
    // We first look in the grid _models cache, then we look in the DKV.
    // FIXME: get checksum here since model builder will modify instance of params!!!

    // Grid search might be continued over the very exact hyperspace, but with autoexporting disabled. 
    // To prevent 
    final long checksum = params.checksum(IGNORED_FIELDS_PARAM_HASH);
    Key<Model> key = grid.getModelKey(checksum);
    if (key != null) {
      if (DKV.get(key) == null) {
        // We know about a model that's been removed; rebuild.
        Log.info("GridSearch.buildModel(): model with these parameters was built but removed, rebuilding; checksum: " + checksum);
      } else {
        Log.info("GridSearch.buildModel(): model with these parameters already exists, skipping; checksum: " + checksum);
        return key.get();
      }
    }

    // Is there a model with the same params in the DKV?
    Key<Model>[] modelKeys = findModelsByChecksum(checksum);

    if (modelKeys.length > 0) {
      onModel(grid, checksum, modelKeys[0]);
      return modelKeys[0].get();
    }

    // Modify model key to have nice version with counter
    // Note: Cannot create it before checking the cache since checksum would differ for each model
    Key<Model> result = Key.make(protoModelKey + paramsIdx);
    // Build a new model
    assert grid.getModel(params) == null;
    Model m = ModelBuilder.trainModelNested(_job, result, params, null);
    assert checksum == m._input_parms.checksum(IGNORED_FIELDS_PARAM_HASH) : 
        "Model checksum different from original params";
    onModel(grid, checksum, result);
    return m;
  }

  @SuppressWarnings("unchecked")
  static Key<Model>[] findModelsByChecksum(final long checksum) {
    return KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        if (! Value.isSubclassOf(k._type, Model.class))
          return false;
        Model m = ((Model)k._key.get());
        if ((m == null) || (m._parms == null))
          return false;
        try {
          return m._parms.checksum(IGNORED_FIELDS_PARAM_HASH) == checksum;
        } catch (H2OConcurrentModificationException e) {
          // We are inspecting model parameters that doesn't belong to us - they might be modified (or deleted) while
          // checksum is being calculated: we skip them (see PUBDEV-5286)
          Log.warn("GridSearch encountered concurrent modification while searching DKV", e);
          return false;
        } catch (final RuntimeException e) {
          Throwable ex = e;
          boolean concurrentModification = false;
          while (ex.getCause() != null) {
            ex = ex.getCause();
            if (ex instanceof H2OConcurrentModificationException) {
              concurrentModification = true;
              break;
            }
          }
          if (! concurrentModification)
            throw e;
          Log.warn("GridSearch encountered concurrent modification while searching DKV", e);
          return false;
        }
      }
    }).keys();
  } 
  
  /**
   * Defines a key for a new Grid object holding results of grid search.
   *
   * @return a grid key for a particular modeling class and frame.
   * @throws java.lang.IllegalArgumentException if frame is not saved to distributed store.
   */
  protected static Key<Grid> gridKeyName(String modelName, Frame fr) {
    if (fr == null || fr._key == null) {
      throw new IllegalArgumentException("The frame being grid-searched over must have a Key");
    }
    return Key.make("Grid_" + modelName + "_" + fr._key.toString() + H2O.calcNextUniqueModelId(""));
  }

  /**
   * Start a new grid search job.  This is the method that gets called by GridSearchHandler.do_train().
   * <p>
   * This method launches a "classical" grid search traversing cartesian grid of parameters
   * point-by-point, <b>or</b> a random hyperparameter search, depending on the value of the <i>strategy</i>
   * parameter.
   *
   * @param destKey              A key to store result of grid search under.
   * @param params               Default parameters for model builder. This object is used to create
   *                             a specific model parameters for a combination of hyper parameters.
   * @param hyperParams          A set of arrays of hyper parameter values, used to specify a simple
   *                             fully-filled-in grid search.
   * @param paramsBuilderFactory defines a strategy for creating a new model parameters based on
   *                             common parameters and list of hyper-parameters
   * @param parallelism          Level of model-building parallelism
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
          final Key<Grid> destKey,
          final MP params,
          final Map<String, Object[]> hyperParams,
          final ModelParametersBuilderFactory<MP> paramsBuilderFactory,
          final HyperSpaceSearchCriteria searchCriteria,
          final int parallelism) {

    return startGridSearch(
        destKey, params, hyperParams, paramsBuilderFactory, searchCriteria, null, parallelism
    );
  }

  /**
   * Start a new grid search job.  This is the method that gets called by GridSearchHandler.do_train().
   * <p>
   * This method launches a "classical" grid search traversing cartesian grid of parameters
   * point-by-point, <b>or</b> a random hyperparameter search, depending on the value of the <i>strategy</i>
   * parameter.
   *
   * @param destKey              A key to store result of grid search under.
   * @param params               Default parameters for model builder. This object is used to create
   *                             a specific model parameters for a combination of hyper parameters.
   * @param hyperParams          A set of arrays of hyper parameter values, used to specify a simple
   *                             fully-filled-in grid search.
   * @param paramsBuilderFactory defines a strategy for creating a new model parameters based on
   *                             common parameters and list of hyper-parameters
   * @param recovery             Defines recovery strategy for when the cluster crashes while grid is 
   *                             training models.
   * @param parallelism          Level of model-building parallelism
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
      final Key<Grid> destKey,
      final MP params,
      final Map<String, Object[]> hyperParams,
      final ModelParametersBuilderFactory<MP> paramsBuilderFactory,
      final HyperSpaceSearchCriteria searchCriteria,
      final Recovery<Grid> recovery,
      final int parallelism) {

    return startGridSearch(
        destKey,
        BaseWalker.WalkerFactory.create(params, hyperParams, paramsBuilderFactory, searchCriteria),
        recovery,
        parallelism
    );
  }

  /**
   * Start a new sequential grid search job.
   *
   * <p>This method launches "classical" grid search traversing cartesian grid of parameters
   * point-by-point.  For more advanced hyperparameter search behavior call the referenced method.
   *
   * @param destKey     A key to store result of grid search under.
   * @param params      Default parameters for model builder. This object is used to create a
   *                    specific model parameters for a combination of hyper parameters.
   * @param hyperParams A set of arrays of hyper parameter values, used to specify a simple
   *                    fully-filled-in grid search.
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   * @see #startGridSearch(Key, Model.Parameters, Map, ModelParametersBuilderFactory, HyperSpaceSearchCriteria, Recovery, int)
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
          final Key<Grid> destKey,
          final MP params,
          final Map<String, Object[]> hyperParams) {
    return startGridSearch(
            destKey,
            params,
            hyperParams,
            new SimpleParametersBuilderFactory<>(),
            new HyperSpaceSearchCriteria.CartesianSearchCriteria(),
            GridSearch.SEQUENTIAL_MODEL_BUILDING
    );
  }


  /**
   * Start a new grid search job.
   *
   * <p>This method launches "classical" grid search traversing cartesian grid of parameters
   * point-by-point.  For more advanced hyperparameter search behavior call the referenced method.
   *
   * @param destKey      A key to store result of grid search under.
   * @param params       Default parameters for model builder. This object is used to create a
   *                     specific model parameters for a combination of hyper parameters.
   * @param hyperParams  A set of arrays of hyper parameter values, used to specify a simple
   *                     fully-filled-in grid search.
   * @param parallelism Level of parallelism during the process of building this grid's models
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   *
   * @see #startGridSearch(Key, Model.Parameters, Map, ModelParametersBuilderFactory, HyperSpaceSearchCriteria, Recovery, int)
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
          final Key<Grid> destKey,
          final MP params,
          final Map<String, Object[]> hyperParams,
          final int parallelism
  ) {
    return startGridSearch(
            destKey,
            params,
            hyperParams,
            new SimpleParametersBuilderFactory<MP>(),
            new HyperSpaceSearchCriteria.CartesianSearchCriteria(),
            parallelism
    );
  }

  /**
   * Start a new grid search job. <p> This method launches any grid search traversing space of hyper
   * parameters based on specified strategy.
   *
   * @param destKey          A key to store result of grid search under.
   * @param hyperSpaceWalker Defines a strategy for traversing a hyper space. The object itself
   *                         holds definition of hyper space.
   * @param parallelism      Level of parallelism during the process of building of the grid models
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
          final Key<Grid> destKey,
          final HyperSpaceWalker<MP, ?> hyperSpaceWalker,
          final int parallelism
  ) {
    return startGridSearch(destKey, hyperSpaceWalker, null, parallelism);
  }

  /**
   * Start a new grid search job. <p> This method launches any grid search traversing space of hyper
   * parameters based on specified strategy.
   *
   * @param destKey          A key to store result of grid search under.
   * @param hyperSpaceWalker Defines a strategy for traversing a hyper space. The object itself
   *                         holds definition of hyper space.
   * @param recovery         Defines recovery strategy for when the cluster crashes while grid is 
   *                         training models.
   * @param parallelism      Level of parallelism during the process of building of the grid models
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
      final Key<Grid> destKey,
      final HyperSpaceWalker<MP, ?> hyperSpaceWalker,
      final Recovery<Grid> recovery,
      final int parallelism
  ) {
    // Compute key for destination object representing grid
    MP params = hyperSpaceWalker.getParams();
    Key<Grid> gridKey = destKey != null ? destKey
        : gridKeyName(params.algoName(), params.train());

    // Start the search
    return new GridSearch<>(gridKey, hyperSpaceWalker, recovery, parallelism).start();
  }

  /**
   * The factory is producing a parameters builder which uses reflection to setup field values.
   *
   * @param <MP> type of model parameters object
   */
  public static class SimpleParametersBuilderFactory<MP extends Model.Parameters>
          implements ModelParametersBuilderFactory<MP> {

    @Override
    public ModelParametersBuilder<MP> get(MP initialParams) {
      return new SimpleParamsBuilder<>(initialParams);
    }

    @Override
    public PojoUtils.FieldNaming getFieldNamingStrategy() {
      return PojoUtils.FieldNaming.CONSISTENT;
    }

    /**
     * The builder modifies initial model parameters directly by reflection.
     *
     * Usage:
     * <pre>{@code
     *   GBMModel.GBMParameters params =
     *     new SimpleParamsBuilder(initialParams)
     *      .set("_ntrees", 30).set("_learn_rate", 0.01).build()
     * }</pre>
     *
     * @param <MP> type of model parameters object
     */
    public static class SimpleParamsBuilder<MP extends Model.Parameters>
            implements ModelParametersBuilder<MP> {

      final private MP params;

      public SimpleParamsBuilder(MP initialParams) {
        params = initialParams;
      }

      @Override
      public ModelParametersBuilder<MP> set(String name, Object value) {
        PojoUtils.setField(params, name, value, PojoUtils.FieldNaming.CONSISTENT);
        return this;
      }

      @Override
      public MP build() {
        return params;
      }
    }
  }

  /**
   * Constant for adaptive parallelism level - number of models built in parallel is decided by H2O.
   */
  public static final int ADAPTIVE_PARALLELISM_LEVEL = 0;
  public static final int SEQUENTIAL_MODEL_BUILDING = 1;

  /**
   * Converts user-given number representing parallelism level and regime to a final number, representing the number of models
   * built in parallel.
   *
   * @param parallelism User-desired parallelism, the frontend/client API representation.
   * @return An integer >= 1, representing the final number of models to be built in parallel. 1 effectively means sequential
   * (no parallelism).
   */
  public static int getParallelismLevel(final int parallelism) {
    if (parallelism < 0) {
      throw new IllegalArgumentException(String.format("Grid search parallelism level must be >= 0. Give value is '%d'.",
              parallelism));
    }

    if (parallelism == 0) {
      return getAdaptiveParallelism();
    } else {
      return parallelism;
    }
  }

  /**
   * @return An integer with dynamically calculated level of parallelism based on Cluster's properties.
   */
  public static int getAdaptiveParallelism() {
    return 2 * H2O.NUMCPUS;
  }
}
