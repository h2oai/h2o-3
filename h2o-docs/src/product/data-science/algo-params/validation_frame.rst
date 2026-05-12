``validation_frame``
--------------------

- Available in: GBM, DRF, Deep Learning, GLM, GAM, HGLM, PCA, GLRM, Naïve-Bayes, K-Means, Stacked Ensembles, AutoML, XGBoost, Uplift DRF, ModelSelection
- Hyperparameter: no

Description
~~~~~~~~~~~

Datasets are commonly split into training, testing, and validation sets. When splitting a dataset, the bulk of the data goes into the training dataset, with small portions held out for the testing and validation dataframes. 

While the ``training_frame`` is used to build the model, the ``validation_frame`` is used to compare against the adjusted model and evaluate the model's accuracy. Typically, the model will include sampled data which will then be compared against the validation frame's unsampled data. The recommended process is to train on the training set and stop early based on the validation set (and/or cross-validation). When you find a good model, you score it once on the test set to estimate the generalization error.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `model_id <model_id.html>`_
- `training_frame <training_frame.html>`__

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

		# try using the `validation_frame` parameter:
		# train your model, where you specify your 'x' predictors, your 'y' the response column
		# training_frame and validation_frame
		cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
		                    validation_frame = valid, seed = 1234)

		# print the auc for your model
		print(h2o.auc(cars_gbm, valid = TRUE))

   .. code-tab:: python

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

		# split into train and validation sets
		train, valid = cars.split_frame(ratios = [.8], seed = 1234)

		# try using the `validation_frame` parameter:
		# first initialize your estimator
		cars_gbm = H2OGradientBoostingEstimator(seed = 1234)

		# then train your model, where you specify your 'x' predictors, your 'y' the response column
		# training_frame and validation_frame
		cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		cars_gbm.auc(valid=True)