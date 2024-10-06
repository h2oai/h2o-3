Grid (Hyperparameter) Search
============================


H2O supports two types of grid search -- traditional (or "cartesian") grid search and random grid search.  In a cartesian grid search, users specify a set of values for each hyperparameter that they want to search over, and H2O will train a model for every combination of the hyperparameter values.  This means that if you have three hyperparameters and you specify 5, 10 and 2 values for each, your grid will contain a total of 5*10*2 = 100 models.

In random grid search, the user specifies the hyperparameter space in the exact same way, except H2O will sample uniformly from the set of all possible hyperparameter value combinations.  In random grid search, the user also specifies a stopping criterion, which controls when the random grid search is completed.  The user can tell the random grid search to stop by specifying a maximum number of models or the maximum number of seconds allowed for the search.  The user may also specify a performance-metric-based stopping criterion, which will stop the random grid search when the performance stops improving by a specified amount. 

Once the grid search is complete, the user can query the grid object and sort the models by a particular performance metric (for example, "AUC").  All models are stored in the H2O cluster and are accessible by model id.

Examples of how to perform cartesian and random grid search in all of H2O's APIs follow below.  There are also longer grid search tutorials available for `R <https://github.com/h2oai/h2o-tutorials/blob/master/h2o-open-tour-2016/chicago/grid-search-model-selection.R>`__ and `Python <https://github.com/h2oai/h2o-tutorials/blob/master/h2o-open-tour-2016/chicago/grid-search-model-selection.ipynb>`__.

Quick Links
-----------

- `Grid Search in R <#grid-search-in-r>`__
- `Grid Search in Python <#grid-search-in-python>`__
- `Java API <#grid-search-java-api>`__
- `REST API <#rest-api>`__
- `Supported Grid Search Hyperparameters <#supported-grid-search-hyperparameters>`__
- `Grid Testing <#grid-testing>`__
- `Caveats/In Progress <#caveats-in-progress>`__
- `Additional Documentation <#additional-documentation>`__


Grid Search in R and Python
---------------------------

.. tabs::
   .. group-tab:: R

    Grid search in R provides the following capabilities:

    -  ``H2OGrid class``: Represents the results of the grid search
    -  ``h2o.getGrid(<grid_id>, sort_by, decreasing)``: Displays the
       specified grid
    -  ``h2o.grid()``: Starts a new grid search parameterized by

       -  model builder name (e.g., ``gbm``)
       -  model parameters (e.g., ``ntrees = 100``)
       -  ``hyper_parameters`` attribute for passing a list of hyper
          parameters (e.g., ``list(ntrees = c(1,100), learn_rate = c(0.1, 0.001))``)
       -  ``search_criteria`` optional attribute for specifying a more
          advanced search strategy  
       -  ``parallelism`` The number of models to build in parallel. 
          Parallelism allows the leader node to search the hyperspace and build models in a parallel way, which ultimately speeds up grid search on small data. A value of 1 (default) specifies sequential building. Specify 0 for adaptive parallelism, which is decided by H2O. Any number >1 sets the exact number of models built in parallel.

    More about ``search_criteria``:  

    This is a named list of control parameters for smarter hyperparameter search.  The list can include values for: ``strategy``, ``max_models``, ``max_runtime_secs``, ``stopping_metric``, ``stopping_tolerance``, ``stopping_rounds`` and ``seed``. The default value for ``strategy``, "Cartesian", covers the entire space of hyperparameter combinations.  If you want to use cartesian grid search, you can leave the ``search_criteria`` argument unspecified.  Specify the "RandomDiscrete" strategy to perform a random search of all the combinations of your hyperparameters. RandomDiscrete should be usually combined with at least one early stopping criterion, ``max_models`` and/or ``max_runtime_secs``.
    You can also use "Sequential", which goes through the specified parameters in sequence and requires the specified parameter lists to have the same length. "Sequential" strategy exposes ``early_stopping`` parameter (defaults to TRUE) that can be used to disable early stopping while still obeying the ``max_models`` and ``max_runtime_secs``.
    Some examples below:

    .. code-block:: r 

        list(strategy = "RandomDiscrete", max_models = 10, seed = 1)
        list(strategy = "RandomDiscrete", max_runtime_secs = 3600)
        list(strategy = "RandomDiscrete", max_models = 42, max_runtime_secs = 28800)
        list(strategy = "RandomDiscrete", stopping_tolerance = 0.001, stopping_rounds = 10)
        list(strategy = "RandomDiscrete", stopping_metric = "misclassification", stopping_tolerance = 0.0005, stopping_rounds = 5)

        list(strategy = "Sequential", max_runtime_secs = 3600)
        list(strategy = "Sequential", max_models = 42, max_runtime_secs = 28800)
        list(strategy = "Sequential", stopping_tolerance = 0.001, stopping_rounds = 10)
        list(strategy = "Sequential", early_stopping = FALSE)
        list(strategy = "Sequential", early_stopping = FALSE, max_models = 42, max_runtime_secs = 28800)
   .. group-tab:: Python

    -  Class is ``H2OGridSearch``
    -  ``<grid_name>.show()``: Display a list of models (including model
       IDs, hyperparameters, and MSE) explored by grid search (where
       ``<grid_name>`` is an instance of an ``H2OGridSearch`` class)
    -  ``grid_search = H2OGridSearch(<model_type), hyper_params=hyper_parameters)``:
       Start a new grid search parameterized by:

       -  ``model_type`` is the type of H2O estimator model with its
          unchanged parameters
       -  ``hyper_params`` in Python is a dictionary of string parameters
          (keys) and a list of values to be explored by grid search (values)
          (e.g., ``{'ntrees':[1,100], 'learn_rate':[0.1, 0.001]}``
       -  ``search_criteria`` is the optional dictionary for specifying more a
          advanced search strategy
       -  ``parallelism`` The number of models to build in parallel.     
          Parallelism allows the leaer node to search the hyperspace and build models in a parallel way, which ultimately speeds up grid search on small data. A value of 1 (default) specifies sequential building. Specify 0 for adaptive parallelism, which is decided by H2O. Any number >1 sets the exact number of models built in parallel.


    More about ``search_criteria``:  

    This is a dictionary of control parameters for smarter hyperparameter search.  The dictionary can include values for: ``strategy``, ``max_models``, ``max_runtime_secs``, ``stopping_metric``, ``stopping_tolerance``, ``stopping_rounds`` and ``seed``. The default value for ``strategy``, "Cartesian", covers the entire space of hyperparameter combinations.  If you want to use cartesian grid search, you can leave the ``search_criteria`` argument unspecified.  Specify the "RandomDiscrete" strategy to perform a random search of all the combinations of your hyperparameters. RandomDiscrete should be usually combined with at least one early stopping criterion, ``max_models`` and/or ``max_runtime_secs``.
    You can also use "Sequential", which goes through the specified parameters in sequence and requires the specified parameter lists to have the same length. "Sequential" strategy exposes ``early_stopping`` parameter (defaults to True) that can be used to disable early stopping while still obeying the ``max_models`` and ``max_runtime_secs``.
    Some examples below:

    .. code-block:: python

        {'strategy': "RandomDiscrete", 'max_models': 10, 'seed': 1}
        {'strategy': "RandomDiscrete", 'max_runtime_secs': 3600}
        {'strategy': "RandomDiscrete", 'max_models': 42, 'max_runtime_secs': 28800}
        {'strategy': "RandomDiscrete", 'stopping_tolerance': 0.001, 'stopping_rounds': 10}
        {'strategy': "RandomDiscrete", 'stopping_metric': "misclassification", 'stopping_tolerance': 0.0005, 'stopping_rounds': 5}

        {'strategy': "Sequential", 'max_runtime_secs': 3600}
        {'strategy': "Sequential", 'max_models': 42, 'max_runtime_secs': 28800}
        {'strategy': "Sequential", 'stopping_tolerance': 0.001, 'stopping_rounds': 10}
        {'strategy': "Sequential", 'early_stopping': False}
        {'strategy': "Sequential", 'early_stopping': False, 'max_models': 42, 'max_runtime_secs': 28800}


Grid Search Examples
~~~~~~~~~~~~~~~~~~~~

.. tabs::
   .. code-tab:: r R

    library(h2o)

    h2o.init()

    # Import a sample binary outcome dataset into H2O
    data <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_10k.csv")
    test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(data), y)

    # For binary classification, response should be a factor
    data[, y] <- as.factor(data[, y])
    test[, y] <- as.factor(test[, y])

    # Split data into train & validation
    ss <- h2o.splitFrame(data, seed = 1)
    train <- ss[[1]]
    valid <- ss[[2]]

    # GBM hyperparameters
    gbm_params1 <- list(learn_rate = c(0.01, 0.1),
                        max_depth = c(3, 5, 9),
                        sample_rate = c(0.8, 1.0),
                        col_sample_rate = c(0.2, 0.5, 1.0))

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 <- h2o.grid("gbm", x = x, y = y,
                          grid_id = "gbm_grid1",
                          training_frame = train,
                          validation_frame = valid,
                          ntrees = 100,
                          seed = 1,
                          hyper_params = gbm_params1)

    # Get the grid results, sorted by validation AUC
    gbm_gridperf1 <- h2o.getGrid(grid_id = "gbm_grid1", 
                                 sort_by = "auc", 
                                 decreasing = TRUE)
    print(gbm_gridperf1)

    # Grab the top GBM model, chosen by validation AUC
    best_gbm1 <- h2o.getModel(gbm_gridperf1@model_ids[[1]])

    # Now let's evaluate the model performance on a test set
    # so we get an honest estimate of top model performance
    best_gbm_perf1 <- h2o.performance(model = best_gbm1, 
                                      newdata = test)
    h2o.auc(best_gbm_perf1)
    # 0.7781779

    # Look at the hyperparameters for the best model
    print(best_gbm1@model[["model_summary"]])

   .. code-tab:: python

    import h2o
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    from h2o.grid.grid_search import H2OGridSearch

    h2o.init()

    # Import a sample binary outcome dataset into H2O
    data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_10k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    x = data.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    data[y] = data[y].asfactor()
    test[y] = test[y].asfactor()

    # Split data into train & validation
    ss = data.split_frame(seed = 1)
    train = ss[0]
    valid = ss[1]

    # GBM hyperparameters
    gbm_params1 = {'learn_rate': [0.01, 0.1], 
                    'max_depth': [3, 5, 9],
                    'sample_rate': [0.8, 1.0],
                    'col_sample_rate': [0.2, 0.5, 1.0]}

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator,
                              grid_id='gbm_grid1',
                              hyper_params=gbm_params1)
    gbm_grid1.train(x=x, y=y, 
                    training_frame=train, 
                    validation_frame=valid, 
                    ntrees=100,
                    seed=1)

    # Get the grid results, sorted by validation AUC
    gbm_gridperf1 = gbm_grid1.get_grid(sort_by='auc', decreasing=True)
    gbm_gridperf1

    # Grab the top GBM model, chosen by validation AUC
    best_gbm1 = gbm_gridperf1.models[0]

    # Now let's evaluate the model performance on a test set
    # so we get an honest estimate of top model performance
    best_gbm_perf1 = best_gbm1.model_performance(test)

    best_gbm_perf1.auc()
    # 0.7781778619721595


Random Grid Search Examples
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. tabs::
   .. code-tab:: r R

    # Use same data as previous example

    # GBM hyperparameters (bigger grid than above)
    gbm_params2 <- list(learn_rate = seq(0.01, 0.1, 0.01),
                        max_depth = seq(2, 10, 1),
                        sample_rate = seq(0.5, 1.0, 0.1),
                        col_sample_rate = seq(0.1, 1.0, 0.1))
    search_criteria <- list(strategy = "RandomDiscrete", max_models = 36, seed = 1)

    # Train and validate a random grid of GBMs
    gbm_grid2 <- h2o.grid("gbm", x = x, y = y,
                          grid_id = "gbm_grid2",
                          training_frame = train,
                          validation_frame = valid,
                          ntrees = 100,
                          seed = 1,
                          hyper_params = gbm_params2,
                          search_criteria = search_criteria)

    gbm_gridperf2 <- h2o.getGrid(grid_id = "gbm_grid2", 
                                 sort_by = "auc", 
                                 decreasing = TRUE)
    print(gbm_gridperf2)

    # Grab the top GBM model, chosen by validation AUC
    best_gbm2 <- h2o.getModel(gbm_gridperf2@model_ids[[1]])

    # Now let's evaluate the model performance on a test set
    # so we get an honest estimate of top model performance
    best_gbm_perf2 <- h2o.performance(model = best_gbm2, 
                                      newdata = test)
    h2o.auc(best_gbm_perf2)
    # 0.7810757

    # Look at the hyperparameters for the best model
    print(best_gbm2@model[["model_summary"]])


    For more information, refer to the `R grid search tutorial <https://github.com/h2oai/h2o-tutorials/blob/master/h2o-open-tour-2016/chicago/grid-search-model-selection.R>`__, `R grid search code <https://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/grid.R>`__, and `runit\_GBMGrid\_airlines.R <https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/gbm/runit_GBMGrid_airlines.R>`__.


   .. code-tab:: python

    # Use same data as previous example

    # GBM hyperparameters
    gbm_params2 = {'learn_rate': [i * 0.01 for i in range(1, 11)],  
                    'max_depth': list(range(2, 11)),
                    'sample_rate': [i * 0.1 for i in range(5, 11)], 
                    'col_sample_rate': [i * 0.1 for i in range(1, 11)]}

    # Search criteria
    search_criteria = {'strategy': 'RandomDiscrete', 'max_models': 36, 'seed': 1} 

    # Train and validate a random grid of GBMs
    gbm_grid2 = H2OGridSearch(model=H2OGradientBoostingEstimator,
                              grid_id='gbm_grid2',
                              hyper_params=gbm_params2,
                              search_criteria=search_criteria)
    gbm_grid2.train(x=x, y=y, 
                    training_frame=train, 
                    validation_frame=valid, 
                    ntrees=100,
                    seed=1)

    # Get the grid results, sorted by validation AUC
    gbm_gridperf2 = gbm_grid2.get_grid(sort_by='auc', decreasing=True)
    gbm_gridperf2

    # Grab the top GBM model, chosen by validation AUC
    best_gbm2 = gbm_gridperf2.models[0]

    # Now let's evaluate the model performance on a test set
    # so we get an honest estimate of top model performance
    best_gbm_perf2 = best_gbm2.model_performance(test)

    best_gbm_perf2.auc()
    # 0.7810757307013204


For more information, refer to the `Python grid search tutorial <https://github.com/h2oai/h2o-tutorials/blob/master/h2o-open-tour-2016/chicago/grid-search-model-selection.ipynb>`__, `Python grid search code <https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/grid/grid_search.py>`__, and `pyunit\_benign\_glm\_grid.py <https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/glm/pyunit_benign_glm_grid.py>`__.


Saving and Loading a Grid Search
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O supports saving and loading grids even after a cluster wipe or complete cluster restart. The ``save_grid`` function will export a grid and its models into a given folder while the ``load_grid`` function loads a previously saved grid and all its models from the given folder.

There are two modes to save a grid (in both R and Python):

- Use auto-checkpointing and supply the ``export_checkpoints_dir`` parameter
- Call the function ``h2o.save_grid`` for manual export

Checkpointing Example
'''''''''''''''''''''

Using the `Grid Search <#grid-search-examples>`__ example through the hyperparameters section, run the following additional commands to retrieve the checkpointed saved grid.

.. tabs::
  .. code-tab:: r R

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 <- h2o.grid("gbm", x = x, 
                          y = y, grid_id = "gbm_grid_test", 
                          training_frame = train, 
                          validation_frame = valid, 
                          ntrees = 100, seed = 1, 
                          hyper_params = gbm_params1, 
                          export_checkpoints_dir = tempdir())

    # Identify the grid_id and model_ids
    grid_id <- gbm_grid1@grid_id
    gbm_grid_model_count <- length(gbm_grid1@model_ids)

    # Wipe the cloud to simulate cluster restart 
    #(the models will no longer be available)
    h2o.removeAll()

    # Retrieve the saved grid
    grid <- h2o.loadGrid(paste0(tempdir(), "/", grid_id))
    grid


  .. code-tab:: python

    # Add the save location
    import tempfile
    checkpoints_dir = tempfile.mkdtemp()

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator, 
                              grid_id='gbm_grid', 
                              hyper_params=gbm_params1, 
                              export_checkpoints_dir=checkpoints_dir)
    gbm_grid1.train(x=x, y=y, 
                    training_frame=train, 
                    validation_frame=valid, 
                    ntrees=100, 
                    seed=1)

    # Identify the grid_id and model_ids
    grid_id = gbm_grid1.grid_id
    old_grid_model_count = len(gbm_grid1.model_ids)

    # Wipe the cloud to simulate cluster restart 
    #(the models will no longer be available)
    h2o.remove_all()

    # Retrieve the saved grid
    grid = h2o.load_grid(checkpoints_dir + "/" + grid_id)
    grid


Manual Export Example
'''''''''''''''''''''

Using the `Grid Search <#grid-search-examples>`__ example through the hyperparameters section, run the following additional commands to retrieve the manually exported saved grid.

.. tabs::
  .. code-tab:: r R

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 <- h2o.grid("gbm", x = x, 
                          y = y, grid_id = "gbm_grid1", 
                          training_frame = train, 
                          validation_frame = valid, 
                          ntrees = 100, seed = 1, 
                          hyper_params = gbm_params1)

    # Identify the grid_id and model_ids
    grid_id <- gbm_grid1@grid_id
    gbm_grid1_model_count <- length(gbm_grid1@model_ids)

    # Save the grid
    saved_path <- h2o.saveGrid(grid_directory = tempdir(), grid_id = grid_id)

    # Wipe the cloud to simulate cluster restart 
    #(the models will no longer be available)
    h2o.removeAll()

    # Retrieve the saved grid
    grid <- h2o.loadGrid(saved_path)
    grid


  .. code-tab:: python

    # Add the save location
    import tempfile
    checkpoints_dir = tempfile.mkdtemp()

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator, 
                              grid_id='gbm_grid1', 
                              hyper_params=gbm_params1)
    gbm_grid1.train(x=x, y=y, 
                    training_frame=train, 
                    validation_frame=valid, 
                    ntrees=100, seed=1)

    # Identify the grid_id and model_ids
    grid_id = gbm_grid1.grid_id
    old_grid_model_count = len(gbm_grid1.model_ids)

    # Save the grid
    saved_path = h2o.save_grid(checkpoints_dir, grid_id)

    # Wipe the cloud to simulate cluster restart 
    #(the models will no longer be available)
    h2o.remove_all()

    # Retrieve the saved grid
    grid = h2o.load_grid(saved_path)
    grid


Fault-Tolerant Grid Search
~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O supports progress recovery should the cluster fail during grid training. The ``recovery_dir`` parameter will cause the grid to save all its inputs and outputs into the given directory, and should the training fail, the grid progress can be resumed from the last model that was successfully trained.

.. tabs::
  .. code-tab:: r R

    iris <- h2o.importFile(
        "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv", 
        destination_frame="iris"
    )
    hyper_parameters <- list(
        learn_rate=c(0.01, 0.02, 0.03, 0.04),
        ntrees=c(100, 120, 130, 140)
    )
    # train a cartesian grid of GBMs
    gbm_grid <- h2o.grid(
        "gbm", x=1:4, y=5, 
        grid_id="gbm_grid", training_frame=iris, 
        hyper_params=hyper_parameters,
        recovery_dir="hdfs://nameNode/user/john/gbm_grid_recovery"
    )
    
    # on a new cluster recover grid
    # this will load the training frame and any other objects required for training
    h2o.loadGrid(
        "hdfs://nameNode/user/john/gbm_grid_recovery/gbm_grid", # append grid ID to the recovery_dir 
        load_params_references=TRUE
    )
    iris <- h2o.getFrame("iris") # get reference to re-loaded training frame
    # continue grid training, same grid id will cause H2O to resume progress
    grid <- h2o.grid(
        "gbm", grid_id="gbm_grid", x=1:4, y=5,
        training_frame=iris, 
        hyper_params=hyper_parameters # use original hyper-parameters
    )

  .. code-tab:: python

    iris = h2o.import_file(
        "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv", 
        destination_frame="iris"
    )
    hyper_parameters = {
        "learn_rate": [0.01, 0.02, 0.03, 0.04],
        "ntrees": [100, 120, 130, 140]
    }
    
    # train a cartesian grid of GBMs
    gbm_grid = H2OGridSearch(
        model=H2OGradientBoostingEstimator,
        grid_id='gbm_grid', 
        hyper_params=hyper_parameters,
        recovery_dir="hdfs://nameNode/user/john/gbm_grid_recovery"
    )
    gbm_grid.train(x=list(range(4)), y=4, training_frame=iris)
    
    # on a new cluster recover grid
    # this will load the training frame and any other objects required for training
    grid = h2o.load_grid(
        "hdfs://nameNode/user/john/gbm_grid_recovery/gbm_grid",  # append grid ID to the recovery_dir 
        load_params_references=True
    )
    train = h2o.get_frame("iris") # get reference to re-loaded training frame
    grid.hyper_params = hyper_parameters # use original hyper-parameters
    # continue grid training
    grid.train(x=list(range(4)), y=4, training_frame=train)



Grid Search Java API
--------------------

Each parameter exposed by the schema can specify if it is supported by
grid search by including the attribute ``gridable=true`` in the schema
@API annotation. In any case, the Java API does not restrict the
parameters supported by grid search.

There are two core entities: ``Grid`` and ``GridSearch``. ``GridSeach``
is a job-building ``Grid`` object and is defined by the user's model
factory and the `hyperspace walk
strategy <https://en.wikipedia.org/wiki/Hyperparameter_optimization>`__.
The model factory must be defined for each supported model type (DRF,
GBM, DL, and K-means). The hyperspace walk strategy specifies how the
user-defined space of hyperparameters is traversed. The space
definition is not limited. For each point in hyperspace, model
parameters of the specified type are produced.

The implementation supports a simple cartesian grid search as well as
random search with several different stopping criteria. Grid build
triggers a new model builder job for each hyperspace point returned by
the walk strategy. If the model builder job fails, the resulting model
is ignored; however, it can still be tracked in the job list, and errors
are returned in the grid build result.

Model builder jobs are run serially in sequential order. More advanced
job scheduling schemes are under development. Note that in cases of true
big data, sequential scheduling will yield the highest performance. It is
only with a large cluster and small data that concurrent scheduling will
improve performance.

The grid object contains the results of the grid search: a list of model
keys produced by the grid search as well as any errors, and a table of
metrics for each succesful model. The grid object publishes a simple API
to get the models.

Launch the grid search by specifying:

-  the common model hyperparameters (parameter values that will be
   common across all models in the search)
-  the search hyperparameters (a map ``<parameterName, listOfValues>``
   that defines the parameter spaces to traverse)
-  optionally, search criteria (an instance of
   ``HyperSpaceSearchCriteria``)

The Java API can grid search any parameters defined in the model
parameter's class (e.g., ``GBMParameters``). Paramters that are
appropriate for gridding are marked by the @API parameter, but this is
not enforced by the framework.

Additional methods are available in the model builder to support
creation of model parameters and configuration. This eliminates the
requirement of the previous implementation where each gridable value was
represented as a ``double``. This also allows users to specify different
building strategies for model parameters. For example, the REST layer
uses a builder that validates parameters against the model parameter's
schema, where the Java API uses a simple reflective builder. Additional
reflections support is provided by PojoUtils (methods ``setField``,
``getFieldValue``).

Example
~~~~~~~

.. code:: java

    HashMap<String, Object[]> hyperParms = new HashMap<>();
    hyperParms.put("_ntrees", new Integer[]{1, 2});
    hyperParms.put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
    hyperParms.put("_max_depth", new Integer[]{1, 2, 5});
    hyperParms.put("_learn_rate", new Float[]{0.01f, 0.1f, 0.3f});

    // Setup common model parameters
    GBMModel.GBMParameters params = new GBMModel.GBMParameters();
    params._train = fr._key;
    params._response_column = "cylinders";
    // Trigger new grid search job, block for results and get the resulting grid object
    GridSearch gs =
     GridSearch.startGridSearch(params, hyperParms, GBM_MODEL_FACTORY, new HyperSpaceSearchCriteria.CartesianSearchCriteria());
    Grid grid = (Grid) gs.get();

Exposing grid search end-point for a new algorithm
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In the following example, the PCA algorithm has been implemented, and we
would like to expose the algorithm via REST API. The following aspects
are assumed:

-  The PCA model builder is called ``PCA``
-  The PCA parameters are defined in a class called ``PCAParameters``
-  The PCA parameters schema is called ``PCAParametersV3``

To add support for PCA grid search:

1. Add the PCA model build factory into the ``hex.grid.ModelFactories``
   class:

  ::

	class ModelFactories {
	 /* ... */
	 public static ModelFactory<PCAModel.PCAParameters>
	   PCA_MODEL_FACTORY =
	   new ModelFactory<PCAModel.PCAParametners>() {
	     @Override
	     public String getModelName() {
	       return "PCA";
	     }
	     @Override
	     public ModelBuilder buildModel(PCAModel.PCAParameters params) {
	       return new PCA(params);
	     }
	  };
	}

2. Add the PCA REST end-point schema:

  ::

	public class PCAGridSearchV99 extends GridSearchSchema<PCAGridSearchHandler.PCAGrid,
	 PCAGridSearchV99,
	 PCAModel.PCAParameters,
	 PCAV3.PCAParametersV3> {
	}

3. Add the PCA REST end-point handler:

   ::

    public class PCAGridSearchHandler
     extends GridSearchHandler<PCAGridSearchHandler.PCAGrid,
     PCAGridSearchV99,
     PCAModel.PCAParameters,
     PCAV3.PCAParametersV3> {

       public PCAGridSearchV99 train(int version, PCAGridSearchV99 gridSearchSchema) {
         return super.do_train(version, gridSearchSchema);
       }

       @Override
       protected ModelFactory<PCAModel.PCAParameters> getModelFactory() {
         return ModelFactories.PCA_MODEL_FACTORY;
       }

       @Deprecated
       public static class PCAGrid extends Grid<PCAModel.PCAParameters> {

         public PCAGrid() {
           super(null, null, null, null);
         }
       }
    }

4. Register the REST end-point in the register factory
   ``hex.api.Register``:

  ::

    public class Register extends AbstractRegister {
      @Override
      public void register() {
        // ...
        H2O.registerPOST("/99/Grid/pca", PCAGridSearchHandler.class, "train", "Run grid search for PCA model.");
        // ...
      }
    }


REST API
--------

The current implementation of the grid search REST API exposes the
following endpoints:

-  ``GET /<version>/Grids``: List available grids, with optional
   parameters to sort the list by model metric such as MSE
-  ``GET /<version>/Grids/<grid_id>``: Return specified grid
-  ``POST /<version>/Grids/<algo_name>``: Start a new grid search

   -  ``<algo_name>``: Supported algorithm values are
      ``{glm, gbm, drf, kmeans, deeplearning}``

Endpoints accept model-specific parameters (e.g.,
`GBMParametersV3 <https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/schemas/GBMV3.java>`__)
and an additional parameter called ``hyper_parameters``, which contains a
dictionary of the hyperparameters that will be searched. In this
dictionary, an array of values is specified for each searched
hyperparameter.

.. code:: java

    {
      "ntrees":[1,5],
      "learn_rate":[0.1,0.01]
    }

An optional ``search_criteria`` dictionary specifies options for
controlling more advanced search strategies. Currently, full
``Cartesian`` is the default. ``RandomDiscrete`` allows a random search
over the hyperparameter space with three ways of specifying when to
stop the search: max number of models, max time, and metric-based early
stopping (e.g., stop if MSE hasn't improved by 0.0001 over the 5 best
models). An example is:

.. code:: java

    {
      "strategy": "RandomDiscrete",
      "max_runtime_secs": 600,
      "max_models": 100,
      "stopping_metric": "AUTO",
      "stopping_tolerance": 0.00001,
      "stopping_rounds": 5,
      "seed": 123456
    }

With grid search, each model is built sequentially, allowing users to
view each model as it is built.

Example
~~~~~~~

Invoke a new GBM model grid search by POSTing the following request to
``/99/Grid/gbm``:

:: 

    parms:{hyper_parameters={"ntrees":[1,5],"learn_rate":[0.1,0.01]}, training_frame="filefd41fe7ac0b_csv_1.hex_2", grid_id="gbm_grid_search", response_column="Species"", ignored_columns=[""]}


Supported Grid Search Hyperparameters
-------------------------------------

The following hyperparameters are supported by grid search.

Supervised Algorithms
~~~~~~~~~~~~~~~~~~~~~

AutoML Hyperparameters
''''''''''''''''''''''

No available hyperparameters.

CoxPH Hyperparameters
'''''''''''''''''''''

- ``use_all_factor_levels``

Deep Learning Hyperparameters
'''''''''''''''''''''''''''''

- ``activation`` 
- ``average_activation`` 
- ``adaptive_rate`` 
- ``balance_classes``
- ``class_sampling_factors``
- ``classification_stop`` 
- ``col_major`` 
- ``elastic_averaging`` 
- ``elastic_averaging_moving_rate`` 
- ``elastic_averaging_regularization`` 
- ``epochs`` 
- ``epsilon`` 
- ``fast_mode`` 
- ``force_load_balance`` 
- ``hidden`` 
- ``hidden_dropout_ratios`` 
- ``initial_biases`` 
- ``initial_weight_distribution`` 
- ``initial_weight_scale`` 
- ``initial_weights`` 
- ``input_dropout_ratio`` 
- ``l1`` 
- ``l2`` 
- ``loss`` 
- ``max_categorical_features`` 
- ``max_w2`` 
- ``missing_values_handling``
- ``momentum_ramp`` 
- ``momentum_stable`` 
- ``nesterov_accelerated_gradient`` 
- ``overwrite_with_best_model`` 
- ``quiet_mode`` 
- ``rate`` 
- ``rate_annealing`` 
- ``rate_decay`` 
- ``regression_stop`` 
- ``replicate_training_data`` 
- ``reproducible`` 
- ``rho`` 
- ``seed``
- ``score_duty_cycle`` 
- ``score_interval`` 
- ``score_training_samples`` 
- ``score_validation_samples`` 
- ``score_validation_sampling`` 
- ``shuffle_training_data`` 
- ``sparse`` 
- ``sparsity_beta``
- ``standardize``
- ``target_ratio_comm_to_comp`` 
- ``train_samples_per_iteration`` 
- ``variable_importances``

DRF Hyperparameters
'''''''''''''''''''

- ``balance_classes``
- ``class_sampling_factors``
- ``max_after_balance_size``
- ``seed``

GLM Hyperparameters
'''''''''''''''''''

- ``alpha``
- ``dispersion_learning_rate``
- ``init_dispersion_parameter``
- ``lambda``
- ``rand_family``
- ``rand_link``
- ``startval``
- ``theta``
- ``tweedie_variance_power``
- ``tweedie_link_power``

Isotonic Regression Hyperparameters
'''''''''''''''''''''''''''''''''''

No available hyperparameters.

ModelSelection Hyperparameters
''''''''''''''''''''''''''''''

- ``alpha``
- ``lambda``
- ``missing_values_handling``
- ``nparallelism``
- ``rand_family``
- ``seed``
- ``startval``
- ``tweedie_variance_power``

GAM Hyperparameters
'''''''''''''''''''

- ``alpha``
- ``balance_classes``
- ``bs``
- ``gam_columns``
- ``lambda``
- ``missing_values_handling``
- ``num_knots``
- ``rand_family``
- ``scale``
- ``seed``
- ``splines_non_negative``
- ``spline_order``
- ``startval``
- ``theta``
- ``tweedie_variance_power``

GBM Hyperparameters
'''''''''''''''''''

- ``balance_classes``
- ``class_sampling_factors``
- ``max_after_balance_size``
- ``seed``

Naïve Bayes Hyperparameters
'''''''''''''''''''''''''''

- ``compute_metrics``
- ``eps_prob``
- ``eps_sdev``
- ``laplace``
- ``min_prob``
- ``min_sdev``
- ``seed``

Rulefit Hyperparameters
'''''''''''''''''''''''

- ``seed``

Stacked Ensemble Hyperparameters
''''''''''''''''''''''''''''''''

- ``seed``

SVM Hyperparameters
'''''''''''''''''''

- ``gamma``
- ``hyper_param``
- ``rank_ratio``
- ``seed``

Uplift DRF Hyperparameters
''''''''''''''''''''''''''

- ``balance_classes``
- ``class_sampling_factors``
- ``max_after_balance_size``
- ``seed``

XGBoost Hyperparameters
'''''''''''''''''''''''

- ``backend``
- ``booster``
- ``colsample_bylevel``
- ``colsample_bynode``
- ``colsample_bytree``
- ``dmatrix_type``
- ``eta``
- ``gamma``
- ``grow_policy``
- ``max_bins``
- ``max_delta_step``
- ``max_leaves``
- ``min_child_weight``
- ``normalize_type``
- ``one_drop``
- ``rate_drop``
- ``reg_alpha``
- ``reg_lambda``
- ``sample_type``
- ``scale_pos_weight``
- ``seed``
- ``skip_drop``
- ``subsample``
- ``tree_method``

Unsupervised Hyperparameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Aggregator Hyperparameters
''''''''''''''''''''''''''

- ``k``
- ``max_iterations``
- ``pca_method``
- ``rel_tol_num_exemplars``
- ``target_num_exemplars``
- ``transform``
- ``use_all_factor_levels``



GLRM Hyperparameters
''''''''''''''''''''

- ``estimate_k``
- ``gamma_x``
- ``gamma_y``
- ``init``
- ``init_step_size``
- ``k``
- ``loss``
- ``loss_by_col``
- ``loss_by_col_idx``
- ``max_iterations``
- ``max_updates``
- ``min_step_size``
- ``multi_loss``
- ``period``
- ``regularization_x``
- ``regularization_y``
- ``seed``
- ``svd_method``
- ``transform``

PCA Hyperparameters
'''''''''''''''''''

- ``k``
- ``max_iterations``
- ``transform``

K-Means Hyperparameters
'''''''''''''''''''''''

- ``estimate_k``
- ``init``
- ``max_iterations``
- ``seed``
- ``standardize``

Isolation Forest Hyperparameters
''''''''''''''''''''''''''''''''

- ``seed``

Extended Isolation Forest Hyperparameters
'''''''''''''''''''''''''''''''''''''''''

- ``seed``

Shared Tree Hyperparameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. |yes| image:: /images/checkmark.png
   :scale: 3%
   :align: middle

.. |no| image:: /images/xmark.png
  :scale: 3%
  :align: middle

+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| Shared                              | GBM | DRF | XGBoost | Isolation | Extended  | Uplift |
| Tree                                |     |     |         | Forest    | Isolation | DRF    |
| Hyperparameters                     |     |     |         |           | Forest    |        |
+=====================================+=====+=====+=========+===========+===========+========+
| ``col_sample_rate``                 ||yes|| |no|| |yes|   | |no|      | |no|      | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``col_sample_rate_change_per_level``||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``col_sample_rate_per_tree``        ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``histogram_type``                  ||yes|||yes|| |yes|   | |yes|     | |no|      | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``learn_rate``                      ||yes|| |no|| |yes|   | |no|      | |no|      | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``max_abs_leafnode_pred``           ||yes|| |no|| |yes|   | |no|      | |no|      | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``max_depth``                       ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``min_rows``                        ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``min_split_improvement``           ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``mtries``                          | |no|||yes|| |no|    | |yes|     | |no|      | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``nbins``                           ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``nbins_cats``                      ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``nbins_top_level``                 ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``ntrees``                          ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``sample_rate``                     ||yes|||yes|| |yes|   | |yes|     | |no|      | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``sample_rate_per_class``           ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``sample_size``                     | |no|| |no|| |no|    | |yes|     | |yes|     | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``seed``                            ||yes|||yes|| |yes|   | |yes|     | |yes|     | |yes|  |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``learn_rate_annealing``            ||yes|| |no|| |no|    | |no|      | |no|      | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+
| ``pred_noise_bandwidth``            ||yes|| |no|| |no|    | |no|      | |no|      | |no|   |
+-------------------------------------+-----+-----+---------+-----------+-----------+--------+

Grid Testing
------------

The current test infrastructure includes:

**R Tests**

-  GBM grids using wine, airlines, and iris datasets verify the
   consistency of results
-  DL grid using the ``hidden`` parameter verifying the passing of
   structured parameters as a list of values
-  Minor R testing support verifying equality of the model's parameters
   against a given list of hyper parameters.

**JUnit Test**

-  Basic tests verifying consistency of the results for DRF, GBM, and
   KMeans
-  JUnit test assertions for grid results

There are tests for the ``RandomDiscrete`` search criteria in
`runit\_GBMGrid\_airlines.R <https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/gbm/runit_GBMGrid_airlines.R>`_
and
`pyunit\_benign\_glm\_grid.py <https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/glm/pyunit_benign_glm_grid.py>`_.

Caveats/In Progress
-------------------

-  Currently, the schema system requires specific classes instead of
   parameterized classes. For example, the schema definition
   ``Grid<GBMParameters>`` is not supported unless your define the class
   ``GBMGrid extends Grid<GBMParameters>``.
-  Grid Job scheduler is sequential only; schedulers for concurrent
   builds are under development. Note that in cases of true big data
   sequential scheduling will yield the highest performance. It is only
   with a large cluster and small data that concurrent scheduling will
   improve performance.
-  The model builder job and grid jobs are not associated.
-  There is no way to list the hyper space parameters that caused a
   model builder job failure.
- The ``h2o.get_grid()`` (Python) or ``h2o.getGrid()`` (R) function can be called to retrieve a grid search instance. If neither cross-validation nor a validation frame is used in the grid search, then the training metrics will display in the "get grid" output. If a validation frame is passed to the grid, and ``nfolds = 0``, then the validation metrics will display. However, if ``nfolds`` > 1, then cross-validation metrics will display even if a validation frame is provided.

Additional Documentation
------------------------

-  `H2O Core Java Developer Documentation <../h2o-core/javadoc/index.html>`_: The definitive Java API guide
   for the core components of H2O.

-  `H2O Algos Java Developer Documentation <../h2o-algos/javadoc/index.html>`_: The definitive Java API guide
   for the algorithms used by H2O.

-  `Hyperparameter Optimization in H2O <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/random%20hyperparmeter%20search%20and%20roadmap.md>`_: A guide to Grid Search and Random Search in H2O.
