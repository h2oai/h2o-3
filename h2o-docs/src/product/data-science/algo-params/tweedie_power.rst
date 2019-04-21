``tweedie_power``
-----------------

- Available in: GBM, Deep Learning, XGBoost
- Hyperparameter: yes

Description
~~~~~~~~~~~

 A Tweedie distribution provides a continuous spectrum from Poisson distribution to the Gamma distribution. When ``distribution=tweedie`` is specified, then you can also specify a ``tweedie_power`` value. Users can tune over this option with values > 1.0 and < 2.0. 

 More information about Tweedie distribution is available `here <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.	

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the insurance dataset:
	# this dataset predicts the number of claims a policy holder will make
	# original dataset can be found at https://cran.r-project.org/web/packages/MASS/MASS.pdf
	insurance <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")

	# set the predictor names and the response column name
	predictors <- colnames(insurance)[1:4]
	response <- 'Claims'

	# convert columns to factors
	insurance['Group'] <- as.factor(insurance['Group'])
	insurance['Age'] <- as.factor(insurance['Age'])

	# split into train and validation sets
	insurance.splits <- h2o.splitFrame(data =  insurance, ratios = .8, seed = 1234)
	train <- insurance.splits[[1]]
	valid <- insurance.splits[[2]]

	# try using the `tweedie_power` parameter:
	# train your model, where you specify the distribution as tweedie
	# and the tweedie_power parameter
	insurance_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                         validation_frame = valid,
	                         distribution = 'tweedie',
	                         tweedie_power = 1.2,
	                         seed = 1234)

	# print the MSE for validation set
	print(h2o.mse(insurance_gbm, valid = TRUE))

	# grid over `tweedie_power` parameter
	# select the values for `tweedie_power` to grid over
	hyper_params <- list( tweedie_power = c(1.2, 1.5, 1.7, 1.8) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously made GBM and hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train,
	                 validation_frame = valid, algorithm = "gbm", 
	                 grid_id = "insurance_grid", 
	                 distribution = "tweedie",
	                 hyper_params = hyper_params,
	                 seed = 1234)

	# Sort the grid models by MSE
	sortedGrid <- h2o.getGrid("insurance_grid", sort_by = "mse", decreasing = FALSE)
	sortedGrid

   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the insurance dataset:
	# this dataset predicts the number of claims a policy holder will make
	# original dataset can be found at https://cran.r-project.org/web/packages/MASS/MASS.pdf
	insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")

	# set the predictor names and the response column name
	predictors = insurance.columns[0:4]
	response = 'Claims'

	# convert columns to factors
	insurance['Group'] = insurance['Group'].asfactor()
	insurance['Age'] = insurance['Age'].asfactor()

	# split into train and validation sets
	train, valid = insurance.split_frame(ratios = [.8], seed = 1234)

	# try using the `tweedie_power` parameter:
	# initialize your estimator
	insurance_gbm = H2OGradientBoostingEstimator(distribution="tweedie", tweedie_power = 1.2, seed =1234)

	# then train your model
	insurance_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the MSE for the validation data
	print(insurance_gbm.mse(valid=True))


	# Example of values to grid over for `tweedie_power`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for tweedie_power to grid over
	hyper_params = {'tweedie_power': [1.2, 1.5, 1.7, 1.8]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	insurance_gbm_2 = H2OGradientBoostingEstimator(distribution = "tweedie", seed = 1234,)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = insurance_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing MSE
	sorted_grid = grid.get_grid(sort_by = 'mse', decreasing = False)
	print(sorted_grid)



