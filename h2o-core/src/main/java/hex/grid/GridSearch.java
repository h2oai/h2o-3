package hex.grid;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersBuilderFactory;
import hex.ScoringInfo;
import hex.grid.HyperSpaceWalker.BaseWalker;
import jsr166y.CountedCompleter;
import water.*;
import water.exceptions.H2OConcurrentModificationException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.PojoUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Grid search job.
 *
 * This job represents a generic interface to launch "any" hyper space
 * search. It triggers sub-jobs for each point in hyper space.  It produces
 * <code>Grid</code> object which contains a list of build models. A triggered
 * model builder job can fail!
 *
 * Grid search is parametrized by hyper space walk strategy ({@link
 * hex.grid.HyperSpaceWalker} which defines how the space of hyper parameters
 * is traversed.
 *
 * The job is started by the <code>startGridSearch</code> method which create a new grid search, put
 * representation of Grid into distributed KV store, and for each parameter in hyper space of
 * possible parameters, it launches a separated model building job. The launch of jobs is sequential
 * and blocking. So after finish the last model, whole grid search job is done as well.
 *
 * By default, the grid search invokes cartezian grid search, but it can be
 * modified by passing explicit hyper space walk strategy via the
 * {@link #startGridSearch(Key, HyperSpaceWalker)} method.
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
 * @see hex.grid.HyperSpaceWalker
 * @see #startGridSearch(Key, HyperSpaceWalker)
 */
public final class GridSearch<MP extends Model.Parameters> extends Keyed<GridSearch> {
  public final Key<Grid> _result;
  public final Job<Grid> _job;

  /** Walks hyper space and for each point produces model parameters. It is
   *  used only locally to fire new model builders.  */
  private final transient HyperSpaceWalker<MP, ?> _hyperSpaceWalker;

  private GridSearch(Key<Grid> gkey, HyperSpaceWalker<MP, ?> hyperSpaceWalker) {
    assert hyperSpaceWalker != null : "Grid search needs to know how to walk around hyper space!";
    _hyperSpaceWalker = hyperSpaceWalker;
    _result = gkey;
    String algoName = hyperSpaceWalker.getParams().algoName();
    _job = new Job<>(gkey, Grid.class.getName(), algoName + " Grid Search");
    // Note: do not validate parameters of created model builders here!
    // Leave it to launch time, and just mark the corresponding model builder job as failed.
  }

  Job<Grid> start() {
    final long gridSize = _hyperSpaceWalker.getMaxHyperSpaceSize();
    Log.info("Starting gridsearch: estimated size of search space = " + gridSize);
    // Create grid object and lock it
    // Creation is done here, since we would like make sure that after leaving
    // this function the grid object is in DKV and accessible.
    final Grid<MP> grid;
    Keyed keyed = DKV.getGet(_result);
    if (keyed != null) {
      if (! (keyed instanceof Grid))
        throw new H2OIllegalArgumentException("Name conflict: tried to create a Grid using the ID of a non-Grid object that's already in H2O: " + _job._result + "; it is a: " + keyed.getClass());
      grid = (Grid) keyed;
      Frame specTrainFrame = _hyperSpaceWalker.getParams().train();
      Frame oldTrainFrame = grid.getTrainingFrame();
      if (oldTrainFrame != null && !specTrainFrame._key.equals(oldTrainFrame._key) ||
          oldTrainFrame != null && specTrainFrame.checksum() != oldTrainFrame.checksum())
        throw new H2OIllegalArgumentException("training_frame", "grid", "Cannot append new models to a grid with different training input");
      grid.write_lock(_job);
    } else {
      grid =
          new Grid<>(_result,
                     _hyperSpaceWalker.getParams(),
                     _hyperSpaceWalker.getHyperParamNames(),
                     _hyperSpaceWalker.getParametersBuilderFactory().getFieldNamingStrategy());
      grid.delete_and_lock(_job);
    }

    Model model = null;
    HyperSpaceWalker.HyperSpaceIterator<MP> it = _hyperSpaceWalker.iterator();
    long gridWork=0;
    if (gridSize > 0) {//if total grid space is known, walk it all and count up models to be built (not subject to time-based or converge-based early stopping)
      int count=0;
      while (it.hasNext(model) && (it.max_models() > 0 && count++ < it.max_models())) { //only walk the first max_models models, if specified
        try {
          Model.Parameters parms = it.nextModelParameters(model);
          gridWork += (parms._nfolds > 0 ? (parms._nfolds+1/*main model*/) : 1) *parms.progressUnits();
        } catch(Throwable ex) {
          //swallow invalid combinations
        }
      }
    } else {
      //TODO: Future totally unbounded search: need a time-based progress bar
      gridWork = Long.MAX_VALUE;
    }
    it.reset();

    // Install this as job functions
    return _job.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        gridSearch(grid);
        tryComplete();
      }
      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        Log.warn("GridSearch job "+_job._description+" completed with exception: "+ex);
        return true;
      }
    }, gridWork, it.max_runtime_secs());
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
      while (it.hasNext(model)) {
        if (_job.stop_requested()) throw new Job.JobCancelledException();  // Handle end-user cancel request
        double max_runtime_secs = it.max_runtime_secs();

        double time_remaining_secs = Double.MAX_VALUE;
        if (max_runtime_secs > 0) {
          time_remaining_secs = it.time_remaining_secs();
          if (time_remaining_secs < 0) {
            Log.info("Grid max_runtime_secs of " + max_runtime_secs + " secs has expired; stopping early.");
            throw new Job.JobCancelledException();
          }
        }

        MP params;
        try {
          // Get parameters for next model
          params = it.nextModelParameters(model);

          // Sequential model building, should never propagate
          // exception up, just mark combination of model parameters as wrong

          // Do we need to limit the model build time?
          if (max_runtime_secs > 0) {
            Log.info("Grid time is limited to: " + max_runtime_secs + " for grid: " + grid._key + ". Remaining time is: " + time_remaining_secs);
            double scale = params._nfolds > 0 ? params._nfolds+1 : 1; //remaining time per cv model is less
            if (params._max_runtime_secs == 0) { // unlimited
              params._max_runtime_secs = time_remaining_secs/scale;
              Log.info("Due to the grid time limit, changing model max runtime to: " + params._max_runtime_secs + " secs.");
            } else {
              double was = params._max_runtime_secs;
              params._max_runtime_secs = Math.min(params._max_runtime_secs, time_remaining_secs/scale);
              Log.info("Due to the grid time limit, changing model max runtime from: " + was + " secs to: " + params._max_runtime_secs + " secs.");
            }
          }

          try {
            ScoringInfo scoringInfo = new ScoringInfo();
            scoringInfo.time_stamp_ms = System.currentTimeMillis();

            //// build the model!
            model = buildModel(params, grid, ++counter, protoModelKey);
            if (model != null) {
              model.fillScoringInfo(scoringInfo);
              grid.setScoringInfos(ScoringInfo.prependScoringInfo(scoringInfo, grid.getScoringInfos()));
              ScoringInfo.sort(grid.getScoringInfos(), _hyperSpaceWalker.search_criteria().stopping_metric()); // Currently AUTO for Cartesian and user-specified for RandomDiscrete
            }
          } catch (RuntimeException e) { // Catch everything
            if (!Job.isCancelledException(e)) {
              StringWriter sw = new StringWriter();
              PrintWriter pw = new PrintWriter(sw);
              e.printStackTrace(pw);
              Log.warn("Grid search: model builder for parameters " + params + " failed! Exception: ", e, sw.toString());
            }
            grid.appendFailedModelParameters(params, e);
          }
        } catch (IllegalArgumentException e) {
          Log.warn("Grid search: construction of model parameters failed! Exception: ", e);
          // Model parameters cannot be constructed for some reason
          it.modelFailed(model);
          Object[] rawParams = it.getCurrentRawParameters();
          grid.appendFailedModelParameters(rawParams, e);
        } finally {
          // Update progress by 1 increment
          _job.update(1);
          // Always update grid in DKV after model building attempt
          grid.update(_job);
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

    final long checksum = params.checksum();
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
    final Key<Model>[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        if (! Value.isSubclassOf(k._type, Model.class))
          return false;
        Model m = ((Model)k._key.get());
        if ((m == null) || (m._parms == null))
          return false;
        try {
          return m._parms.checksum() == checksum;
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

    if (modelKeys.length > 0) {
      grid.putModel(checksum, modelKeys[0]);
      return modelKeys[0].get();
    }


    // Modify model key to have nice version with counter
    // Note: Cannot create it before checking the cache since checksum would differ for each model
    Key<Model> result = Key.make(protoModelKey + paramsIdx);
    // Build a new model
    // THIS IS BLOCKING call since we do not have enough information about free resources
    // FIXME: we should allow here any launching strategy (not only sequential)
    Model m = (Model)startBuildModel(result,params, grid).dest().get();
    grid.putModel(checksum, result);
    return m;
  }

  /**
   * Triggers model building process but do not block on it.
   *
   * @param params parameters for a new model
   * @param grid   resulting grid object
   * @return A Future of a model run with these parameters, typically built on demand and not cached
   * - expected to be an expensive operation.  If the model in question is "in progress", a 2nd
   * build will NOT be kicked off. This is a non-blocking call.
   */
  private ModelBuilder startBuildModel(Key result, MP params, Grid<MP> grid) {
    if (grid.getModel(params) != null) return null;
    ModelBuilder mb = ModelBuilder.make(params.algoName(), _job, result);
    mb._parms = params;
    mb.trainModelNested(null);
    return mb;
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
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
      final Key<Grid> destKey,
      final MP params,
      final Map<String, Object[]> hyperParams,
      final ModelParametersBuilderFactory<MP> paramsBuilderFactory,
      final HyperSpaceSearchCriteria searchCriteria) {

    return startGridSearch(
        destKey,
        BaseWalker.WalkerFactory.create(params, hyperParams, paramsBuilderFactory, searchCriteria)
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
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   *
   * @see #startGridSearch(Key, Model.Parameters, Map, ModelParametersBuilderFactory, HyperSpaceSearchCriteria)
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
      final Key<Grid> destKey,
      final MP params,
      final Map<String, Object[]> hyperParams
  ) {
    return startGridSearch(
        destKey,
        params,
        hyperParams,
        new SimpleParametersBuilderFactory<MP>(),
        new HyperSpaceSearchCriteria.CartesianSearchCriteria());
  }

  /**
   * Start a new grid search job. <p> This method launches any grid search traversing space of hyper
   * parameters based on specified strategy.
   *
   * @param destKey          A key to store result of grid search under.
   * @param hyperSpaceWalker defines a strategy for traversing a hyper space. The object itself
   *                         holds definition of hyper space.
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> Job<Grid> startGridSearch(
      final Key<Grid> destKey,
      final HyperSpaceWalker<MP, ?> hyperSpaceWalker
    ) {
    // Compute key for destination object representing grid
    MP params = hyperSpaceWalker.getParams();
    Key<Grid> gridKey = destKey != null ? destKey
            : gridKeyName(params.algoName(), params.train());

    // Start the search
    return new GridSearch(gridKey, hyperSpaceWalker).start();
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
}
