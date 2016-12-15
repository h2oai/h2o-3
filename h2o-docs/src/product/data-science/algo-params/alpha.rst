``alpha``
---------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

To get the best possible model, GLM needs to find the optimal values of the regularization parameters :math:`\alpha` and :math:`\lambda`. When performing regularization, penalties are introduced to the model buidling process to avoid overfitting, to reduce variance of the prediction error, and to handle correlated predictors. The two most common penalized models are ridge regression and LASSO (least absolute shrinkage and selection operator). The elastic net combines both penalties. These types of penalties are described in greater detail in the `Regularization <../glm.html#regularization>`__ section in GLM for more information. 

The ``alpha`` parameter controls the distribution between the :math:`\ell_1` (LASSO) and :math:`\ell_2` (ridge regression) penalties. The penalty is defined as 

 :math:`P(\alpha,\beta) = (1 - \alpha) /2 ||\beta||{^2_2} + \alpha||\beta||_1 = \sum_j[(1 - \alpha) /2\beta{^2_j} + \alpha|\beta_j|]`

Given the above, a value of 1.0 represents LASSO, and a value of 0.0 produces ridge regression. This value defaults to 0 if ``solver=L_BFGS``; otherwise, this value defaults to 0.5.

This option also works closely with the `lambda <lambda.html>`__ parameter, which controls the amount of regularization applied. The following table describes the type of penalized model that results based on the values specifed for the ``lambda`` and ``alpha`` options.

+------------------+-----------------------+------------------------------------------+
| ``lambda`` value | ``alpha`` value       | Result                                   |
+==================+=======================+==========================================+
| ``lambda`` == 0  | ``alpha`` = any value | No regularization. ``alpha`` is ignored. |
+------------------+-----------------------+------------------------------------------+
| ``lambda`` > 0   | ``alpha`` == 0        | Ridge Regression                         |
+------------------+-----------------------+------------------------------------------+
| ``lambda`` > 0   | ``alpha`` == 1        | LASSO                                    |
+------------------+-----------------------+------------------------------------------+
| ``lambda`` > 0   | 0 < ``alpha`` < 1     | Elastic Net Penalty                      |
+------------------+-----------------------+------------------------------------------+

Related Parameters
~~~~~~~~~~~~~~~~~~

- `lambda <lambda.html>`__
- `solver <solver.html>`__

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

	# try using the `alpha` parameter:
	# train your model, where you specify alpha
	boston_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                      validation_frame = valid,
	                      alpha = .25)

	# print the mse for the validation data
	print(h2o.mse(boston_glm, valid=TRUE))

	# grid over `alpha`
	# select the values for `alpha` to grid over
	hyper_params <- list( alpha = c(0, .25, .5, .75, .1) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}

	# build grid search with previously selected hyperparameters
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "glm", grid_id = "boston_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"))

	# Sort the grid models by mse
	sortedGrid <- h2o.getGrid("boston_grid", sort_by = "mse", decreasing = FALSE)
	sortedGrid


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


	# try using the `alpha` parameter:
	# initialize the estimator then train the model
	boston_glm = H2OGeneralizedLinearEstimator(alpha = .25)
	boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(boston_glm.mse(valid=True))

	# grid over `alpha`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `alpha` to grid over
	hyper_params = {'alpha': [0, .25, .5, .75, .1]}

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
