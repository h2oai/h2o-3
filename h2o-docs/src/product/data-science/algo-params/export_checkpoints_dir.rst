``export_checkpoints_dir``
--------------------------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, Word2Vec, Stacked Ensembles, XGBoost, Aggregator, CoxPH, Isolation Forest, AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

This option is used to automatically export generated models to a specified directory.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	airlines = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")

	predictors <- c("DayofMonth", "DayOfWeek")
	response <- "IsDepDelayed"
	hyper_parameters <- list(ntrees = c(5,10))
	checkpoints_dir <- tempfile()

	gbm_grid <- h2o.grid("gbm", x=predictors, y=response, training_frame=airlines, distribution="bernoulli", stopping_rounds=3, stopping_metric="AUTO", stopping_tolerance=1e-2, learn_rate=0.1, max_depth=3, hyper_params=hyper_parameters, export_checkpoints_dir=checkpoints_dir, seed=1234)

	num_files <- length(checkpoints_dir)
	num_files


   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	import tempfile
	from os import listdir
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	from h2o.grid.grid_search import H2OGridSearch
	airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")

	predictors = ["DayofMonth", "DayOfWeek"]
	response = "IsDepDelayed"

	hyper_parameters = {'ntrees': [5,10]}

	search_crit = {'strategy': "RandomDiscrete",
                   	   'seed': 1234,
                   	   'stopping_rounds' : 3,
                   	   'stopping_metric' : "AUTO",
                   	   'stopping_tolerance': 1e-2}

	checkpoints_dir = tempfile.mkdtemp()

	air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)

	air_grid.train(x=predictors, y=response, training_frame=airlines, distribution="bernoulli",
                   learn_rate=0.1,
                   max_depth=3,
                   export_checkpoints_dir=checkpoints_dir)

	num_files = len(listdir(checkpoints_dir))
	num_files
	2
