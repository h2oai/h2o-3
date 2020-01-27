``binomial_double_trees``
-------------------------

- Available in: DRF
- Hyperparameter: no

Description
~~~~~~~~~~~

When building classification models, this option specifies to build twice as many internal trees as the number of trees (one per class). Enabling this option can lead to higher accuracy but lower speed times, while disabling this can result in faster model building. This option is disabled by default.

Note that ``ntrees=50`` by default, so specifying ``binomial_double_trees=TRUE`` without specifying a number of trees will result in 50 trees and 100 internal trees.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `ntrees <ntrees.html>`__

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
	cars.splits <- h2o.splitFrame(data =  cars, ratios = .8, seed = 1234)
	train <- cars.splits[[1]]
	valid <- cars.splits[[2]]

	# try using the `binomial_double_trees` (boolean parameter):
	car_drf <- h2o.randomForest(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid,
	                   binomial_double_trees = FALSE, seed = 1234)

	# print the auc and the number of trees built with binomial_double_trees turned off
	# see the difference between the number of trees and number of internal trees
	print(paste('without binomial_double_trees', h2o.auc(car_drf, valid = TRUE), sep = ": "))
	print(car_drf@model$model_summary$number_of_trees)
	print(car_drf@model$model_summary$number_of_internal_trees)
	      

	# try using the `binomial_double_trees` (boolean parameter):
	car_drf_2 <- h2o.randomForest(x = predictors, y = response, training_frame = train,
	                   validation_frame = valid,
	                   binomial_double_trees = TRUE, seed = 1234)

	# print the auc and the number of trees built with binomial_double_trees turned on
	# see the difference between the number of trees and number of internal trees
	print(paste('with binomial_double_trees', h2o.auc(car_drf_2, valid = TRUE), sep = ": "))
	print(car_drf_2@model$model_summary$number_of_trees)
	print(car_drf_2@model$model_summary$number_of_internal_trees)

   .. code-block:: python

	import h2o
	from h2o.estimators.random_forest import H2ORandomForestEstimator
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

	# try using the binomial_double_trees (boolean parameter):
	# Initialize and train a DRF
	cars_drf = H2ORandomForestEstimator(binomial_double_trees = False, seed = 1234)
	cars_drf.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc and the number of trees built with binomial_double_trees turned off
	print('without binomial_double_trees:', cars_drf.auc(valid=True))


	# Initialize and train a DRF
	cars_drf_2 = H2ORandomForestEstimator(binomial_double_trees = True, seed = 1234)
	cars_drf_2.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc and the number of trees built with binomial_double_trees turned on
	print('with binomial_double_trees:', cars_drf_2.auc(valid=True))



