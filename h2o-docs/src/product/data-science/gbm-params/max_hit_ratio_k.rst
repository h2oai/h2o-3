``max_hit_ratio_k``
-------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: no

Description
~~~~~~~~~~~
Hit ratios can be used to evaluate the performance of a model. The hit ratio is the percentage of instances where the model correctly predicts the actual class of an observation. The ``max_hit_ratio_k`` option specifies the maximum number of predictions to consider for the hit ratio computation. 

Note that this option is available for multiclass problems only and is set to 0 (disabled) by default.


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

	# try using the max_hit_ratio_k parameter:
	# max_hit_ratio_k does not affect the actual model fit, and is for information
	# and inner-H2O calculations
	cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid, max_hit_ratio_k = 3, seed = 1234)

	# print out model results to see the max_hite_ratio_k table
	cov_gbm 

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init(strict_version_check=False)
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

	# try using the max_hit_ratio_k parameter:
	# max_hit_ratio_k does not affect the actual model fit, and is for information
	# and inner-H2O calculations
	cov_gbm = H2OGradientBoostingEstimator(max_hit_ratio_k = 3, seed = 1234)

	cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print out model results to see the max_hite_ratio_k table
	cov_gbm.show()