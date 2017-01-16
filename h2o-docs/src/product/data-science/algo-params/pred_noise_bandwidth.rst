``pred_noise_bandwidth``
------------------------

- Available in: GBM
- Hyperparameter: yes

Description
~~~~~~~~~~~

Use this option to specify the bandwidth (sigma) of Gaussian multiplicative noise ~N(1,sigma) for tree-node predictions. If this parameter is specified with a value greater than 0, then every leaf node prediction is randomly scaled by a number drawn from a normal distribution centered around 1 with a bandwidth given by this parameter. 

Refer to the following `wikipedia page <https://en.wikipedia.org/wiki/Noise_(signal_processing)>`_ for more information about signal processing noise. 

This value must be >= to 0 and defaults to 0 (disabled).

Related Parameters
~~~~~~~~~~~~~~~~~~

- none


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the titanic dataset:
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic <-  h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] <- as.factor(titanic['survived'])

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors <- setdiff(colnames(titanic), colnames(titanic)[2:3])
	response <- "survived"

	# split into train and validation
	titanic.splits <- h2o.splitFrame(data =  titanic, ratios = .8, seed = 1234)
	train <- titanic.splits[[1]]
	valid <- titanic.splits[[2]]

	# try using the pred_noise_bandwidth parameter:
	titanic_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid,
	                   pred_noise_bandwidth = 0.1, seed = 1234)

	# print the auc for your model
	print(h2o.auc(titanic_gbm, valid = TRUE))

	# Example of values to grid over for `pred_noise_bandwidth`
	# Note: this parameter is meant for much bigger datasets than the one in this example
	hyper_params <- list( pred_noise_bandwidth = c(0, 0.1, 0.3) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "titanic_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("titanic_grid", sort_by = "auc", decreasing = TRUE)
	sortedGrid

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the titanic dataset:
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] = titanic['survived'].asfactor()

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors = titanic.columns
	del predictors[1:3]
	response = 'survived'

	# split into train and validation sets
	train, valid = titanic.split_frame(ratios = [.8], seed = 1234)

	# try using the `pred_noise_bandwidth` parameter:
	# initiliaze the estimator
	titanic_gbm = H2OGradientBoostingEstimator(pred_noise_bandwidth = 0.1, seed = 1234)

	# train the model
	titanic_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(titanic_gbm.auc(valid = True))


	# Example of values to grid over for `pred_noise_bandwidth`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `pred_noise_bandwidth` to grid over
	# Note: this parameter is meant for much bigger datasets than the one in this example
	hyper_params = {'pred_noise_bandwidth': [0.0, 0.1, 0.3]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	titanic_gbm_2 = H2OGradientBoostingEstimator(seed = 1234)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = titanic_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by='auc', decreasing=True)
	print(sorted_grid)