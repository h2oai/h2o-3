``fold_assignment``
-------------------

- Available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost
- Hyperparameter: no


Description
~~~~~~~~~~~

This option specifies the scheme to use for `cross-validation <../../cross-validation.html>`__ fold assignment. This option is only applicable if a value for ``nfolds`` is specified and a ``fold_column`` is not specified. Options include:

- Auto: Allow the algorithm to automatically choose an option. Auto currently uses Random.
- Random: Randomly split the data into ``nfolds`` pieces. (Default) 
- Modulo: Performs `modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__ operation when splitting the folds. 
- Stratified: Stratifies the folds based on the response variable for classification problems.  

Keep the following in mind when specifying a fold assignment for your data:

- **Random** is best for large datasets, but can lead to imbalanced samples for small datasets.
- **Modulo** is a simple deterministic way to evenly split the dataset into the folds and does not depend on the seed.
- Specifying **Stratified** will attempt to evenly distribute observations from the different classes to all sets when splitting a dataset into training and validation. This can be useful if there are many classes and the dataset is relatively small.
- Note that all three options are only suitable for datasets that are i.i.d. If the dataset requires custom grouping to perform meaningful cross-validation, then a ``fold_column`` should be created and provided instead.
- In general, when comparing multiple models using validation sets, ensure that you use the same validation set for all models. When performing cross-validation, specify a seed for all models, or specify **Modulo** for the ``fold_assignment``. This ensures that the cross-validation folds are the same, and eliminates the noise that can come from, for example, the Random fold assignment.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `fold_column <fold_column.html>`__
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

	# try using the fold_assignment parameter:
	# note you must set nfolds to use this parameter
	assignment_type <- "Random"
	# you can also try "Auto", "Modulo", and "Stratified"

	# train a GBM
	car_gbm <- h2o.gbm(x = predictors, y = response, training_frame = cars,
	                   fold_assignment = assignment_type,
	                   nfolds = 5, seed = 1234)

	# print the auc for your validation data
	print(h2o.auc(car_gbm, xval = TRUE))




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

	# try using the fold_assignment parameter:
	# note you must set nfolds to use this parameter
	assignment_type = "Random"
	# you can also try "Auto", "Modulo", and "Stratified"

	# Initialize and train a GBM
	cars_gbm = H2OGradientBoostingEstimator(fold_assignment = assignment_type, nfolds = 5, seed = 1234)
	cars_gbm.train(x = predictors, y = response, training_frame = cars)

	# print the auc for the validation data
	cars_gbm.auc(xval=True)