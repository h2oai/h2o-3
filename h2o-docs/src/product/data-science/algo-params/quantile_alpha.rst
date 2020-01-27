``quantile_alpha``
------------------

- Available in: GBM, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

The ``quantile_alpha`` parameter value defines the desired quantile when performing quantile regression. Used in combination with ``distribution = quantile``, ``quantile_alpha`` activates the quantile loss function. For example, if you want to predict the 80th percentile of the response column’s value, then you can specify ``quantile_alpha=0.8``. The ``quantile_alpha`` value defaults to 0.5 (i.e., the median value, essentially the same as specifying ``distribution=laplace``).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the boston dataset:
	# this dataset looks at features of the boston suburbs and predicts median housing prices
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
	boston <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

	# set the predictor names and the response column name
	predictors <- colnames(boston)[1:13]
	# set the response column to "medv", the median value of owner-occupied homes in $1000's
	response <- "medv"

	# convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
	boston["chas"] <- as.factor(boston["chas"])

	# split into train and validation sets
	boston.splits <- h2o.splitFrame(data =  boston, ratios = .8, seed = 1234)
	train <- boston.splits[[1]]
	valid <- boston.splits[[2]]

	# try using the `quantile_alpha` parameter:
	# train your model, where you specify distribution = quantile
	# and the quantile_alpha value
	boston_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      distribution = 'quantile',
	                      quantile_alpha = .8,
	                      seed = 1234)

	# print the mse for validation set
	print(h2o.mse(boston_gbm, valid = TRUE))

	# grid over `quantile_alpha` parameter
	# select the values for `quantile_alpha` to grid over
	hyper_params <- list( quantile_alpha = c(.2, .5, .8) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously made GBM and hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train,
	                 validation_frame = valid, algorithm = "gbm", 
	                 grid_id = "boston_grid", 
	                 distribution = "quantile",
	                 hyper_params = hyper_params,
	                 seed = 1234)

	# Sort the grid models by MSE
	sortedGrid <- h2o.getGrid("boston_grid", sort_by = "mse", decreasing = FALSE)
	sortedGrid

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the boston dataset:
	# this dataset looks at features of the boston suburbs and predicts median housing prices
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
	boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

	# set the predictor names and the response column name
	predictors = boston.columns[:-1]
	# set the response column to "medv", the median value of owner-occupied homes in $1000's
	response = "medv"

	# convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
	boston['chas'] = boston['chas'].asfactor()

	# split into train and validation sets
	train, valid = boston.split_frame(ratios = [.8], seed = 1234)

	# try using the `quantile_alpha` parameter:
	# initialize the estimator then train the model where you specify distribution = quantile
	# and the quantile_alpha value
	boston_gbm = H2OGradientBoostingEstimator(distribution = "quantile", quantile_alpha = .8, seed = 1234)

	# then train your model
	boston_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the MSE for the validation data
	print(boston_gbm.mse(valid=True))


	# Example of values to grid over for `quantile_alpha`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `quantile_alpha` to grid over
	hyper_params = {'quantile_alpha': [.2, .5, .8]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	boston_gbm_2 = H2OGradientBoostingEstimator(distribution="quantile", seed = 1234,
	                                              stopping_metric = "mse", stopping_tolerance = 1e-4)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = boston_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing MSE
	sorted_grid = grid.get_grid(sort_by = 'mse', decreasing = False)
	print(sorted_grid)