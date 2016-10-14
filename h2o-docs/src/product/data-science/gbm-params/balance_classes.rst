``balance_classes``
-------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

During model training, you might find that the majority of your data belongs in a single class. For example, consider a binary classification model that has 100 rows, with 80 rows labeled as class 1 and the remaining 20 rows labeled as class 2. This is a common scenario, given that machine learning attempts to predict class 1 with the highest accuracy. It can also be an example of an imbalanced dataset, in this case, with a ratio of 4:1. 

The ``balance_classes`` option can be used to balance the class distribution. When enabled, H2O will either undersample the majority classes or oversample the minority classes. In addition, if this option is enabled, then you can also specify a value for the ``class_sampling_factors`` and ``max_after_balance_size`` options. 

**Notes**:

- This option is disabled by default. 
- This option only applies to classification problems. 
- Enabling this option can increase the size of the data frame.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `class_sampling_factors <class_sampling_factors.html>`__
- `max_after_balance_size <max_after_balance_size.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the covtype dataset: 
	# this dataset is used to classify the correct forest cover type 
	# original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Covertype
	covtype <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")

	# convert response column to a factor
	covtype[,55] <- as.factor(covtype[,55])

	# set the predictor names and the response column name
	predictors <- colnames(covtype[1:54])
	response <- 'C55'

	# split into train and validation sets
	covtype.splits <- h2o.splitFrame(data =  covtype, ratios = .8, seed = 1234)
	train <- covtype.splits[[1]]
	valid <- covtype.splits[[2]]

	# # try using the balance_classes parameter (set to TRUE):
	# cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	#                    validation_frame = valid, balance_classes = TRUE, seed = 1234)
	# print(h2o.logloss(cov_gbm, valid = TRUE))

	# grid over `balance_classes` (boolean parameter)
	# select the values for `balance_classes` to grid over
	hyper_params <- list( balance_classes = c(TRUE, FALSE) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously made GBM and hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "covtype_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)  

	# Sort the grid models by logloss
	sortedGrid <- h2o.getGrid("covtype_grid", sort_by = "logloss", decreasing = FALSE)    
	sortedGrid


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

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

	# # try using the balance_classes parameter (set to True):
	# cov_gbm = H2OGradientBoostingEstimator(balance_classes = True, seed = 1234)
	# cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	print('logloss', cov_gbm.logloss(valid = True))

	# grid over `balance_classes` (boolean parameter)
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `balance_classes` to grid over
	hyper_params = {'balance_classes': [True, False]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	cov_gbm_2 = H2OGradientBoostingEstimator(seed = 1234)

	# build grid search with previously made GBM and hyperparameters
	grid = H2OGridSearch(model = cov_gbm_2, hyper_params = hyper_params,  
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by increasing logloss
	sorted_grid = grid.get_grid(sort_by='logloss', decreasing=False)
	print(sorted_grid)