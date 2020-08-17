# Grid Search (Hyperparameter Search) API

>**Note**: This topic is no longer being maintained. Refer to [Grid Search](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/grid-search.rst) for the most up-to-date documentation.

## REST

The current implementation of the grid search REST API exposes the following endpoints:

- `GET /<version>/Grids`: List available grids, with optional parameters to sort the list by model metric such as MSE
- `GET /<version>/Grids/<grid_id>`: Return specified grid
- `POST /<version>/Grid/<algo_name>`: Start a new grid search
	- `<algo_name>`: Supported algorithm values are `{glm, gbm, drf, kmeans, deeplearning}`

Endpoints accept model-specific parameters (e.g., [GBMParametersV3](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/schemas/GBMV3.java) and an additional parameter called `hyper_parameters` which contains a dictionary of the hyper parameters which will be searched. In this dictionary an array of values is specified for each searched hyperparameter.

```json
{
  "ntrees":[1,5],
  "learn_rate":[0.1,0.01]
}
```

An optional `search_criteria` dictionary specifies options for controlling more advanced search strategies.  Currently, full `Cartesian` is the default.  `RandomDiscrete` allows a random search over the hyperparameter space, with three ways of specifying when to stop the search: max number of models, max time, and metric-based early stopping (e.g., stop if MSE hasn't improved by 0.0001 over the 5 best models). An example is:

```json
{
  "strategy": "RandomDiscrete",
  "max_runtime_secs": 600,
  "max_models": 100,
  "stopping_metric": "AUTO",
  "stopping_tolerance": 0.00001,
  "stopping_rounds": 5,
  "seed": 123456
}
```

With grid search, each model is built sequentially, allowing users to view each model as it is built.

## Supported Grid Search Hyperparameters
The following hyperparameters are supported by grid search.

### Common Hyperparameters Supported by Grid Search

- `validation_frame`
- `response_column`
- `weights_column`
- `offset_column`
- `fold_column`
- `fold_assignment`
- `stopping_rounds`
- `max_runtime_secs`
- `stopping_metric`
- `stopping_tolerance`

### Shared Tree Hyperparameters Supported by Grid Search

>***Note***: The Shared Tree hyperparameters apply to DRF and GBM.

- `balance_classes`
- `class_sampling_factors`
- `max_after_balance_size`
- `ntrees`
- `max_depth`
- `min_rows`
- `nbins`
- `nbins_top_level`
- `nbins_cats`
- `r2_stopping`
- `seed`
- `build_tree_one_node`
- `sample_rate`
- `sample_rate_per_class`
- `col_sample_rate_per_tree`
- `col_sample_rate_change_per_level`
- `score_tree_interval`
- `min_split_improvement`
- `histogram_type`

### DRF Hyperparameters Supported by Grid Search

- `mtries`

### GBM Hyperparameters Supported by Grid Search

- `learn_rate`
- `learn_rate_annealing`
- `distribution`
- `quantile_alpha`
- `tweedie_power`
- `col_sample_rate`
- `max_abs_leafnode_pred`

### K-Means Hyperparameters Supported by Grid Search

- `max_iterations`
- `standardize`
- `seed`
- `init`

### GLM Hyperparameters Supported by Grid Search

- `transform`
- `k`
- `loss`
- `multi_loss`
- `loss_by_col`
- `period`
- `regularization_x`
- `regularization_y`
- `gamma_x`
- `gamma_y`
- `max_iterations`
- `max_updates`
- `missing_values_handling`
- `init_step_size`
- `min_step_size`
- `seed`
- `init`
- `svd_method`

### Naïve Bayes Hyperparameters Supported by Grid Search

- `laplace`
- `min_sdev`
- `eps_sdev`
- `min_prob`
- `eps_prob`
- `compute_metrics`
- `seed`

### PCA Hyperparameters Supported by Grid Search

- `transform`
- `k`
- `max_iterations`

### Deep Learning Hyperparameters Supported by Grid Search

- `balance_classes`
- `class_sampling_factors`
- `max_after_balance_size`
- `max_confusion_matrix_size`
- `overwrite_with_best_model`
- `use_all_factor_levels`
- `standardize`
- `activation`
- `hidden`
- `epochs`
- `train_samples_per_iteration`
- `target_ratio_comm_to_comp`
- `seed`
- `adaptive_rate`
- `rho`
- `epsilon`
- `rate`
- `rate_annealing`
- `rate_decay`
- `momentum_start`
- `momentum_ramp`
- `momentum_stable`
- `nesterov_accelerated_gradient`
- `input_dropout_ratio`
- `hidden_dropout_ratios`
- `l1`
- `l2`
- `max_w2`
- `initial_weight_distribution`
- `initial_weight_scale`
- `initial_weights`
- `initial_biases`
- `loss`
- `distribution`
- `tweedie_power`
- `quantile_alpha`
- `score_interval`
- `score_training_samples`
- `score_validation_samples`
- `score_duty_cycle`
- `classification_stop`
- `regression_stop`
- `quiet_mode`
- `score_validation_sampling`
- `variable_importances`
- `fast_mode`
- `force_load_balance`
- `replicate_training_data`
- `single_node_mode`
- `shuffle_training_data`
- `missing_values_handling`
- `sparse`
- `col_major`
- `average_activation`
- `sparsity_beta`
- `max_categorical_features`
- `reproducible`
- `elastic_averaging`
- `elastic_averaging_moving_rate`
- `elastic_averaging_regularization`

### Aggregator Hyperparameters Supported by Grid Search

- `radius_scale`
- `transform`
- `pca_method`
- `k`
- `max_iterations`

## Example

Invoke a new GBM model grid search by POSTing the following request to `/99/Grid/gbm`:

```json
parms:{hyper_parameters={"ntrees":[1,5],"learn_rate":[0.1,0.01]}, training_frame="filefd41fe7ac0b_csv_1.hex_2", grid_id="gbm_grid_search", response_column="Species"", ignored_columns=[""]}
```

## Grid Search in R

Grid search in R provides the following capabilities:

- `H2OGrid class`: Represents the results of the grid search
- `h2o.getGrid(<grid_id>, sort_by, decreasing)`: Display the specified grid
- `h2o.grid`: Start a new grid search parameterized by
	- model builder name (e.g., `gbm`)
	- model parameters (e.g., `ntrees=100`)
	- `hyper_parameters` attribute for passing a list of hyper parameters (e.g., `list(ntrees=c(1,100), learn_rate=c(0.1,0.001))`)
	- `search_criteria` optional attribute for specifying more a advanced search strategy

### Example

```r
ntrees_opts = c(1, 5)
learn_rate_opts = c(0.1, 0.01)
hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
grid_models <- lapply(grid@model_ids, function(mid) {
    model = h2o.getModel(mid)
  })
```

### Random Hyper-Parameter Grid Search Example

```r
# The following two commands remove any previously installed H2O packages for R.
if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }

# Next, we download packages that H2O depends on.
pkgs <- c("methods","statmod","stats","graphics","RCurl","jsonlite","tools","utils")
for (pkg in pkgs) {
  if (! (pkg %in% rownames(installed.packages()))) { install.packages(pkg) }
}

# Now we download, install and initialize the H2O package for R.
install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/rel-tukey/7/R")))


library(h2o)
h2o.init(nthreads=-1)
train <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/arrhythmia.csv.gz")
dim(train)
response <- 1
predictors <- c(2:ncol(train))

splits<-h2o.splitFrame(train, 0.9, destination_frames = c("trainSplit","validSplit"), seed = 123456)
trainSplit <- splits[[1]]
validSplit <- splits[[2]]


## Hyper-Parameter Search

## Construct a large Cartesian hyper-parameter space
ntrees_opts <- c(10000) ## early stopping will stop earlier
max_depth_opts <- seq(1,20)
min_rows_opts <- c(1,5,10,20,50,100)
learn_rate_opts <- seq(0.001,0.01,0.001)
sample_rate_opts <- seq(0.3,1,0.05)
col_sample_rate_opts <- seq(0.3,1,0.05)
col_sample_rate_per_tree_opts = seq(0.3,1,0.05)
#nbins_cats_opts = seq(100,10000,100) ## no categorical features in this dataset

hyper_params = list( ntrees = ntrees_opts,
                     max_depth = max_depth_opts,
                     min_rows = min_rows_opts,
                     learn_rate = learn_rate_opts,
                     sample_rate = sample_rate_opts,
                     col_sample_rate = col_sample_rate_opts,
                     col_sample_rate_per_tree = col_sample_rate_per_tree_opts
                     #,nbins_cats = nbins_cats_opts
)


## Search a random subset of these hyper-parmameters (max runtime and max models are enforced, and the search will stop after we don't improve much over the best 5 random models)
search_criteria = list(strategy = "RandomDiscrete", max_runtime_secs = 600, max_models = 100, stopping_metric = "AUTO", stopping_tolerance = 0.00001, stopping_rounds = 5, seed = 123456)

gbm.grid <- h2o.grid("gbm",
                     grid_id = "mygrid",
                     x = predictors,
                     y = response,

                     # faster to use a 80/20 split
                     training_frame = trainSplit,
                     validation_frame = validSplit,
                     nfolds = 0,

                     # alternatively, use N-fold cross-validation
                     #training_frame = train,
                     #nfolds = 5,

                     distribution="gaussian", ## best for MSE loss, but can try other distributions ("laplace", "quantile")

                     ## stop as soon as mse doesn't improve by more than 0.1% on the validation set,
                     ## for 2 consecutive scoring events
                     stopping_rounds = 2,
                     stopping_tolerance = 1e-3,
                     stopping_metric = "MSE",

                     score_tree_interval = 100, ## how often to score (affects early stopping)
                     seed = 123456, ## seed to control the sampling of the Cartesian hyper-parameter space
                     hyper_params = hyper_params,
                     search_criteria = search_criteria)

gbm.sorted.grid <- h2o.getGrid(grid_id = "mygrid", sort_by = "mse")
print(gbm.sorted.grid)

best_model <- h2o.getModel(gbm.sorted.grid@model_ids[[1]])
summary(best_model)

scoring_history <- as.data.frame(best_model@model$scoring_history)
plot(scoring_history$number_of_trees, scoring_history$training_MSE, type="p") #training mse
points(scoring_history$number_of_trees, scoring_history$validation_MSE, type="l") #validation mse

## get the actual number of trees
ntrees <- best_model@model$model_summary$number_of_trees
print(ntrees)
```

For more information, refer to the [R grid search code](https://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/grid.R) and [runit_GBMGrid_airlines.R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/gbm/runit_GBMGrid_airlines.R).


## Grid Search in Python

- Class is `H2OGridSearch`
- `<grid_name>.show()`: Display a list of models (including model IDs, hyperparameters, and MSE) explored by grid search  (where `<grid_name>` is an instance of an `H2OGridSearch` class)
- `grid_search = H2OGridSearch(<model_type), hyper_params=hyper_parameters)`: Start a new grid search parameterized by:
	- `model_type` is the type of H2O estimator model with its unchanged parameters
	- `hyper_params` in Python is a dictionary of string parameters (keys) and a list of values to be explored by grid search (values) (e.g., `{'ntrees':[1,100], 'learn_rate':[0.1, 0.001]}`
	- `search_criteria` optional dictionary for specifying more a advanced search strategy



### Example


```python
  hyper_parameters = {'ntrees':[10,50], 'max_depth':[20,10]}
  grid_search = H2OGridSearch(H2ORandomForestEstimator, hyper_params=hyper_parameters)
  grid_search.train(x=["x1", "x2"], y="y", training_frame=train)
  grid_search.show()

```

For more information, refer to the [Python grid search code](https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/grid/grid_search.py) and [pyunit_benign_glm_grid.py](https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/glm/pyunit_benign_glm_grid.py).


## Grid Search Java API

Each parameter exposed by the schema can specify if it is supported by grid search by specifying the attribute `gridable=true` in the schema @API annotation. In any case, the Java API does not restrict the parameters supported by grid search.

There are two core entities: `Grid` and `GridSearch`. `GridSeach` is a job-building `Grid` object and is defined by the user's model factory and the [hyperspace walk strategy](https://en.wikipedia.org/wiki/Hyperparameter_optimization).  The model factory must be defined for each supported model type (DRF, GBM, DL, and K-means). The hyperspace walk strategy specifies how the user-defined space of hyper parameters is traversed. The space definition is not limited. For each point in hyperspace, model parameters of the specified type are produced.

The implementation supports a simple Cartesian grid search as well as random search with several different stopping criteria. Grid build triggers a new model builder job for each hyperspace point returned by the walk strategy. If the model builder job fails, the resulting model is ignored; however, it can still be tracked in the job list, and errors are returned in the grid build result.

Model builder jobs are run serially in sequential order. More advanced job scheduling schemes are under development.  Note that in cases of true big data sequential scheduling will yield the highest performance.  It is only with a large cluster and small data that concurrent scheduling will improve performance.

The grid object contains the results of the grid search: a list of model keys produced by the grid search as well as any errors, and a table of metrics for each succesful model. The grid object publishes a simple API to get the models.

Launch the grid search by specifying:

- the common model hyperparameters (parameter values which will be common across all models in the search)
- the search hyperparameters (a map `<parameterName, listOfValues>` that defines the parameter spaces to traverse)
- optionally, search criteria (an instance of `HyperSpaceSearchCriteria`)

The Java API can grid search any parameters defined in the model parameter's class (e.g., `GBMParameters`). Paramters that are appropriate for gridding are marked by the @API parameter, but this is not enforced by the framework.

Additional methods are available in the model builder to support creation of model parameters and configuration. This eliminates the requirement of the previous implementation where each gridable value was represented as a `double`. This also allows users to specify different building strategies for model parameters. For example, the REST layer uses a builder that validates parameters against the model parameter's schema, where the Java API uses a simple reflective builder. Additional reflections support is provided by PojoUtils (methods `setField`, `getFieldValue`).

### Example

```java
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
```

### Exposing grid search end-point for a new algorithm

In the following example, the PCA algorithm has been implemented and we would like to expose the algorithm via REST API. The following aspects are assumed:

  - The PCA model builder is called `PCA`
  - The PCA parameters are defined in a class called `PCAParameters`
  - The PCA parameters schema is called `PCAParametersV3`

To add support for PCA grid search:

1. Add the PCA model build factory into the `hex.grid.ModelFactories` class:

	  ```java
	  class ModelFactories {
	    /* ... */
	    public static ModelFactory<PCAModel.PCAParameters>
	      PCA_MODEL_FACTORY =
	      new ModelFactory<PCAModel.PCAParameters>() {
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
	  ```

2. Add the PCA REST end-point schema:

	  ```java
	  public class PCAGridSearchV99 extends GridSearchSchema<PCAGridSearchHandler.PCAGrid,
	    PCAGridSearchV99,
	    PCAModel.PCAParameters,
	    PCAV3.PCAParametersV3> {

	  }
	  ```

3. Add the PCA REST end-point handler:

	  ```java
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
	  ```

4. Register the REST end-point in the register factory `hex.api.Register`:

	  ```java
	  public class Register extends AbstractRegister {
	      @Override
	      public void register() {
	          // ...
	          H2O.registerPOST("/99/Grid/pca", PCAGridSearchHandler.class, "train", "Run grid search for PCA model.");
	          // ...
	       }
	  }
	  ```

## Grid Testing

The current test infrastructure includes:

**R Tests**

- GBM grids using wine, airlines, and iris datasets verify the consistency of results
- DL grid using the `hidden` parameter verifying the passing of structured parameters as a list of values
- Minor R testing support verifying equality of the model's parameters against  a given list of hyper parameters.

**JUnit Test**

- Basic tests verifying consistency of the results for DRF, GBM, and KMeans
- JUnit test assertions for grid results

There are tests for the `RandomDiscrete` search criteria in [runit_GBMGrid_airlines.R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/gbm/runit_GBMGrid_airlines.R) and [pyunit_benign_glm_grid.py](https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/glm/pyunit_benign_glm_grid.py).

## Caveats/In Progress

- Currently, the schema system requires specific classes instead of parameterized classes. For example, the schema definition `Grid<GBMParameters>` is not supported unless your define the class `GBMGrid extends Grid<GBMParameters>`.
- Grid Job scheduler is sequential only; schedulers for concurrent builds are under development.  Note that in cases of true big data sequential scheduling will yield the highest performance.  It is only with a large cluster and small data that concurrent scheduling will improve performance.
- The model builder job and grid jobs are not associated.
- There is no way to list the hyper space parameters that caused a model builder job failure.


## Documentation

- <a href="http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-core/javadoc/index.html" target="_blank">H2O Core Java Developer Documentation</a>: The definitive Java API guide for the core components of H2O.

- <a href="http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-algos/javadoc/index.html" target="_blank">H2O Algos Java Developer Documentation</a>: The definitive Java API guide for the algorithms used by H2O.

- <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/random%20hyperparmeter%20search%20and%20roadmap.md">Hyperparameter Optimization in H2O </a>: A guide to Grid Search and Random Search in H2O.

