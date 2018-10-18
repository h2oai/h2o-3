.. _score_tree_interval:

``score_tree_interval``
------------------------

- Available in: GBM, DRF, XGBoost, Isolation Forest
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``score_tree_interval`` option specifies to score the model after this many trees. This option is useful when used with early stopping and attempting to make early stopping reproducible. It can be disabled by setting this value to 0.

When specifying early stopping parameters, the ``stopping_rounds`` option applies to the number of scoring intervals or iterations (with ``score_each_iteration``) H2O has performed, so regular scoring intervals of small size help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O’s assessment of a reasonable ratio of training time to scoring time, which often results in inconsistent scoring gaps. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `score_each_iteration <score_each_iteration.html>`__
- `stopping_metric <stopping_metric.html>`__
- `stopping_rounds <stopping_rounds.html>`__
- `stopping_tolerance <stopping_tolerance.html>`__


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

	# try using the `score_tree_interval` parameter:
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, score_tree_interval = 5, seed = 1234)

	# print the model score every 5 trees
	print(h2o.scoreHistory(cars_gbm))

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

	# try turning on the `score_tree_interval` parameter:
	# initialize your estimator
	cars_gbm = H2OGradientBoostingEstimator(score_tree_interval = 5, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the model score every 5 trees
	cars_gbm.scoring_history()