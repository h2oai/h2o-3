``stratify_by``
---------------

- Available in: CoxPH
- Hyperparameter: yes

Description
~~~~~~~~~~~

Specify a list of columns to use for stratification.


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
	
