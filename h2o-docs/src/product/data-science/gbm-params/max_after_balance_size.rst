``max_after_balance_size``
--------------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

When your datasest includes imbalanced data, you may find it necessary to balance the data using the ``balance_classes`` option. When specified, the algorithm will either undersample the majority classes or oversampling the minority classes. In most cases, though, enabling the ``balance_classes`` option will increase the data frame size. To reduce the data frame size, you can use the ``max_after_balance_size`` option. This specifies the maximum relative size of the training data after balancing class counts. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `balance_classes <balance_classes.html>`__
- `class_sampling_factors <class_sampling_factors.html>`__


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

	# look at the frequencies of each class
	print(h2o.table(covtype['C55']))

	# try using the max_after_balance_size parameter:
	max = .85
	cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid, balance_classes = TRUE, 
	                   max_after_balance_size = max, seed = 1234)

	# print the logloss for your model
	print(h2o.logloss(cov_gbm, valid = TRUE))

	# grid over `max_after_balance_size`
	# select the values for `max_after_balance_size` to grid over
	# the first and last max_after_balance_sizes reduce the size of the
	# original dataset, the second increases the dataset by 1.7 
	hyper_params <- list( max_after_balance_size = c(.85, 1.7, .5) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously made GBM and hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "covtype_grid", balance_classes =  TRUE, hyper_params = hyper_params,
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

	# look at the frequencies of each class
	print(covtype[54].table())

	# try using the max_after_balance_size parameter:
	max = .85
	cov_gbm = H2OGradientBoostingEstimator(balance_classes = True, 
	                                       max_after_balance_size = max,
	                                       seed = 1234)

	cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for your model
	print('logloss', cov_gbm.logloss(valid = True))

	# grid over `max_after_balance_size` 
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `max_after_balance_size` to grid over
	# the first and last max_after_balance_sizes reduce the size of the
	# original dataset, the second increases the dataset by 1.7 
	hyper_params = {'max_after_balance_size': [.85, 1.7,.5]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	cov_gbm_2 = H2OGradientBoostingEstimator(balance_classes = True, seed = 1234)

	# build grid search with previously made GBM and hyperparameters
	grid = H2OGridSearch(model = cov_gbm_2, hyper_params = hyper_params,  
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by increasing logloss
	sorted_grid = grid.get_grid(sort_by='logloss', decreasing=False)
	print(sorted_grid)