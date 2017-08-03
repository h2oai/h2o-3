``nfolds``
---------------

- Available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost
- Hyperparameter: no


Description
~~~~~~~~~~~

This option specifies the number of folds to use for `cross-validation <../../cross-validation.html>`__. 

N-fold cross-validation is used to validate a model internally, i.e., estimate the model performance without having to sacrifice a validation split. Also, you avoid statistical issues with your validation split (it might be a “lucky” split, especially for imbalanced data). Good values for ``nfolds`` are generally from 5 to 10, but keep in mind that higher values result in higher computational cost. 

When specifying ``nfolds``, the algorithm will build ``nfolds`` +1 models. For example, if you specify ``nfolds=5``, then 6 models are built. The first 5 models (cross-validation models) are built on 80% of the training data, and a different 20% is held out for each of the 5 models. Then the main model is built on 100% of the training data. This main model is the model you get back from H2O in R, Python, and Flow.

All 5 cross-validation models contain training metrics (from the 80% training data) and validation metrics (from their 20% holdout/validation data). To compute their individual validation metrics, each of the 5 cross-validation models had to make predictions on 20% of the rows from the original training frame, and score against the true labels of the 20% holdout.

For the main model, this is how the cross-validation metrics are computed: The 5 holdout predictions are combined into one prediction for the full training dataset (i.e., predictions for every row of the training data, with the model making the prediction for a particular row not having seen that row during training). This “holdout prediction” is then scored against the true labels, and the overall cross-validation metrics are computed.

This approach has some implications. Scoring the holdout predictions freshly can result in different metrics than taking the average of the 5 validation metrics of the cross-validation models. For example, if the sizes of the holdout folds differ a lot (e.g., when a user-given ``fold_column`` is used), then the average should probably be replaced with a weighted average. Also, if the cross-validation models map to slightly different probability spaces, which can happen for small DL models that converge to different local minima, then the confused rank ordering of the combined predictions would lead to a significantly different AUC than the average.

When performing cross-validation, you can also specify either a ``fold_assignment``, which specifies the scheme to use for cross-validation, or you can specify a ``fold_column``, which indicates the column that contains the cross-validation fold index assignment per observation. In general, a ``fold_assignment`` is appropriate for datasets that are i.i.d. If the dataset requires custom grouping to perform meaningful cross-validation, then a "fold column" should be created and provided instead.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `fold_assignment <fold_assignment.html>`__
- `fold_column <fold_column.html>`__


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

	# set the number of folds for you n-fold cross validation:
	folds <- 5
	# folds <- 5

	# train a gbm using the nfolds parameter:
	cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = cars,
	                    nfolds = folds, seed = 1234)

	# print the auc for the cross-validated data
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

	# set the number of folds for you n-fold cross validation:
	folds = 5
	# folds = 5

	# initialize the estimator then train the model
	cars_gbm = H2OGradientBoostingEstimator(nfolds = folds, seed = 1234)
	cars_gbm.train(x=predictors, y=response, training_frame=cars)

	# print the auc for the cross-validated data
	cars_gbm.auc(xval=True)
	