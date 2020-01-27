``nlambdas``
------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

This option specifies the number of lambdas to use during a lambda search. As such, this option is only available if ``lambda_search=TRUE``. 

``nlambdas`` works in conjunction with ``lambda_min_ratio``. The sequence of the :math:`\lambda` values is automatically generated as an exponentially decreasing sequence. It ranges from :math:`\lambda_{max}` (the smallest :math:`\lambda` so that the solution is a model with all 0s) to :math:`\lambda_{min} =` ``lambda_min_ratio`` :math:`\times` :math:`\lambda_{max}`.

H2O computes :math:`\lambda` models sequentially and in decreasing order, warm-starting the model (using the previous solution as the initial prediction) for :math:`\lambda_k` with the solution for :math:`\lambda_{k-1}`. By warm-starting the models, we get better performance. Typically models for subsequent :math:`\lambda` values are close to each other, so only a few iterations per :math:`\lambda` are needed (two or three). This also achieves greater numerical stability because models with a higher penalty are easier to compute. This method starts with an easy problem and then continues to make small adjustments. 

**Notes**: 

- ``lambda_min_ratio`` and ``nlambdas`` also specify the relative distance of any two lambdas in the sequence. This is important when applying recursive strong rules, which are only effective if the neighboring lambdas are "close" to each other. 
- When ``alpha`` > 0, the default value for ``lambda_min_ratio`` is :math:`1e^{-4}`, and the default value for ``nlambdas`` is 100. This gives a ratio of 0.912. For best results when using strong rules, keep the ratio close to this default. 
- When ``alpha=0``, the default value for ``nlamdas`` is set to 30 because fewer lambdas are needed for ridge regression.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `alpha <alpha.html>`__
- `lambda <lambda.html>`__
- `lambda_min_ratio <lambda_min_ratio.html>`__
- `lambda_search <lambda_search.html>`__

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

	# try using the `nlambas` parameter:
	# train your model
	boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      lambda_search = TRUE,
	                      nlambdas = 50)

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


	# try using the `nlambdas` parameter:
	# initialize the estimator then train the model
	boston_glm = H2OGeneralizedLinearEstimator(lambda_search = True, nlambdas = 50)
	boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(boston_glm.mse(valid=True))