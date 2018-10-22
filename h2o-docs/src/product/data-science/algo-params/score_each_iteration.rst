.. _score_each_iteration:

``score_each_iteration``
------------------------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, XGBoost, Isolation Forest
- Hyperparameter: no


Description
~~~~~~~~~~~

This option allows you to specify to score during each iteration of model training. This option is useful when used with early stopping and attempting to make early stopping reproducible. When used with early stopping, the ``stopping_rounds`` option applies to the number of scoring iterations that H2O has performed, so regular scoring iterations of small size help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O's assessment of a reasonable ratio of training iterations to scoring time, which often results in inconsistent scoring gaps. 

This option is disabled by default. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `score_tree_interval <score_tree_interval.html>`__
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

	# try using the `score_each` parameter (boolean parameter):
	# set ntrees = 55 and print out score for all 55 trees
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid, score_each_iteration = TRUE,
	                    ntrees = 55, seed = 1234)

	# print the auc for your model
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

	# try turning on the `score_each_iteration` parameter (boolean parameter):
	# initialize your estimator
	# set ntrees = 55 and print out score for all 55 trees
	cars_gbm = H2OGradientBoostingEstimator(score_each_iteration = True, ntrees = 55, seed = 1234)

	# then train your model
	cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the model score every 5 trees
	cars_gbm.scoring_history()
