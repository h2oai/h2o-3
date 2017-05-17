``init``
--------

- Available in: GLRM, K-means
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the initialization mode used in K-Means. The options are Random, Furthest, PlusPlus, and User.

- **Random**: Choose :math:`K` clusters from the set of :math:`N` observations at random so that each observation has an equal chance of being chosen.

- **Furthest** (Default): 

  a. Choose one center :math:`m_{1}` at random.

  b. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = ||(x_{i}-m_{1})||^2`

  c. Choose :math:`m_{2}` to be the :math:`x_{i}` that maximizes :math:`d(x_{i}, m_{1})`.

  d. Repeat until :math:`K` centers have been chosen.

- **PlusPlus**: 

  a. Choose one center :math:`m_{1}` at random.

  b. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = \|(x_{i}-m_{1})\|^2`

  c. Let :math:`P(i)` be the probability of choosing :math:`x_{i}` as :math:`m_{2}`. Weight :math:`P(i)` by :math:`d(x_{i}, m_{1})` so that those :math:`x_{i}` furthest from :math:`m_{2}` have a higher probability of being selected than those :math:`x_{i}` close to :math:`m_{1}`.

  d. Choose the next center :math:`m_{2}` by drawing at random according to the weighted probability distribution.
   
  e. Repeat until :math:`K` centers have been chosen. 

- **User** initialization allows you to specify a file (using the ``user_points`` parameter) that includes a vector of initial cluster centers. 

**Notes**: 

- The user-specified points dataset must have the same number of columns as the training observations.
- This option is ignored when ``estimate_k`` is enabled. In this case, the algorithm is deterministic. 
- If this option is not specified but a user-points file is specified, then this value will default to ``user``.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `estimate_k <estimate_k.html>`__
- `user_points <user_points.html>`__

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
