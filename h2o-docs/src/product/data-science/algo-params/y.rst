``y``
-----
- Available in: GBM, DRF, Deep Learning, GLM, GAM, HGLM, Naïve-Bayes, Stacked Ensembles, AutoML, XGBoost, Aggregator, Uplift DRF, AdaBoost, Decision Tree, ANOVAGLM, ModelSelection
- Hyperparameter: no


Description
~~~~~~~~~~~

Use this option to specify a response column (y-axis). The response column is the column that you are attempting to predict. For example, based on a set of parameters in a training dataset, will a new customber be more or less likely to purchase a product? Or based on some known variables, what is the likelihood that a flight will be delayed? In both cases, a model can be applied to a training frame and to a validation frame to predict the likely response.  

**Response Columns with DL and GBM Distribution**

Response columns can be numeric or categorical, and they can be binomial or multiomial. If you are specifying a distribution type in DL or GBM, however, then keep in mind the following when defining a response column:

- If the distribution is ``bernoulli``, the the response column must be 2-class categorical
- If the distribution is ``multinomial``, the response column must be categorical.
- If the distribution is ``poisson``, the response column must be numeric.
- If the distribution is ``laplace``, the response column must be numeric.
- If the distribution is ``tweedie``, the response column must be numeric.
- If the distribution is ``gaussian``, the response column must be numeric.
- If the distribution is ``huber``, the response column must be numeric.
- If the distribution is ``gamma``, the response column must be numeric.
- If the distribution is ``quantile``, the response column must be numeric.

**Response Columns with GLM Family**

In GLM, you can specify one of the following family options based on the response column type:

- ``gaussian``: The data must be numeric (Real or Int). This is the default family.
- ``binomial``: The data must be categorical 2 levels/classes or binary (Enum or Int).
- ``quasibinomial``: The data must be numeric.
- ``multinomial``: The data can be categorical with more than two levels/classes (Enum).
- ``poisson``: The data must be numeric and non-negative (Int).
- ``gamma``: The data must be numeric and continuous and positive (Real or Int).
- ``tweedie``: The data must be numeric and continuous (Real) and non-negative.
- ``ordinal``: Requires a categorical response with at least 3 levels. (For 2-class problems use family="binomial".)

**Notes**: 

- The response column cannot be the same as the `fold_column <fold_column.html>`__. 
- For supervised learning, the response column cannot be the same as the `weights_column <weights_column.html>`__, and the response column must exist in both the training frame and in the validation frame. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__
- `family <family.html>`__
- `offset_column <offset_column.html>`__
- `weights_column <weights_column.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the cars dataset: 
		# this dataset is used to classify whether or not a car is economical based on 
		# the car's displacement, power, weight, and acceleration, and the year it was made 
		cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# convert response column to a factor
		cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

		# set the predictor names and the response column name
		predictors <- c("displacement", "power", "weight", "acceleration", "year")
		response <- "economy_20mpg"

		# split into train and validation sets
		cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
		train <- cars_split[[1]]
		valid <- cars_split[[2]]

		# try using the `y` parameter:
		# train your model, where you specify your 'x' predictors, your 'y' the response column
		# training_frame and validation_frame
		cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
		                    validation_frame = valid, seed = 1234)

		# print the auc for your model
		print(h2o.auc(cars_gbm, valid = TRUE))

   .. code-tab:: python

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

		# split into train and validation sets
		train, valid = cars.split_frame(ratios = [.8], seed = 1234)

		# try using the `y` parameter:
		# first initialize your estimator
		cars_gbm = H2OGradientBoostingEstimator(seed = 1234)

		# then train your model, where you specify your 'x' predictors, your 'y' the response column
		# training_frame and validation_frame
		cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		cars_gbm.auc(valid=True)