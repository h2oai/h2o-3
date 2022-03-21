``check_constant_response``
---------------------------

- Available in: GBM, DRF, Uplift DRF
- Hyperparameter: no

Description
~~~~~~~~~~~

This option checks if a response column is a constant value. If this option is enabled (default), then an exception is thrown if the response column is a constant value. If this option is disabled, then the model will train regardless of the response column being a constant value or not.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the iris dataset: 
		train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
		train$constantCol <- 1

		# Build a GBM model. This should run successfully when 
		# check_constant_response is set to false.
		iris_gbm_initial <- h2o.gbm(y = 6, x = 1:5, training_frame = train)


   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
		h2o.init()

		# import the iris dataset: 
		train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
		train["constantCol"] = 1

		# Build a GBM model. This should run successfully when 
		# check_constant_response is set to false.
		my_gbm = H2OGradientBoostingEstimator(check_constant_response=False)
		my_gbm.train(x=list(range(1,5)), y="constantCol", training_frame=train)

