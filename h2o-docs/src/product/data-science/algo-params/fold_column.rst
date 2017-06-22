``fold_column``
---------------

- Available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, AutoML, XGBoost
- Hyperparameter: no


Description
~~~~~~~~~~~

When performing N-fold cross validation, you can specify to split the data into subsets using a `fold_assignment <fold_assignment.html>`__. A fold assignment is suitable for datasets that are i.i.d. 

If your dataset requires custom grouping to perform meaningful cross-validation, then a "fold column" should be created and provided instead. The ``fold_column`` option specifies the column in the dataset that contains the cross-validation fold index assignment per observation. The fold column can include integers or categorical values. When specified, the algorithm uses that column’s values to split the data into subsets.

**Notes** 

* The fold column must exist in the training data. 
* The fold column that is specified cannot be specified in ``ignored_columns``, ``response_colum``, ``weights_column`` or ``offset_column``.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `fold_assignment <fold_assignment.html>`__
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

	# create a fold column with 5 folds
	# randomly assign fold numbers 0 through 4 for each row in the column
	fold_numbers <- h2o.kfold_column(cars, nfolds=5)

	# rename the column "fold_numbers"
	names(fold_numbers) <- "fold_numbers"

	# print the fold_assignment column
	print(fold_numbers)

	# append the fold_numbers column to the cars dataset
	cars <- h2o.cbind(cars,fold_numbers)

	# try using the fold_column parameter:
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = cars,
	                    fold_column="fold_numbers", seed = 1234)

	# print the auc for your model
	print(h2o.auc(cars_gbm, xval = TRUE))

   .. code-block:: python

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

	# create a fold column with 5 folds
	# randomly assign fold numbers 0 through 4 for each row in the column
	fold_numbers = cars.kfold_column(n_folds = 5, seed = 1234)

	# rename the column "fold_numbers"
	fold_numbers.set_names(["fold_numbers"])

	# append the fold_numbers column to the cars dataset
	cars = cars.cbind(fold_numbers)

	# print the fold_assignment column
	print(cars['fold_numbers'])

	# initialize the estimator then train the model
	cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
	cars_gbm.train(x=predictors, y=response, training_frame=cars, fold_column="fold_numbers")

	# print the auc for the cross-validated data
	cars_gbm.auc(xval=True)
