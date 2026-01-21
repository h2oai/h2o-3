``x``
-----

- Available in: GBM, DRF, Deep Learning, GLM, GAM, HGLM, PCA, GLRM, Naïve-Bayes, K-Means, Stacked Ensembles, AutoML, XGBoost, Uplift DRF, AdaBoost, Decision Tree, ANOVAGLM, ModelSelection
- Hyperparameter: no

Description
~~~~~~~~~~~

There may be instances when your dataset includes more information than you want to be included when building a model. Use the ``x`` parameter to specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

Note that this is a strict parameter that takes into account the exact string of the column name. So, for example, if your dataset includes one column named **Type** and another column named **Types**, and you specify ``x=["type"]``, then the algorithm will only include the **Type** column and will ignore the **Types** column.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `ignore_const_cols <ignore_const_cols.html>`__
- `y <y.html>`__

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

		# try using the `y` parameter:
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

		# try using the `y` parameter:
		# first initialize your estimator
		cars_gbm = H2OGradientBoostingEstimator(seed = 1234)

		# then train your model, where you specify your 'x' predictors, your 'y' the response column
		# training_frame and validation_frame
		cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		cars_gbm.auc(valid=True)
