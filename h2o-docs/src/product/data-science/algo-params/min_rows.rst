``min_rows``
------------

- Available in: GBM, DRF, XGBoost, Isolation Forest 
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the minimum number of observations for a leaf in order to split. For example, if a user specifies ``min_rows = 500``, and the data has 500 TRUEs and 400 FALSEs, then the algorithm won’t split because it requires 500 responses on both sides. In addition, a single tree will stop splitting when there are no more splits that satisfy the ``min_rows`` parameter, if it reaches ``max_depth``, or if there are no splits that satisfy this ``min_split_improvement`` parameter.

The default value for ``min_rows`` is 10, so this option rarely affects the GBM splits because GBMs are typically shallow, but the concept still applies.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `max_depth <max_depth.html>`__
- `min_split_improvement <min_split_improvement.html>`__ 


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the cars dataset: 
	# this dataset is used to classify whether or not a car is economical based on 
	# the car's displacement, power, weight, and acceleration, and the year it was made 
	cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

	# convert response column to a factor
	cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

	# set the predictor names and the response column name
	predictors <- c("displacement","power","weight","acceleration","year")
	response <- "economy_20mpg"

	# split into train and validation sets
	cars.split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
	train <- cars.split[[1]]
	valid <- cars.split[[2]]

	# try using the `min_rows` parameter:
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, min_rows = 16, seed = 1234)

	# print the auc for your model
	print(h2o.auc(cars_gbm, valid = TRUE))

	# Example of values to grid over for `min_rows`:
	hyper_params <- list( min_rows = seq(1,20,1)  )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	# this GBM uses early stopping once the validation AUC doesn't improve by at least 0.01% for
	# 5 consecutive scoring events
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "cars_grid", hyper_params = hyper_params,
	                 stopping_rounds = 5, stopping_tolerance = 1e-4, stopping_metric = "AUC",
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("cars_grid", sort_by = "auc", decreasing = TRUE)
	sortedGrid

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the cars dataset:
	# this dataset is used to classify whether or not a car is economical based on
	# the car's displacement, power, weight, and acceleration, and the year it was made
	cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

	# convert response column to a factor
	cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

	# set the predictor names and the response column name
	predictors = ["displacement","power","weight","acceleration","year"]
	response = "economy_20mpg"

	# split into train and validation sets
	train, valid = cars.split_frame(ratios = [.8], seed = 1234)

	# try turning on the `min_rows` parameter:
	# initialize your estimator
	cars_gbm = H2OGradientBoostingEstimator(min_rows = 16, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(cars_gbm.auc(valid=True))


	# Example of values to grid over for `min_rows`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `min_rows` to grid over
	hyper_params = {'min_rows': list(range(1,21))}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	# use early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	cars_gbm_2 = H2OGradientBoostingEstimator(seed = 1234,
	                                          stopping_rounds = 5,
	                                          stopping_metric = "AUC", stopping_tolerance = 1e-4,)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = cars_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid, seed = 1234)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by = 'auc', decreasing = True)
	print(sorted_grid)
