``sample_rate_per_class``
-------------------------

- Available in: GBM, DRF 
- Hyperparameter: yes

Description
~~~~~~~~~~~

When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with ``sample_rate``). The range for this option is 0.0 to 1.0. This option can also be specified along with ``sample_rate``. In this case, only the first option that GBM/DRF encounters will be used.

**Note:** If ``sample_rate_per_class`` is specified, then ``sample_rate`` will be ignored.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `col_sample_rate <col_sample_rate.html>`__
- `sample_rate <sample_rate.html>`__


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

	# look at the counts per class in the training set:
	h2o.table(train[response])

	# try using the `sample_rate_per_class` parameter:
	# downsample the Class 2, and leave the rest the same
	rate_per_class_list = c(1, .4, 1, 1, 1, 1, 1)
	cov_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid, sample_rate_per_class = rate_per_class_list,
	                   seed = 1234)

	# print the logloss
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

	# look at the counts per class in the training set:
	print(train[response].table())

	# try using the `sample_rate_per_class` parameter:
	# downsample the Class 2, and leave the rest the same
	rate_per_class_list = [1, .4, 1, 1, 1, 1, 1]
	cov_gbm = H2OGradientBoostingEstimator(sample_rate_per_class = rate_per_class_list, seed = 1234)
	cov_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	print('logloss', cov_gbm.logloss(valid = True))