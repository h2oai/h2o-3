``mtries``
----------

- Available in: DRF, Isolation Forest, Uplift DRF
- Hyperparameter: yes

Description
~~~~~~~~~~~

Use this option to specify the number of columns to randomly select at each level. 

This value defaults to -1. Valid values for this option are -2, -1, and any value >= 1. If a value other than -1 or -2 is used, then the number of variables is:

- the square root of the number of columns for classification 
- p/3 for regression (where p is the number of predictors). 

**Note:** If ``mtries=-2``, it uses all features for DRF and IF.

The following illustrates how column sampling is implemented for DRF. For an example model using:

- 100 columns
- ``col_sample_rate_per_tree`` is 0.602
- ``mtries`` is -1 or 7 (refers to the number of active predictor columns for the dataset)

For each tree, the floor is used to determine the number of columns that are randomly picked (for this example, (0.602*100)=60 out of the 100 columns). 

For classification cases where ``mtries=-1``, the square root is randomly chosen for each split decision (out of the total 60 - for this example, (:math:`\sqrt{100}` = 10 columns).

For regression, the floor  is used for each split by default (in this example, (100/3)=33 columns). If ``mtries=7``, then 7 columns are picked for each split decision (out of the 60).

``mtries`` is configured independently of ``col_sample_rate_per_tree``, but it can be limited by it. For example, if ``col_sample_rate_per_tree=0.01``, then there’s only one column left for each split, regardless of how large the value for ``mtries`` is.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `col_sample_rate_change_per_level <col_sample_rate_change_per_level.html>`__
- `col_sample_rate_per_tree <col_sample_rate_per_tree.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the covtype dataset:
		# this dataset is used to classify the correct forest cover type
		# original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Covertype
		covtype <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")

		# convert response column to a factor
		covtype[, 55] <- as.factor(covtype[, 55])

		# set the predictor names and the response column name
		predictors <- colnames(covtype[1:54])
		response <- 'C55'

		# split into train and validation sets
		covtype_splits <- h2o.splitFrame(data =  covtype, ratios = 0.8, seed = 1234)
		train <- covtype_splits[[1]]
		valid <- covtype_splits[[2]]

		# try using the `mtries` parameter:
		cov_drf <- h2o.randomForest(x = predictors, y = response, training_frame = train,
		                   validation_frame = valid, mtries = 30, seed = 1234)
		print(h2o.logloss(cov_drf, valid = TRUE))

		# grid over `mtries` parameter:
		# select the values for `mtries` to grid over
		hyper_params <- list( mtries = c(10, 40, 50) )

		# this example uses cartesian grid search because the search space is small
		# and we want to see the performance of all models. For a larger search space use
		# random grid search instead: {'strategy': "RandomDiscrete"}

		# build grid search with previously made DRF and hyperparameters
		grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
		                 algorithm = "drf", grid_id = "covtype_grid", hyper_params = hyper_params,
		                 search_criteria = list(strategy = "Cartesian"), seed = 1234)

		# Sort the grid models by logloss
		sorted_grid <- h2o.getGrid("covtype_grid", sort_by = "logloss", decreasing = FALSE)
		sorted_grid

   .. code-tab:: python

		import h2o
		from h2o.estimators.random_forest import H2ORandomForestEstimator
		h2o.init()

		# import the covtype dataset:
		# this dataset is used to classify the correct forest cover type
		# original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Covertype
		covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")

		# convert response column to a factor
		covtype[54] = covtype[54].asfactor()

		# set the predictor names and the response column name
		predictors = covtype.columns[0:54]
		response = 'C55'

		# split into train and validation sets
		train, valid = covtype.split_frame(ratios = [.8], seed = 1234)

		# try using the `mtries` parameter:
		cov_drf = H2ORandomForestEstimator(mtries = 30, seed = 1234)
		cov_drf.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		print('logloss', cov_drf.logloss(valid = True))

		# grid over `mtries` parameter:
		# import Grid Search
		from h2o.grid.grid_search import H2OGridSearch

		# select the values for `mtries` to grid over
		hyper_params = {'mtries': [10, 40, 50]}

		# this example uses cartesian grid search because the search space is small
		# and we want to see the performance of all models. For a larger search space use
		# random grid search instead: {'strategy': "RandomDiscrete"}
		# initialize the drf estimator
		cov_drf_2 = H2ORandomForestEstimator(seed = 1234)

		# build grid search with previously made DRF and hyperparameters
		grid = H2OGridSearch(model = cov_drf_2, hyper_params = hyper_params,
		                     search_criteria = {'strategy': "Cartesian"})

		# train using the grid
		grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# sort the grid models by logloss
		sorted_grid = grid.get_grid(sort_by='logloss', decreasing=False)
		print(sorted_grid)
