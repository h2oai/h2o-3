.. _keep_cross_validation_fold_assignment:

``keep_cross_validation_fold_assignment``
-----------------------------------------

- Available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost, AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

When performing cross-validation, data is split into subsets using either the ``fold_column`` or ``fold_assignment`` parameter. You can then specify to save each of the outputted fold assignments by enabling the ``keep_cross_validation_fold_assignment`` option. Note that this option is disabled by default.

More information about cross-validation is available in the `Cross-Validation <../../cross-validation.html>`__ section. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `fold_assignment <fold_assignment.html>`__
- `fold_column <fold_column.html>`__
- `keep_cross_validation_models <keep_cross_validation_models.html>`__
- `keep_cross_validation_predictions <keep_cross_validation_predictions.html>`__
- `nfolds <nfolds.html>`__


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

	# try using the ` keep_cross_validation_fold_assignment` (boolean parameter):
	# train your model, set nfolds parameter
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    nfolds = 5,  keep_cross_validation_fold_assignment= TRUE, seed = 1234)

	# retrieve the cross-validation fold assignment
	h2o.cross_validation_fold_assignment(cars_gbm)


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

	# try using the ` keep_cross_validation_fold_assignment` (boolean parameter):
	# first initialize your estimator, set nfolds parameter
	cars_gbm = H2OGradientBoostingEstimator(keep_cross_validation_fold_assignment = True, nfolds = 5, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train)

	# retrieve the cross-validation fold assignment
	cars_gbm.cross_validation_fold_assignment()