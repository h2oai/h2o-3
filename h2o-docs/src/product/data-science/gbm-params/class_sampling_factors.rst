``class_sampling_factors``
--------------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

When your datasest includes imbalanced data, you may find it necessary to balance the data using the ``balance_classes`` option. When specified, the algorithm will either undersample the majority classes or oversampling the minority classes. 

By default, sampling factors will be automatically computed to obtain class balance during training. You can change this behavior using the ``class_sampling_factors`` option. This option sets an over/under-sampling ratio for each class (in lexicographic order).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `balance_classes <balance_classes.html>`__
- `max_after_balance_size <max_after_balance_size.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the covtype dataset: 
	# This dataset is used to classify the correct forest cover type 
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

	# try using the class_sampling_factors parameter:
	# since all but Class 2 have similar frequency counts, let's undersample Class 2
	# and not change the sampling rate of the other classes
	# note: class_sampling_factors must be a list of floats
	sample_factors <- c(1., 0.5, 1., 1., 1., 1., 1.)
	cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid, balance_classes = TRUE, 
	                   class_sampling_factors = sample_factors, seed = 1234)

	# print the logloss for your model
	print(h2o.logloss(cov_gbm, valid = TRUE))

	# grid over `class_sampling_factors`
	# select the values for `class_sampling_factors` to grid over
	hyper_params <- list( class_sampling_factors = list(c(1., 0.5, 1., 1., 1., 1., 1.),
	                      c(2., 1., 2., 2., 2., 2., 2.), c(4., 0.5, 1., 1., 2., 2., 1.)))

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
	# This dataset is used to classify the correct forest cover type 
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

	# try using the class_sampling_factors parameter:
	# since all but Class 2 have similar frequency counts, let's undersample Class 2
	# and not change the sampling rate of the other classes
	# note: class_sampling_factors must be a list of floats
	sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
	cov_gbm = H2OGradientBoostingEstimator(balance_classes = True, class_sampling_factors = sample_factors, seed = 1234)
	cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for your model
	print('logloss', cov_gbm.logloss(valid = True))

	# grid over `class_sampling_factors`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `class_sampling_factors` to grid over
	# the first class_sampling_factors is the same as above
	# the second doubles the number of samples for all but Class 2
	# the third demonstrates a random option 
	hyper_params = {'class_sampling_factors': [[1., 0.5, 1., 1., 1., 1., 1.], [2., 1., 2., 2., 2., 2., 2.],
	               [4., 0.5, 1., 1., 2., 2., 1.]]}

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