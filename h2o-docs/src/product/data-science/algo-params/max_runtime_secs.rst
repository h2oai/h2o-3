.. _max_runtime_secs:

``max_runtime_secs``
-----------------------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, AutoML, XGBoost, Word2vec, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

**Model Building**

When building a model, this option specifes the maximum runtime in seconds that you want to allot in order to complete the model. If this maximum runtime is exceeded before the model build is completed, then the model will fail. 

**Using with Grid Search**

When performing a grid search, this option specifies the maximum runtime in seconds for the entire grid. This option can also be combined with ``max_runtime_secs`` in the model parameters. If ``max_runtime_secs`` is not set in the model parameters, then each model build is launched with a limit equal to the remainder of the grid time. On the other hand, if ``max_runtime_secs`` is set in the model parameters, then each build is launched with a limit equal to the minimum of the model time limit and the remaining time for the grid.

Specifying ``max_runtime_secs=0`` disables this option, thus allowing for an unlimited amount of runtime.

Related Parameters
~~~~~~~~~~~~~~~~~~

- none

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

	# try using the `max_runtime_secs` parameter:
	# train your model
	# set max_runtime_secs to 10 seconds to limit how long the model can take to build
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, max_runtime_secs = 10, ntrees = 10000, max_depth = 10, seed = 1234)

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

	# split into train and validation sets
	train, valid = cars.split_frame(ratios = [.8], seed = 1234)

	# try using the `max_runtime_secs` parameter:
	# first initialize your estimator
	# set max_runtime_secs to 10 seconds to limit how long the model can take to build
	cars_gbm = H2OGradientBoostingEstimator(max_runtime_secs = 10, ntrees = 10000, max_depth = 10, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	cars_gbm.auc(valid = True)