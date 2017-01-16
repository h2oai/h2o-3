``lambda_search``
-----------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Setting ``lambda_search`` to TRUE enables efficient and automatic search for the optimal value of the ``lambda`` parameter. When enabled, GLM will first fit a model with maximum regularization (highest lambda value) and then keep decreasing it at each step until it reaches the minimum lambda or until overfitting occurs. The resulting model is based on the best lambda value. 

Note that GLM will automatically calculate the minimum lambda value unless a value for ``lambda_min_ratio`` is specified. In that case, the specified value becomes the minimum lambda value. If you enter one or more values for ``lambda``, then the lambda search is performed over only those provided lambdas. 

When looking for a sparse solution (``alpha`` > 0), lambda search can also be used to efficiently handle very wide datasets because it can filter out inactive predictors (noise) and only build models for a small subset of predictors. A possible use case for lambda search is to run it on a dataset with many predictors but limit the number of active predictors to a relatively small value. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `alpha <alpha.html>`__
- `lambda <lambda.html>`__
- `lambda_min_ratio <lambda_min_ratio.html>`__
- `max_active_predictors <max_active_predictors.html>`__
- `nlambdas <nlambdas.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the boston dataset:
	# this dataset looks at features of the boston suburbs and predicts median housing prices
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
	boston <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

	# set the predictor names and the response column name
	predictors <- colnames(boston)[1:13]
	# set the response column to "medv", the median value of owner-occupied homes in $1000's
	response <- "medv"

	# convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
	boston["chas"] <- as.factor(boston["chas"])

	# split into train and validation sets
	boston.splits <- h2o.splitFrame(data =  boston, ratios = .8)
	train <- boston.splits[[1]]
	valid <- boston.splits[[2]]

	# try using the `lambda_search` parameter (boolean):
	# train your model
	boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      lambda_search = TRUE)

	# print the mse for the validation data
	print(h2o.mse(boston_glm, valid=TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the boston dataset:
	# this dataset looks at features of the boston suburbs and predicts median housing prices
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
	boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

	# set the predictor names and the response column name
	predictors = boston.columns[:-1]
	# set the response column to "medv", the median value of owner-occupied homes in $1000's
	response = "medv"

	# convert the chas column to a factor (chas = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise))
	boston['chas'] = boston['chas'].asfactor()

	# split into train and validation sets
	train, valid = boston.split_frame(ratios = [.8])

	# try using the `lambda_search` parameter (boolean):
	# initialize the estimator then train the model
	boston_glm = H2OGeneralizedLinearEstimator(lambda_search = True)
	boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(boston_glm.mse(valid=True))
