``tweedie_variance_power``
--------------------------

- Available in: GLM
- Hyperparameter: yes 

Description
~~~~~~~~~~~

When ``family=tweedie``, this option can be used to specify the power for the tweedie variance. This option defaults to 0. 

Tweedie distributions are a family of distributions that include gamma, normal, Poisson and their combinations. This distribution is especially useful for modeling positive continuous variables with exact zeros. The variance of the Tweedie distribution is proportional to the :math:`p`-th power of the mean :math:`var(y_i) = \phi\mu{^p_i}`. 

The Tweedie distribution is parametrized by variance power :math:`p`. It is defined for all :math:`p` values except in the (0,1) interval and has the following distributions as special cases:

- :math:`p = 0`: Normal
- :math:`p = 1`: Poisson
- :math:`p \in (1,2)`: Compound Poisson, non-negative with mass at zero
- :math:`p = 2`: Gamma
- :math:`p = 3`: Gaussian
- :math:`p > 2`: Stable, with support on the positive reals

The following table shows the acceptable relationships between family functions, tweedie variance powers, and tweedie link powers.

+------------------+------------------------+--------------------+
| Family Function  | Tweedie Variance Power | Tweedie Link Power |
+==================+========================+====================+
| Poisson          | 1                      | 0, 1-vpow, 1       |
+------------------+------------------------+--------------------+
| Gamma            | 2                      | 0, 1-vpow, 2       |
+------------------+------------------------+--------------------+
| Gaussian         | 3                      | 1, 1-vpow          |
+------------------+------------------------+--------------------+



Related Parameters
~~~~~~~~~~~~~~~~~~

- `family <family.html>`__
- `link <link.html>`__
- `tweedie_link_power <tweedie_link_power.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the auto dataset:
	# this dataset looks at features of motor insurance policies and predicts the aggregate claim loss
	# the original dataset can be found at https://cran.r-project.org/web/packages/HDtweedie/HDtweedie.pdf
	auto <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")

	# set the predictor names and the response column name
	predictors <- colnames(auto)[-1]
	# The  response is aggregate claim loss (in $1000s)
	response <- "y"

	# split into train and validation sets
	auto.splits <- h2o.splitFrame(data =  auto, ratios = .8)
	train <- auto.splits[[1]]
	valid <- auto.splits[[2]]

	# try using the `tweedie_variance_power` parameter:
	# train your model, where you specify tweedie_variance_power
	auto_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      family = 'tweedie',
	                      tweedie_variance_power = 1)

	# print the mse for validation set
	print(h2o.mse(auto_glm, valid=TRUE))

	# grid over `tweedie_variance_power`
	# select the values for `tweedie_variance_power` to grid over
	hyper_params <- list( tweedie_variance_power = c(0, 1, 1.1, 1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,
	                                          2.1, 2.2,2.3,2.4,2.5,2.6,2.7,2.8,2.9,3, 5, 7) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously selected hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 family = 'tweedie', algorithm = "glm", grid_id = "auto_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"))

	# Sort the grid models by mse
	sortedGrid <- h2o.getGrid("auto_grid", sort_by = "mse", decreasing = FALSE)
	sortedGrid

	# print the mse for the validation data
	print(h2o.mse(auto_glm, valid = TRUE))

   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the auto dataset:
	# this dataset looks at features of motor insurance policies and predicts the aggregate claim loss
	# the original dataset can be found at https://cran.r-project.org/web/packages/HDtweedie/HDtweedie.pdf
	auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")

	# set the predictor names and the response column name
	predictors = auto.names
	predictors.remove('y')
	# The  response is aggregate claim loss (in $1000s)
	response = "y"

	# split into train and validation sets
	train, valid = auto.split_frame(ratios = [.8])

	# try using the `tweedie_variance_power` parameter:
	# initialize the estimator then train the model
	auto_glm = H2OGeneralizedLinearEstimator(family = 'tweedie', tweedie_variance_power = 1)
	auto_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(auto_glm.mse(valid=True))

	# grid over `tweedie_variance_power`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `tweedie_variance_power` to grid over
	hyper_params = {'tweedie_variance_power': [0, 1, 1.1, 1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,
	                                          2.1, 2.2,2.3,2.4,2.5,2.6,2.7,2.8,2.9,3, 5, 7]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GLM estimator
	auto_glm_2 = H2OGeneralizedLinearEstimator(family = 'tweedie')

	# build grid search with previously made GLM and hyperparameters
	grid = H2OGridSearch(model = auto_glm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by mse
	sorted_grid = grid.get_grid(sort_by='mse', decreasing=False)
	print(sorted_grid)