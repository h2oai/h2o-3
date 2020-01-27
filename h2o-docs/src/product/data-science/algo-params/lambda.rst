``lambda``
----------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

To get the best possible model, GLM needs to find the optimal values of the regularization parameters :math:`\alpha` and :math:`\lambda`. When performing regularization, penalties are introduced to the model buidling process to avoid overfitting, to reduce variance of the prediction error, and to handle correlated predictors. The two most common penalized models are ridge regression and LASSO (least absolute shrinkage and selection operator). The elastic net combines both penalties. These types of penalties are described in greater detail in the `Regularization <../glm.html#regularization>`__ section in GLM for more information. 

The ``lambda`` parameter controls the amount of regularization applied to the model. A non-negative value represents a shrinkage parameter, which multiplies :math:`P(\alpha, \beta)` in the objective. The larger lambda is, the more the coefficients are shrunk toward zero (and each other). When the value is 0, regularization is disabled, and ordinary generalized liner models are fit. The default value for ``lambda`` is calculated by H2O using a heuristic based on the training data. 

This option also works closely with the `alpha <alpha.html>`__ parameter, which controls the distribution between the :math:`\ell_1` (LASSO) and :math:`\ell_2` (ridge regression) penalties. The following table describes the type of penalized model that results based on the values specifed for the ``lambda`` and ``alpha`` options.

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

- `alpha <alpha.html>`__
- `lambda_min_ratio <lambda_min_ratio.html>`__
- `lambda_search <lambda_search.html>`__
- `nlambdas <nlambdas.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines <-  h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"] <- as.factor(airlines["Year"])
	airlines["Month"] <- as.factor(airlines["Month"])
	airlines["DayOfWeek"] <- as.factor(airlines["DayOfWeek"])
	airlines["Cancelled"] <- as.factor(airlines["Cancelled"])
	airlines['FlightNum'] <- as.factor(airlines['FlightNum'])

	# set the predictor names and the response column name
	predictors <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum")
	response <- "IsDepDelayed"

	# split into train and validation
	airlines.splits <- h2o.splitFrame(data =  airlines, ratios = .8)
	train <- airlines.splits[[1]]
	valid <- airlines.splits[[2]]

	# try using the `lambda` parameter:
	airlines.glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, lambda =.0001)

	# print the AUC for the validation data
	print(h2o.auc(airlines.glm, valid = TRUE))

	# Example of values to grid over for `lambda`
	hyper_params <- list( lambda = c(1, 0.5, 0.1, 0.01, 0.001, 0.0001, 0.00001, 0) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	grid <- h2o.grid(x = predictors, y = response, family = 'binomial', training_frame = train, validation_frame = valid,
	                 algorithm = "glm", grid_id = "air_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"))

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("air_grid", sort_by = "auc", decreasing = TRUE)
	sortedGrid
	

   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"]= airlines["Year"].asfactor()
	airlines["Month"]= airlines["Month"].asfactor()
	airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
	airlines["Cancelled"] = airlines["Cancelled"].asfactor()
	airlines['FlightNum'] = airlines['FlightNum'].asfactor()

	# set the predictor names and the response column name
	predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
	response = "IsDepDelayed"

	# split into train and validation sets
	train, valid= airlines.split_frame(ratios = [.8])

	# try using the `lambda_` parameter:
	# initialize your estimator
	airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', lambda_ = .0001)

	# then train your model
	airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_glm.auc(valid=True))


	# Example of values to grid over for `lambda`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for lambda_ to grid over
	hyper_params = {'lambda': [1, 0.5, 0.1, 0.01, 0.001, 0.0001, 0.00001, 0]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the glm estimator
	airlines_glm_2 = H2OGeneralizedLinearEstimator(family = 'binomial')

	# build grid search with previously made GLM and hyperparameters
	grid = H2OGridSearch(model = airlines_glm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by = 'auc', decreasing = True)
	print(sorted_grid)