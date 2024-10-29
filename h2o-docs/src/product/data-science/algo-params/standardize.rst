``standardize``
---------------

- Available in: Deep Learning, GLM, GAM, HGLM, K-Means, ANOVAGLM, ModelSelection
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies whether to standardizes numeric columns to have zero mean and unit variance. Enabling this option produces standardized coefficient magnitudes in the model output. 

Standardization is highly recommended. As such, this option is enabled by default. If you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. Only advanced users should disable this option. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

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
		boston_splits <- h2o.splitFrame(data =  boston, ratios = 0.8)
		train <- boston_splits[[1]]
		valid <- boston_splits[[2]]

		# try using the `standardize` parameter:
		boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
		                      validation_frame = valid,
		                      standardize = TRUE)

		# print the mse for the validation data
		print(h2o.mse(boston_glm, valid = TRUE))
	   
   .. code-tab:: python

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

		# try using the `standardize` parameter:
		# initialize the estimator then train the model
		boston_glm = H2OGeneralizedLinearEstimator(standardize = True)
		boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the mse for the validation data
		print(boston_glm.mse(valid=True))
