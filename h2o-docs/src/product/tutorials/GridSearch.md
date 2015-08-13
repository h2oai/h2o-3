#Grid Search API

The current implementation of the grid search REST API exposes the following endpoints: 

- `/<version>/Grids`: List available grids
- `/<version>/Grids/<grid_id>`: Display specified grid
- `/<version>/Grids/<algo_name>`: Start a new grid search
	- `<algo_name>`: Supported algorithm values are `{gbm, drf, kmeans, deeplearning}`

Endpoints accept model-specific parameters (e.g., `GBMParametersV3`) and an additional parameter called `hyper_parameters`, which contains a JSON listing of the hyper parameters (e.g., `{"ntrees":[1,5],"learn_rate":[0.1,0.01]}`).

Each parameter exposed by the schema can specify if it is supported by grid search by specifying the attribute `gridable=true` in the schema @API annotation. In any case, the Java API does not restrict the parameters supported by grid search. 

##Example

Invoke a new GBM model grid search by passing the following request to H2O: 

```
Method: POST  , URI: /99/Grid/gbm, route: /99/Grid/gbm, parms:{hyper_parameters={"ntrees":[1,5],"learn_rate":[0.1,0.01]}, training_frame=filefd41fe7ac0b_csv_1.hex_2, grid_id=gbm_grid_search, response_column=Species, ignored_columns=[""]}
```

##Grid Search in R

Grid search in R provides the following capabilities: 

- `H2OGrid class`: Represents the results of the grid search
- `h2o.getGrid(<grid_id>)`: Display the specified grid
- `h2o.grid`: Start a new grid search parameterized by
	- model builder name (e.g., `gbm`)
	- model parameters (e.g., `ntrees=100`)
	- `hyper_parameters` attribute for passing a list of hyper parameters (e.g., `list(ntrees=c(1,100), learn_rate=c(0.1,0.001))`)

###Example

```
ntrees_opts = c(1, 5)
learn_rate_opts = c(0.1, 0.01)
hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
grid_models <- lapply(grid@model_ids, function(mid) {
    model = h2o.getModel(mid)
  })
```

##Grid Search Java API

There are two core entities: `Grid` and `GridSearch`. `GridSeach` is a job-building `Grid` object and is defined by the user's model factory and the [hyperspace walk strategy](ref???).  The model factory must be defined for each supported model type (DRF, GBM, DL, and K-means). The hyperspace walk strategy specifies how the user-defined space of hyper parameters is traversed. The space definition is not limited. For each point in hyperspace, model parameters of the specified type are produced. 

Currently, the implementation supports a simple cartesian grid search, but additional space traversal strategies are currently in development. This triggers a new model builder job for each hyperspace point returned by the walk strategy. If the model builder job fails, it is ignored; however, it can still be tracked in the job list. Model builder jobs are run serially in sequential order. More advanced job scheduling schemes are under development.

The grid object contains the results of the grid search: a list of model keys produced by the grid search. The grid object publishes a simple API to get the models. 

Launch the grid search by specifying: 

- the model parameters (provides a common setting used to create new models)
- the hyper parameters (a map `<parameterName, listOfValues>` that defines the parameter spaces to traverse)

The Java API can grid search any parameters defined in the model parameter's class (e.g., `GBMParameters`). Paramters that are appropriate for gridding are marked by the @API parameter, but this is not enforced by the framework. 

Additional parameters are available in the model builder to support creation of model parameters and configuration. This eliminates the requirement of the previous implementation where each gridable value was represented as a `double`. This also allows users to specify different building strategies for model parameters. For example, a REST layer uses a builder that validates parameters against the model parameter's schema, where the Java API uses a simple reflective builder. Additional reflections support is provided by PojoUtils (methods `setField`, `getFieldValue`). 

###Example

```
HashMap<String, Object[]> hyperParms = new HashMap<>();
hyperParms.put("_ntrees", new Integer[]{1, 2});
hyperParms.put("_distribution", new Distribution.Family[]{Distribution.Family.multinomial});
hyperParms.put("_max_depth", new Integer[]{1, 2, 5});
hyperParms.put("_learn_rate", new Float[]{0.01f, 0.1f, 0.3f});

// Setup common model parameters
GBMModel.GBMParameters params = new GBMModel.GBMParameters();
params._train = fr._key;
params._response_column = "cylinders";
// Trigger new grid search job, block for results and get the resulting grid object
GridSearch gs = GridSearch.startGridSearch(params, hyperParms, GBM_MODEL_FACTORY);
Grid grid = (Grid) gs.get();
```

##Grid Testing

This feature is tested with the intention of fixing semantics of the grid API. The current test infrastructure includes: 

**R Tests**

- GBM grids using wine, airlines, and iris datasets verify the consistency of results
- DL grid using the `hidden` parameter verifying the passing of structured parameters as a list of values
- Minor R testing support verifying equality of the model's parameters against  a given list of hyper parameters. 

**JUnit Test**

- Basic tests verifying consistency of the results for DRF, GBM, and KMeans
- JUnit test assertions for grid results


##Caveats/In Progress

- Currently, the schema system requires specific classes instead of parameterized classes. For example, the schema definition `Grid<GBMParameters>` is not supported unless your define the class `GBMGrid extends Grid<GBMParameters>`. 
- Grid Job scheduler is sequential only; schedulers for concurrent builds are under development. 
- The model builder job and grid jobs are not associated. 
- There is no way to list the hyper space parameters that caused a model builder job failure. 
- There is no model query interface (i.e., display the best model for the specified criterion). 


##Documentation

- <a href="http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-core/javadoc/index.html" target="_blank">H2O Core Java Developer Documentation</a>: The definitive Java API guide for the core components of H2O. 

- <a href="http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-algos/javadoc/index.html" target="_blank">H2O Algos Java Developer Documentation</a>: The definitive Java API guide for the algorithms used by H2O. 


