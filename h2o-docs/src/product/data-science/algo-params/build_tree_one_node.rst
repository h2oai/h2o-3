``build_tree_one_node``
-----------------------

- Available in: GBM, DRF, Isolation Forest
- Hyperparameter: no

Description
~~~~~~~~~~~

Enable this option to specify that the algorithm will run on a single node. This option is suitable for small datasets as there is no network overhead, but fewer CPUs are used. 

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

	# try using the `build_tree_one_node` parameter:
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, build_tree_one_node = TRUE ,seed = 1234)

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

	# try turning on the `build_tree_one_node` parameter:
	# initialize your estimator
	cars_gbm = H2OGradientBoostingEstimator(build_tree_one_node = True, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	cars_gbm.auc(valid=True)