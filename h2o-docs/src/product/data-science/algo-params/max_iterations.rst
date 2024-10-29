``max_iterations``
------------------

- Available in: GLM, GAM, HGLM, PCA, GLRM, K-Means, CoxPH, ANOVAGLM, ModelSelection
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the maximum allowed number of iterations (passes over data) during model training. This value must be between 1 and 1e6, inclusive.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

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

		# split into train and validation
		cars_splits <- h2o.splitFrame(data = cars, ratios = 0.8)
		train <- cars_splits[[1]]
		valid <- cars_splits[[2]]

		# try using the `max_iterations` parameter:
		car_glm <- h2o.glm(x = predictors, y = response, family = 'binomial', training_frame = train, validation_frame = valid,
		                   max_iterations = 50)

		# print the auc for your validation data
		print(h2o.auc(car_glm, valid = TRUE))

   .. code-tab:: python

		import h2o
		from h2o.estimators.glm import H2OGeneralizedLinearEstimator
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
		train, valid = cars.split_frame(ratios = [.8])

		# try using the `max_iterations` parameter:
		# Initialize and train a GLM
		cars_glm = H2OGeneralizedLinearEstimator(family = 'binomial', max_iterations = 50)
		cars_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		cars_glm.auc(valid = True)
	