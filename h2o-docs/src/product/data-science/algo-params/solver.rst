``solver``
----------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``solver`` option allows you to specify the solver method to use in GLM. When specifying a solver, the optimal solver depends on the data properties and prior information regarding the variables (if available). In general, the data are considered sparse if the ratio of zeros to non-zeros in the input matrix is greater than 10. The solution is sparse when only a subset of the original set of variables is intended to be kept in the model. In a dense solution, all predictors have non-zero coefficients in the final model.

In GLM, you can specify one of the following solvers:

- IRLSM: Iteratively Reweighted Least Squares Method
- L_BFGS: Limited-memory Broyden-Fletcher-Goldfarb-Shanno algorithm
- COORDINATE_DESCENT: Coordinate Decent
- COORDINATE_DESCENT_NAIVE: Coordinate Decent Naive
- AUTO: Sets the solver based on given data and parameters (default)

Detailed information about each of these options is available in the `Solvers <../glm.html#solvers>`__ section. The bullets below describe GLM chooses the solver when ``solver=AUTO``:

-  If there are more than 5k active predictors, GLM uses L_BFGS.
-  If ``family=multinomial`` and ``alpha=0`` (ridge or no penalty), GLM uses L_BFGS.
-  If lambda search is enabled, GLM uses COORDINATE_DESCENT.
-  If your data has upper/lower bounds and no proximal penlaty, GLM uses COORDINATE_DESCENT.
-  If none above is true, then GLM defaults to IRLSM. This is because COORDINATE_DESCENT works much better with lambda search.

Below are some general guidelines to follow when specifying a solver.  

- L_BFGS works much better for L2-only multininomial and if you have too many active predictors. 
- You must use IRLSM if you have p-values. 
- IRLSM and COORDINATE_DESCENT share the same path (i.e., they both compute the same gram matrix), they just solve it differently.
- Use COORDINATE_DESCENT if you have less than 5000 predictors and L1 penalty.
- COORDINATE_DESCENT performs better when ``lambda_search`` is enabled. Also with bounds, it tends to get a higher accuracy.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `alpha <alpha.html>`__
- `lambda <lambda.html>`__
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

	# try using the `solver` parameter:
	boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      solver = 'IRLSM')

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

	# try using the `solver` parameter:
	# initialize the estimator then train the model
	boston_glm = H2OGeneralizedLinearEstimator(solver = 'irlsm')
	boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(boston_glm.mse(valid=True))

