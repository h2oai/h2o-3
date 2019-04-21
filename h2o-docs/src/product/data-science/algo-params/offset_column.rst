``offset_column``
-----------------

- Available in: GBM, Deep Learning, GLM, CoxPH
- Hyperparameter: no


Description
~~~~~~~~~~~

An offset is a per-row “bias value” that is used during model training. For Gaussian distributions, offsets can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. 

When used with distributions/family-link functions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For example, you may have fitted some other (logistic) regression using other variables (and data), and now you want to see if the present variables can add anything. So you use the predicted logit from the other model as an offset in. To get the logit from a predicted probability in H2O, you can use this expression: :math:`\text{logit} = \text{log}\big(\frac{prob}{(1-prob)}\big)`.

**Notes**: 

- This option is not applicable for multinomial distributions
- The offset column cannot be the same as the `fold_column <fold_column.html>`__. 
- This option can be specified in XGBoost, but it is not supported.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__
- `family <family.html>`__
- `link <link.html>`__
- `weights_column <weights_column.html>`__
- `y <y.html>`__

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

	# create a new offset column by taking the log of the response column
	boston["offset"] <- log(boston["medv"])

	# split into train and validation sets
	boston.splits <- h2o.splitFrame(data =  boston, ratios = .8, seed = 1234) 
	train <- boston.splits[[1]]  
	valid <- boston.splits[[2]] 

	# try using the `offset_column` parameter:
	# train your model, where you specify the offset_column
	boston_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, 
	                   validation_frame = valid,
	                   offset_column = "offset",
	                   seed = 1234) 

	# print the mse for validation set
	print(h2o.mse(boston_gbm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

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

	# create a new offset column by taking the log of the response column
	boston["offset"] = boston["medv"].log()

	# split into train and validation sets
	train, valid = boston.split_frame(ratios = [.8], seed = 1234)

	# try using the `offset_column` parameter:
	# initialize the estimator then train the model
	boston_gbm = H2OGradientBoostingEstimator(offset_column = "offset", seed = 1234)
	boston_gbm.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

	# print the mse for validation set
	boston_gbm.mse(valid=True)
