``ignore_const_cols``
---------------------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, XGBoost, Aggregator, Isolation Forest
- Hyperparameter: no

Description
~~~~~~~~~~~

Unlike the ``ignored_columns`` parameter, which allows you to specify the column name or names to ignore when building a model, the ``ignore_const_cols`` option allows you to specify that the algorithm should ignore all constant columns (columns that include the same value). This allows you to speed up training by ignoring columns from which no information can be gained. 

This option is enabled by default. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `ignored_columns <ignored_columns.html>`__


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

	# split into train and validation sets
	cars.split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
	train <- cars.split[[1]]
	valid <- cars.split[[2]]

	# add a few constant columns
	cars["const_1"] = 6
	cars["const_2"] = 7

	# try using the `ignore_const_cols` parameter (boolean parameter):
	# train your model
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, ignore_const_cols = TRUE, seed = 1234)

	# print the auc for your model
	print(h2o.auc(cars_gbm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the cars dataset:
	# this dataset is used to classify whether or not a car is economical based on
	# the car's displacement, power, weight, and acceleration, and the year it was made
	cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

	# convert response column to a factor
	cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

	# set the predictor names and the response column name
	predictors = ["displacement","power","weight","acceleration","year"]
	response = "economy_20mpg"

	# add a few constant columns
	cars["const_1"] = 6
	cars["const_2"] = 7

	# split into train and validation sets
	train, valid = cars.split_frame(ratios = [.8], seed = 1234)

	# try using the `ignore_const_cols` parameter (boolean parameter):
	# first initialize your estimator
	cars_gbm = H2OGradientBoostingEstimator(seed = 1234, ignore_const_cols = True)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	cars_gbm.auc(valid=True)