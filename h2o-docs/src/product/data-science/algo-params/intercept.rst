``intercept``
-------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``intercept`` command allows you to specify whether a single constant term should to be included in the model. By including a constant term (i.e., an "intercept" or "bias"), we ensure that the model is to be unbiased--i.e., the mean of the residuals will be exactly zero. 

The intercept term adjusts all predictions up or down by a constant amount, i.e. it is the predicted value when all inputs are exactly 0. It can be excluded (forced to equal 0) by setting ``intercept=FALSE``. 

In GLM, the inverse of the link function is applied to obtain the final predictions (targets in regression and probabilities in classification). Excluding the intercept term can negatively impact measured model quality, but it is appropriate when a given linear model should definitely predict 0 when all inputs are 0 (before the application of the inverse of the link function to obtain the final predictions). You may also want to exclude the intercept term to choose a simpler model when appropriate and move away from overfitting. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `family <family.html>`__
- `link <link.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] <- as.factor(iris['class'])

	# set the predictor names and the response column name
	predictors <- colnames(iris)[-length(iris)]
	response <- 'class'

	# split into train and validation
	iris.splits <- h2o.splitFrame(data = iris, ratios = .8)
	train <- iris.splits[[1]]
	valid <- iris.splits[[2]]

	# try using the `intercept` parameter:
	iris_glm <- h2o.glm(x = predictors, y = response, family = 'multinomial', 
	                    intercept = TRUE, training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	print(h2o.logloss(iris_glm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] = iris['class'].asfactor()

	# set the predictor names and the response column name
	predictors = iris.columns[:-1]
	response = 'class'

	# split into train and validation sets
	train, valid = iris.split_frame(ratios = [.8])

	# try using the `intercept` parameter:
	# Initialize and train a GLM
	iris_glm = H2OGeneralizedLinearEstimator(family = 'multinomial', intercept = True)
	iris_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	iris_glm.logloss(valid = True)
