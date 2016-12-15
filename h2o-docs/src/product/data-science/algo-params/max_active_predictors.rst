``max_active_predictors``
-------------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

This option limits the number of active predictors. (Note that the actual number of non-zero predictors in the model is going to be slightly lower). It is useful when obtaining a sparse solution to avoid costly computation of models with too many predictors.

When using the :math:`\lambda_1` penalty with lambda search, this option will stop the search before it completes. Models built at the beginning of the lambda search have higher lambda alues, consider fewer predictors, and take less time to calculate the model. Models built at the end of the lambda search have lower lambda values, incorporate more predictors, and take a longer time to calculate the model. Set the ``nlambdas`` parameter for a lambda search to specify the number of models attempted across the search. 

**Default Value**

- If ``solver`` is IRLSM, COORDINATE_DESCENT, or COORDINATE_DESCENT_NAIVE, then ``max_active_predictors`` defaults to 5000.
- If lambda search is disabled, ``alpha`` < 0, ``solver`` is AUTO, and you have less than 5000 active predictors, then the ``solver`` will be IRLSM, and ``max_active_predictors`` defaults to 5000.
- If you run lambda search with ``alpha`` > 0, and ``solver`` is AUTO, then ``solver`` will be COORDINATE_DESCENT, and ``max_active_predictors`` will default to 5000. 
- For all other scenarios, ``max_active_predictors`` will default to 100000000.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `nlambdas <nlambdas.html>`__
- `solver <solver.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the higgs dataset:
	# This dataset is used to classify whether or not a signal process produces a Higgs bosons.
	# original data can be found at https://archive.ics.uci.edu/ml/datasets/HIGGS
	higgs <-  h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv")

	# set the predictor names and the response column name
	predictors <- colnames(higgs)[-1]
	response <- "response"

	# split into train and validation
	higgs.splits <- h2o.splitFrame(data =  higgs, ratios = .8)
	train <- higgs.splits[[1]]
	valid <- higgs.splits[[2]]

	# try using the `max_active_predictors` parameter:
	higgs_glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, 
	                        max_active_predictors = 200)

	# print the AUC for the validation data
	print(h2o.auc(higgs_glm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the higgs dataset:
	# This dataset is used to classify whether or not a signal process produces a Higgs bosons.
	# original data can be found at https://archive.ics.uci.edu/ml/datasets/HIGGS
	higgs= h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv")

	# set the predictor names and the response column name
	predictors = higgs.names
	predictors.remove('response')
	# The response 
	response = "response"

	# split into train and validation sets
	train, valid = higgs.split_frame(ratios = [.8])

	# try using the `max_active_predictors` parameter:
	# initialize the estimator then train the model
	higgs_glm = H2OGeneralizedLinearEstimator(family = 'binomial', max_active_predictors = 200)
	higgs_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(higgs_glm.auc(valid=True))
