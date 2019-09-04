``init`` (CoxPH)
------------------------

- Available in: CoxPH
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option allows you to set the initial values for the coefficients in the model. This value defaults to 0.


Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the seeds dataset:
	# this dataset looks at three different types of wheat varieties
	# the original dataset can be found at http://archive.ics.uci.edu/ml/datasets/seeds
	seeds <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")

	# set the predictor names 
	# ignore the 8th column which has the prior known clusters for this dataset
	predictors <-colnames(seeds)[-length(seeds)]

	# split into train and validation
	seeds_splits <- h2o.splitFrame(data = seeds, ratios = .8, seed = 1234)
	train <- seeds_splits[[1]]
	valid <- seeds_splits[[2]]

	# try using the `init` parameter:
	# build the model with three clusters
	seeds_kmeans <- h2o.kmeans(x = predictors, k = 3, init='Furthest', training_frame = train, validation_frame = valid, seed = 1234)

	# print the total within cluster sum-of-square error for the validation dataset
	print(paste0("Total sum-of-square error for valid dataset: ", h2o.tot_withinss(object = seeds_kmeans, valid = T)))


	# select the values for `init` to grid over:
	# Note: this dataset is too small to see significant differences between these options
	# the purpose of the example is to show how to use grid search with `init` if desired
	hyper_params <- list( init = c("PlusPlus", "Furthest", "Random")  )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	grid <- h2o.grid(x = predictors, k = 3, training_frame = train, validation_frame = valid,
	                 algorithm = "kmeans", grid_id = "seeds_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)

	## Sort the grid models by TotSS
	sortedGrid <- h2o.getGrid("seeds_grid", sort_by  = "tot_withinss", decreasing = F)
	sortedGrid


	
   .. code-block:: python

	import h2o
	from h2o.estimators.kmeans import H2OKMeansEstimator
	h2o.init()

	# import the seeds dataset:
	# this dataset looks at three different types of wheat varieties
	# the original dataset can be found at http://archive.ics.uci.edu/ml/datasets/seeds
	seeds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")

	# set the predictor names 
	# ignore the 8th column which has the prior known clusters for this dataset
	predictors = seeds.columns[0:7]

	# split into train and validation sets
	train, valid = seeds.split_frame(ratios = [.8], seed = 1234)

	# try using the `init` parameter:
	# initialize the estimator then train the model
	seeds_kmeans = H2OKMeansEstimator(k = 3, init='Furthest', seed = 1234)
	seeds_kmeans.train(x = predictors, training_frame = train, validation_frame= valid)

	# print the total within cluster sum-of-square error for the validation dataset
	print("sum-of-square error for valid:",seeds_kmeans.tot_withinss(valid = True))


	# grid over `init`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `init` to grid over
	# Note: this dataset is too small to see significant differences between these options
	# the purpose of the example is to show how to use grid search with `init` if desired
	hyper_params = {'init': ["PlusPlus", "Furthest", "Random"]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the estimator
	seeds_kmeans = H2OKMeansEstimator(k = 3, seed = 1234)

	# build grid search with previously made Kmeans and hyperparameters
	grid = H2OGridSearch(model = seeds_kmeans, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, training_frame = train, validation_frame = valid)

	# sort the grid models by total within cluster sum-of-square error.
	sorted_grid = grid.get_grid(sort_by='tot_withinss', decreasing= False)
	print(sorted_grid)
