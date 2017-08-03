``weights_column``
------------------

- Available in: GBM, DRF, Deep Learning, GLM, AutoML, XGBoost
- Hyperparameter: no

Description
~~~~~~~~~~~

This option specifies the column in a training frame to be used when determining weights. Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are also supported. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

For scoring, all computed metrics will take the observation weights into account (for Gains/Lift, AUC, confusion matrices, logloss, etc.), so it’s important to also provide the weights column for validation or test sets if you want to up/down-weight certain observations (ideally consistently between training and testing). Note that if you omit the weights, then all observations will have equal weights (set to 1) for the computation of the metrics. For example, a weight of 2 is identical to duplicating a row. 

**Notes**: 

- Weights can be specified as integers or as non-integers.
- You do not have to specify a weight when calling ``model.predict()``. New test data will have the weights column with the same column name. 
- The weights column cannot be the same as the `fold_column <fold_column.html>`__. 
- Example unit test scripts are available on GitHub:

  - https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/gbm/pyunit_weights_gbm.py
  - https://github.com/h2oai/h2o-3/blob/master/h2o-py/tests/testdir_algos/gbm/pyunit_weights_gamma_gbm.py

Related Parameters
~~~~~~~~~~~~~~~~~~

- `offset_column <offset_column.html>`__
- `y <y.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the cars dataset: 
	# this dataset is used to classify whether or not a car is economical based on 
	# the car's displacement, power, weight, and acceleration, and the year it was made 
	cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

	# convert response column to a factor
	cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

	# set the predictor names and the response column name
	predictors <- c("displacement","power","weight","acceleration","year")
	response <- "economy_20mpg"

	# create a new column that specifies the weights
	# or use a column that already exists in the dataframe
	# Note: do not use the fold_column
	# in this case we will use the "weight" column that already exists in the dataframe
	# this column contains the integers 1 or 2 in each row

	# split into train and validation sets
	cars.split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
	train <- cars.split[[1]]
	valid <- cars.split[[2]]

	# try using the `weights_column` parameter:
	# train your model, where you specify the weights_column
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, weights_column = "weight", seed = 1234)

	# print the auc for the validation data
	print(h2o.auc(cars_gbm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

	# import the cars dataset:
	# this dataset is used to classify whether or not a car is economical based on
	# the car's displacement, power, weight, and acceleration, and the year it was made
	cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

	# convert response column to a factor
	cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

	# set the predictor names and the response column name
	predictors = ["displacement","power","weight","acceleration","year"]
	response = "economy_20mpg"

	# create a new column that specifies the weights
	# or use a column that already exists in the dataframe
	# Note: do not use the fold_column
	# in this case we will use the "weight" column that already exists in the dataframe
	# this column contains the integers 1 or 2 in each row

	# split into train and validation sets
	train, valid = cars.split_frame(ratios = [.8], seed = 1234)

	# try using the `weights_column` parameter:
	# first initialize your estimator
	cars_gbm = H2OGradientBoostingEstimator(seed = 1234)

	# then train your model, where you specify the weights_column
	cars_gbm.train(x = predictors, y = response, training_frame = train,
	               validation_frame = valid, weights_column = "weight")

	# print the auc for the validation data
	cars_gbm.auc(valid=True)
