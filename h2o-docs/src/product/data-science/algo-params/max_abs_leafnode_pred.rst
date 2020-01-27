``max_abs_leafnode_pred``
-------------------------

- Available in: GBM, XGBoost
- Hyperparameter: yes

Description
~~~~~~~~~~~

When building a GBM model, this option reduces overfitting by limiting the maximum absolute value of a leaf node prediction. This option is mainly used for classification models. It is a pure regularization tuning parameter as it prevents any particular leaf node from making large absolute predictions, but it doesn't directly relate to the actual final prediction (other than that the final value can't be larger than ``ntrees * max_abs_leafnode_pred``, by definition).

This option defaults to Double.MAX_VALUE.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `ntrees <ntrees.html>`__


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

	# try using the max_abs_leafnode_pred parameter:
	cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid,
	                   max_abs_leafnode_pred = 2, seed = 1234)

	# print the logloss for your model
	print(h2o.logloss(cov_gbm, valid = TRUE))


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
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

	# try using the 'max_abs_leafnode_pred' parameter:
	cov_gbm = H2OGradientBoostingEstimator(max_abs_leafnode_pred= 2, seed = 1234)
	cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	print(cov_gbm.logloss(valid=True))
