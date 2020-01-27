``col_sample_rate_change_per_level``
------------------------------------

- Available in: GBM, DRF, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the relative change of the column sampling rate for every level (without replacement). For example, if you want to specify how the sampling rate per split should change as a function of the tree depth, you might consider the following:

- level 0: ``col_sample_rate``
- level 1: ``col_sample_rate`` * factor
- level 2: ``col_sample_rate`` * factor^2
- level 3: ``col_sample_rate`` * factor^3

where factor is the ``col_sample_rate_change_per_level``

As indicated above, this option is multiplicative with ``col_sample_rate``. The effective sampling rate at a given level is:

::

	col_sample_rate_per_tree * col_sample_rate * col_sample_rate_change_per_level^depth

This option defaults to 1.0 and must be > 0.0 and <= 2.0.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `col_sample_rate <col_sample_rate.html>`__
- `col_sample_rate_per_tree <col_sample_rate_per_tree.html>`__


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
	airlines.splits <- h2o.splitFrame(data =  airlines, ratios = .8, seed = 1234)
	train <- airlines.splits[[1]]
	valid <- airlines.splits[[2]]

	# try using the `col_sample_rate_change_per_level` parameter:
	airlines.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, col_sample_rate_change_per_level = .9 , 
	                        seed = 1234)

	# print the AUC for the validation data
	print(h2o.auc(airlines.gbm, valid = TRUE))


	# Example of values to grid over for `col_sample_rate_change_per_level`
	hyper_params <- list( col_sample_rate_change_per_level = c(.3, .7, .8, 2) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	# this GBM uses early stopping once the validation AUC doesn't improve by at least 0.01% for
	# 5 consecutive scoring events
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "air_grid", hyper_params = hyper_params,
	                 stopping_rounds = 5, stopping_tolerance = 1e-4, stopping_metric = "AUC",
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("air_grid", sort_by = "auc", decreasing = TRUE)
	sortedGrid


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
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
	train, valid= airlines.split_frame(ratios = [.8], seed = 1234)

	# try using the `col_sample_rate_change_per_level` parameter: 
	# initialize your estimator
	airlines_gbm = H2OGradientBoostingEstimator(col_sample_rate_change_per_level = .9, seed =1234) 

	# then train your model
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_gbm.auc(valid=True))


	# Example of values to grid over for `col_sample_rate_change_per_level`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `col_sample_rate_change_per_level` to grid over
	hyper_params = {'col_sample_rate_change_per_level': [.3, .7, .8, 2]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	# use early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	airlines_gbm_2 = H2OGradientBoostingEstimator(seed = 1234,
	                                              stopping_rounds = 5,
	                                              stopping_metric = "AUC", stopping_tolerance = 1e-4)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = airlines_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by = 'auc', decreasing = True)
	print(sorted_grid)

