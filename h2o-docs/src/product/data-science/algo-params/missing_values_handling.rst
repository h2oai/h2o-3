``missing_values_handling``
---------------------------

- Available in: Deep Learning, GLM, GAM, HGLM, ANOVAGLM, ModelSelection
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option is used to specify the way that the algorithm will treat missing values. In H2O, the Deep Learning, GLM, and GAM algorithms will either skip or mean-impute rows with NA values. The GLM and GAM algorithms can also use plug_values, which allows you to specify a single-row frame containing values that will be used to impute missing values of the training/validation frame. These three algorithms default to MeanImputation. Note that in Deep Learning, unseen categorical variables are imputed by adding an extra “missing” level. In GLM and GAM, unseen categorical levels are replaced by the most frequent level present in training (mod).
 
The fewer the NA values in your training data, the better. Always check degrees of freedom in the output model. Degrees of freedom is the number of observations used to train the model minus the size of the model (i.e., the number of features). If this number is much smaller than expected, it is likely that too many rows have been excluded due to missing values:

- If you have few columns with many NAs, you might accidentally be losing all your rows, so its better to exclude (skip) them.
- If you have many columns with a small fraction of uniformly distributed missing values, every row will likely have at least one missing value. In this case, impute the NAs (e.g., substitute the NAs with mean values) before modeling. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `plug_values <plug_values.html>`__

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

		# insert missing values at random (this method happens inplace)
		h2o.insertMissingValues(boston)

		# check the number of missing values
		print(paste("missing:", sum(is.na(boston)), sep = ", "))

		# try using the `missing_values_handling` parameter:
		boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
		                      missing_values_handling = "Skip",
		                      validation_frame = valid)

		# print the mse for the validation data
		print(h2o.mse(boston_glm, valid = TRUE))

		# grid over `missing_values_handling`
		# select the values for `missing_values_handling` to grid over
		hyper_params <- list( missing_values_handling = c("Skip", "MeanImputation") )

		# this example uses cartesian grid search because the search space is small
		# and we want to see the performance of all models. For a larger search space use
		# random grid search instead: {'strategy': "RandomDiscrete"}

		# build grid search with previously made GLM and hyperparameters
		grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
		                 algorithm = "glm", grid_id = "boston_grid", hyper_params = hyper_params,
		                 search_criteria = list(strategy = "Cartesian"))

		# Sort the grid models by mse
		sorted_grid <- h2o.getGrid("boston_grid", sort_by = "mse", decreasing = FALSE)
		sorted_grid
   
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

		# insert missing values at random (this method happens inplace)
		boston.insert_missing_values()

		# check the number of missing values
		print('missing:', boston.isna().sum())

		# split into train and validation sets
		train, valid = boston.split_frame(ratios = [.8])

		# try using the `missing_values_handling` parameter:
		# initialize the estimator then train the model
		boston_glm = H2OGeneralizedLinearEstimator(missing_values_handling = "skip")
		boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the mse for the validation data
		print(boston_glm.mse(valid=True))

		# grid over `missing_values_handling`
		# import Grid Search
		from h2o.grid.grid_search import H2OGridSearch

		# select the values for `missing_values_handling` to grid over
		hyper_params = {'missing_values_handling': ["skip", "mean_imputation"]}

		# this example uses cartesian grid search because the search space is small
		# and we want to see the performance of all models. For a larger search space use
		# random grid search instead: {'strategy': "RandomDiscrete"}
		# initialize the GLM estimator
		boston_glm_2 = H2OGeneralizedLinearEstimator()

		# build grid search with previously made GLM and hyperparameters
		grid = H2OGridSearch(model = boston_glm_2, hyper_params = hyper_params,
		                     search_criteria = {'strategy': "Cartesian"})

		# train using the grid
		grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)


		# sort the grid models by mse
		sorted_grid = grid.get_grid(sort_by='mse', decreasing=False)
		print(sorted_grid)
