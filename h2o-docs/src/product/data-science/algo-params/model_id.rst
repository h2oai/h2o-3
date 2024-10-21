``model_id``
------------

- Available in: GBM, DRF, Deep Learning, GLM, GAM, HGLM, PCA, GLRM, Naïve-Bayes, K-Means, Word2Vec, Stacked Ensembles, XGBoost, Aggregator, CoxPH, Isolation Forest, Extended Isolation Forest, Uplift DRF, AdaBoost, Decision Tree, ANOVAGLM, ModelSelection
- Hyperparameter: no

Description
~~~~~~~~~~~

When building a model, H2O automatically generates a destination key as a unique identifier for the model. You can optionally use this option to specify a custom name for your model. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `training_frame <training_frame.html>`__
- `validation_frame <validation_frame.html>`__


Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# convert response column to a factor
		cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

		# set the predictor names and the response column name
		predictors <- c("displacement", "power", "weight", "acceleration", "year")
		response <- "economy_20mpg"

		# split into train and validation sets
		cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
		train <- cars_split[[1]]
		valid <- cars_split[[2]]

		# try using the `model_id` parameter:
		# train your model
		cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
		                    validation_frame = valid, model_id = "first_model", seed = 1234)

		# print the model id
		cars_gbm@model_id

		# the model_id can also be used with checkpointing to continue training

   .. code-tab:: python

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

		# try using the `model_id` parameter:
		# first initialize your estimator
		cars_gbm = H2OGradientBoostingEstimator(model_id = "first_model", seed = 1234)

		# training_frame and validation_frame
		cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the model id
		cars_gbm.model_id

		# the model_id can also be used with checkpointing to continue training