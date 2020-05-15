``interactions``
----------------

- Available in: GLM, GAM, CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

By default, interactions between predictor columns are expanded and computed on the fly as GLM iterates over dataset. The ``interactions`` option allows you to enter a list of predictor column indices that should interact. 

Note that adding a list of interactions to a model changes the interpretation of all of the coefficients. For example, a typical predictor has the form ‘response ~ terms’ where ‘response’ is the (numeric) response vector, and ‘terms’ is a series of terms that specify a linear predictor for ‘response’. For ‘binomial’ and ‘quasibinomial’ families in GLM, the response can also be specified as a ‘factor’ (when the first level denotes failure and all other levels denote success) or as a two-column matrix with the columns giving the numbers of successes and failures. 

Interactions can be specified between two categorical columns, between two numeric columns, or between a mix of categorical and numerical columns. When entered, all pairwise combinations of predictor column indices will be computed for that list. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Examples
~~~~~~~~

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

		# try using the `interactions` parameter:
		# add the interaction terms between 'crim' and 'dis' (per capita crime rate by town and 
		# the weighted distances to five Boston employment centres)
		# initialize the estimator then train the model
		interactions_list = c('crim', 'dis')
		boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
		                      interactions = interactions_list,
		                      validation_frame = valid)

		# print the mse for validation set
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

		# take a look at the boston columns:
		print(boston.columns)

		# try using the `interactions` parameter:
		# add the interaction terms between 'crim' and 'dis' (per capita crime rate by town and 
		# the weighted distances to five Boston employment centres)
		# initialize the estimator then train the model
		interactions_list = ['crim', 'dis']
		boston_glm = H2OGeneralizedLinearEstimator(interactions = interactions_list)
		boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the mse for the validation data
		print(boston_glm.mse(valid=True))
