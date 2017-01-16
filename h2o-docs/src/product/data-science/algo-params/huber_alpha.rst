``huber_alpha``
---------------

- Available in: GBM, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

The Huber loss function is a combination of the squared-error loss function and absolute-error loss function. It applies the squared-error loss for small deviations from the actual response value and the absolute-error loss for large deviations from the actual respone value. To activate this parameter you must set ``distribution=huber`` and specify the ``huber_alpha`` parameter, which dictates the
threshold between quadratic and linear loss (i.e. the top percentile of error that should be considered as outliers). This value must be between 0 and 1 and defaults to 0.9. 

More information about the Huber loss function is available `here <https://en.wikipedia.org/wiki/Huber_loss>`__.

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

	# try using the `huber_alpha` parameter:
	# train your model, where you specify the distribution as huber
	# and the huber_alpha parameter
	insurance_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      distribution = 'huber',
	                      huber_alpha = .9,
	                      seed = 1234)

	# print the MSE for validation set
	print(h2o.mse(insurance_gbm, valid = TRUE))

	# grid over `huber_alpha` parameter
	# select the values for `huber_alpha` to grid over
	hyper_params <- list( huber_alpha = c(.2, .5, .8) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously made GBM and hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train,
	                 validation_frame = valid, algorithm = "gbm", 
	                 grid_id = "insurance_grid", 
	                 distribution = "huber",
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

	# try using the `huber_alpha` parameter:
	# initialize your estimator where you specify the distribution as huber
	# and the huber_alpha parameter
	insurance_gbm = H2OGradientBoostingEstimator(distribution="huber", huber_alpha = 0.9, seed =1234)

	# then train your model
	insurance_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the MSE for the validation data
	print(insurance_gbm.mse(valid = True))


	# Example of values to grid over for `huber_alpha`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `huber_alpha` to grid over
	hyper_params = {'huber_alpha': [.2, .5, .8]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	insurance_gbm_2 = H2OGradientBoostingEstimator(distribution="huber", seed = 1234)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = insurance_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing MSE
	sorted_grid = grid.get_grid(sort_by = 'mse', decreasing = False)
	print(sorted_grid)