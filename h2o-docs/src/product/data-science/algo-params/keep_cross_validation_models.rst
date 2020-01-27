.. _keep_cross_validation_models:

``keep_cross_validation_models``
--------------------------------

- Available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost, AutoML
- Hyperparameter: yes for AutoML, no for other algorithms

Description
~~~~~~~~~~~

N-fold cross-validation is used to validate a model internally, i.e., to estimate the model performance without having to sacrifice a validation split. When building cross-validated models, H2O builds ``nfolds+1`` models: ``nfolds`` cross-validated models and 1 overarching model over all of the training data. For example, if you specify ``nfolds=5``, then 6 models are built. The first 5 models are the cross-validation models and are built on 80% of the training data. You can save each of these models for further inspection by enabling the ``keep_cross_validation_models`` option. Note that this option is enabled by default except for AutoML.

More information is available in the `Cross-Validation <../../cross-validation.html>`__ section. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `keep_cross_validation_predictions <keep_cross_validation_predictions.html>`__
- `keep_cross_validation_fold_assignment <keep_cross_validation_fold_assignment.html>`__
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

	# try using the `keep_cross_validation_models` (boolean parameter):
	# train your model, set nfolds parameter
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    nfolds = 5, keep_cross_validation_models = TRUE, seed = 1234)

	# retrieve the list of cross-validation models
	cars_gbm_cv_models <- h2o.cross_validation_models(cars_gbm)

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

	# try using the `keep_cross_validation_models` (boolean parameter):
	# first initialize your estimator, set nfolds parameter
	cars_gbm = H2OGradientBoostingEstimator(keep_cross_validation_models = True, nfolds = 5, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train)

	# retrieve the cross-validation models
	cars_gbm_cv_models = cars_gbm.cross_validation_models()
