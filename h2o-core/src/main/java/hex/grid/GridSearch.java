package hex.grid;

import java.util.Map;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersBuilderFactory;
import hex.grid.HyperSpaceWalker.CartesianWalker;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.PojoUtils;

/**
 * Grid search job.
 *
 * This job represents a generic interface to launch "any" hyper space search. It triggers sub-jobs
 * for each point in hyper space. It produces <code>Grid</code> object which contains a list of
 * build models. A triggered model builder job can fail!
 *
 * Grid search is parametrized by: <ul> <li>model factory ({@link hex.grid.ModelFactory}) defines
 * model build process</li> <li>hyper space walk strategy ({@link hex.grid.HyperSpaceWalker} defines
 * how the space of hyper parameters is traversed</li> </ul>
 *
 * The job is started by the <code>startGridSearch</code> method which create a new grid search, put
 * representation of Grid into distributed KV store, and for each parameter in hyper space of
 * possible parameters, it launches a separated model building job. The launch of jobs is sequential
 * and blocking. So after finish the last model, whole grid search job is done as well.
 *
 * By default, the grid search invokes cartezian grid search, but it can be modified by passing
 * explicit hyper space walk strategy via the {@link #startGridSearch(Key, ModelFactory,
 * HyperSpaceWalker)} method.
 *
 * If any of forked jobs fails then the failure is ignored, and grid search normally continue in
 * traversing the hyper space.
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
 * hyperParms.put("_distribution",new Distribution.Family[] {Distribution.Family.multinomial});
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
 * @see hex.grid.ModelFactory
 * @see hex.grid.HyperSpaceWalker
 * @see #startGridSearch(Key, ModelFactory, HyperSpaceWalker)
 */
// FIXME: this class should be driver which is passed to job as H2OCountedCompleter. Will be
// FIXME: refactored as part of Job refactoring.
public final class GridSearch<MP extends Model.Parameters> extends Job<Grid> {

  /**
   * Produces a new model builder for given parameters.
   */
  private final transient ModelFactory<MP> _modelFactory;
  /**
   * Walks hyper space and for each point produces model parameters. It is used only locally to fire
   * new model builders via ModelFactory.
   */
  private final transient HyperSpaceWalker<MP> _hyperSpaceWalker;


  private GridSearch(Key gkey,
                     ModelFactory<MP> modelFactory,
                     HyperSpaceWalker<MP> hyperSpaceWalker) {
    super(gkey, modelFactory.getModelName() + " Grid Search");
    assert modelFactory != null : "Grid search needs to know how to build a new model!";
    assert hyperSpaceWalker != null : "Grid search needs to know to how walk around hyper space!";
    //_paramsBuilderFactory = paramsBuilderFactory;
    _modelFactory = modelFactory;
    _hyperSpaceWalker = hyperSpaceWalker;

    // Note: do not validate parameters of created model builders here!
    // Leave it to launch time, and just mark the corresponding model builder job as failed.
  }

  GridSearch start() {
    final int gridSize = _hyperSpaceWalker.getHyperSpaceSize();
    Log.info("Starting gridsearch: estimated size of search space = " + gridSize);
    // Create grid object and lock it
    // Creation is done here, since we would like make sure that after leaving
    // this function the grid object is in DKV and accessible.
    Grid<MP> grid = DKV.getGet(dest());
    if (grid != null) {
      Frame specTrainFrame = _hyperSpaceWalker.getParams().train();
      Frame oldTrainFrame = grid.getTrainingFrame();
      if (!specTrainFrame._key.equals(oldTrainFrame._key) ||
          specTrainFrame.checksum() != oldTrainFrame.checksum()) {
        throw new H2OIllegalArgumentException("training_frame", "grid", "Cannot append new models"
                                              + " to a grid with different training input");
      }
      grid.write_lock(jobKey());
    } else {
      grid =
          new Grid<>(dest(),
                     _hyperSpaceWalker.getParams(),
                     _hyperSpaceWalker.getHyperParamNames(),
                     _modelFactory.getModelName(),
                     _hyperSpaceWalker.getParametersBuilderFactory().getFieldNamingStrategy());
      grid.delete_and_lock(jobKey());
    }
    // Java trick
    final Grid<MP> gridToExpose = grid;
    // Install this as job functions
    start(new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        gridSearch(gridToExpose);
        tryComplete();
      }
    }, gridSize, true);
    return this;
  }

  /**
   * Returns expected number of models in resulting Grid object.
   *
   * The number can differ from final number of models due to visiting duplicate points in hyper
   * space.
   *
   * @return expected number of models produced by this grid search
   */
  public int getModelCount() {
    return _hyperSpaceWalker.getHyperSpaceSize();
  }

  /**
   * Invokes grid search based on specified hyper space walk strategy.
   *
   * It updates passed grid object in distributed store.
   *
   * @param grid grid object to save results
   */
  private void gridSearch(Grid<MP> grid) {
    Model model = null;
    // Prepare nice model key and override default key by appending model counter
    String protoModelKey = _hyperSpaceWalker.getParams()._model_id == null
                           ? grid._key + "_model_"
                           : _hyperSpaceWalker.getParams()._model_id.toString() + H2O.calcNextUniqueModelId("") + "_";
    try {
      // Get iterator to traverse hyper space
      HyperSpaceWalker.HyperSpaceIterator<MP> it = _hyperSpaceWalker.iterator();
      // Number of traversed model parameters
      int counter = 0;
      while (it.hasNext(model)) {
        // Handle end-user cancel request
        if (!isRunning()) {
          // FIXME: propagate cancellation event to sub jobs, block till they are cancelled
          cancel();
          return;
        }
        MP params = null;
        try {
          // Get parameters for next model
          params = it.nextModelParameters(model);
          // Sequential model building, should never propagate
          // exception up, just mark combination of model parameters as wrong
          try {
            model = buildModel(params, grid, counter++, protoModelKey);
          } catch (RuntimeException e) { // Catch everything
            Log.warn("Grid search: model builder for parameters " + params + " failed! Exception: ", e);
            grid.appendFailedModelParameters(params, e);
          }
        } catch (IllegalArgumentException e) {
          Log.warn("Grid search: construction of model parameters failed! Exception: ", e);
          // Model parameters cannot be constructed for some reason
          Object[] rawParams = it.getCurrentRawParameters();
          grid.appendFailedModelParameters(rawParams, e);
        } finally {
          // Update progress by 1 increment
          this.update(1L);
          // Always update grid in DKV after model building attempt
          grid.update(jobKey());
        }
      }
      // Grid search is done
      done();
    } catch(Throwable e) {
      // Something wrong happened during hyper-space walking
      // So cancel this job
      // FIXME: should I delete grid here? it failed but user can be interested in partial result
      Job thisJob = DKV.getGet(jobKey());
      if (thisJob._state == JobState.CANCELLED) {
        Log.info("Job " + jobKey() + " cancelled by user.");
      } else {
        // Mark job as failed
        failed(e);
        // And propagate unknown exception up
        throw e;
      }
    } finally {
      // Unlock grid object
      grid.unlock(jobKey());
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
    // Make sure that the model is not yet built (can be case of duplicated hyper parameters)
    // FIXME: get checksum here since model builder will modify instance of params!!!
    long checksum = params.checksum();
    Key<Model> key = grid.getModelKey(checksum);
    // It was already built
    if (key != null) {
      return key.get();
    }
    // Modify model key to have nice version with counter
    // Note: Cannot create it before checking the cache since checksum would differ for each model
    params._model_id = Key.make(protoModelKey + paramsIdx);
    // Build a new model
    // THIS IS BLOCKING call since we do not have enough information about free resources
    // FIXME: we should allow here any launching strategy (not only sequential)
    Model m = (Model) (startBuildModel(params, grid).get());
    grid.putModel(checksum, m._key);
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
  private ModelBuilder startBuildModel(MP params, Grid<MP> grid) {
    if (grid.getModel(params) != null) {
      return null;
    }
    ModelBuilder mb = _modelFactory.buildModel(params);
    mb.trainModel();
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
   * Start a new grid search job.
   *
   * <p>This method launches "classical" grid search traversing cartezian grid of parameters
   * point-by-point.
   *
   * @param destKey              A key to store result of grid search under.
   * @param params               Default parameters for model builder. This object is used to create
   *                             a specific model parameters for a combination of hyper parameters.
   * @param hyperParams          A set of arrays of hyper parameter values, used to specify a simple
   *                             fully-filled-in grid search.
   * @param modelFactory         defines a strategy for creating new model builders
   * @param paramsBuilderFactory defines a strategy for creating a new model parameters based on
   *                             common parameters and list of hyper-parameters
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> GridSearch startGridSearch(
      final Key<Grid> destKey,
      final MP params,
      final Map<String, Object[]> hyperParams,
      final ModelFactory<MP> modelFactory,
      final ModelParametersBuilderFactory<MP> paramsBuilderFactory) {
    // Create a walker to traverse hyper space of model parameters
    CartesianWalker<MP>
        hyperSpaceWalker =
        new CartesianWalker<>(params, hyperParams, paramsBuilderFactory);

    return startGridSearch(destKey, modelFactory, hyperSpaceWalker);
  }


  /**
   * Start a new grid search job.
   *
   * <p>This method launches "classical" grid search traversing cartezian grid of parameters
   * point-by-point.
   *
   * @param destKey      A key to store result of grid search under.
   * @param params       Default parameters for model builder. This object is used to create a
   *                     specific model parameters for a combination of hyper parameters.
   * @param hyperParams  A set of arrays of hyper parameter values, used to specify a simple
   *                     fully-filled-in grid search.
   * @param modelFactory defines a strategy for creating new model builders
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> GridSearch startGridSearch(final Key<Grid> destKey,
                                                                         final MP params,
                                                                         final Map<String, Object[]> hyperParams,
                                                                         final ModelFactory<MP> modelFactory) {
    return startGridSearch(destKey, params, hyperParams, modelFactory,
                           new SimpleParametersBuilderFactory<MP>());
  }

  public static <MP extends Model.Parameters> GridSearch startGridSearch(final MP params,
                                                                         final Map<String, Object[]> hyperParams,
                                                                         final ModelFactory<MP> modelFactory) {
    return startGridSearch(null, params, hyperParams, modelFactory);
  }

  /**
   * Start a new grid search job. <p> This method launches any grid search traversing space of hyper
   * parameters based on specified strategy.
   *
   * @param destKey          A key to store result of grid search under.
   * @param modelFactory     defines a strategy for creating new model builders
   * @param hyperSpaceWalker defines a strategy for traversing a hyper space. The object itself
   *                         holds definition of hyper space.
   * @return GridSearch Job, with models run with these parameters, built as needed - expected to be
   * an expensive operation.  If the models in question are "in progress", a 2nd build will NOT be
   * kicked off.  This is a non-blocking call.
   */
  public static <MP extends Model.Parameters> GridSearch startGridSearch(
      final Key<Grid> destKey,
      final ModelFactory<MP> modelFactory,
      final HyperSpaceWalker<MP> hyperSpaceWalker) {
    // Compute key for destination object representing grid
    Key<Grid>
        gridKey =
        destKey != null ? destKey : gridKeyName(modelFactory.getModelName(),
                                                hyperSpaceWalker.getParams().train());

    // Start the search
    return new GridSearch(gridKey, modelFactory, hyperSpaceWalker).start();
  }

  /**
   * The factory is producing a parameters builder which uses reflection to setup field values.
   *
   * @param <MP> type of model parameters object
   */
  static class SimpleParametersBuilderFactory<MP extends Model.Parameters>
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
