``sample_rate``
---------------

- Available in: GBM, DRF, XGBoost, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option is used to specify the row (x-axis) sampling rate (without replacement). The range is 0.0 to 1.0. In GBM and XGBoost, this value defaults to 1; in DRF, this value defaults to 0.6320000291. Row and column sampling (``sample_rate`` and ``col_sample_rate``) can improve generalization and lead to lower validation and test set errors. Good general values for large datasets are around 0.7 to 0.8 (sampling 70-80 percent of the data) for both parameters, as higher values generally improve training accuracy.

For highly imbalanced classification datasets, stratified row sampling based on response class membership can help improve predictive accuracy. This is configured with ``sample_rate_per_class`` (array of ratios, one per response class in lexicographic order).

**Note:** If ``sample_rate_per_class`` is specified, then ``sample_rate`` will be ignored.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `col_sample_rate <col_sample_rate.html>`__
- `sample_rate_per_class <sample_rate_per_class.html>`__


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

	# try using the `sample_rate` parameter:
	airlines.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, sample_rate =.7 , 
	                        seed = 1234)

	# print the AUC for the validation data
	print(h2o.auc(airlines.gbm, valid = TRUE))


	# Example of values to grid over for `sample_rate`
	hyper_params <- list( sample_rate = c(.7, .8, 1) )

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

	# try using the `sample_rate` parameter: 
	# initialize your estimator
	airlines_gbm = H2OGradientBoostingEstimator(sample_rate = .7, seed =1234) 

	# then train your model
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_gbm.auc(valid=True))


	# Example of values to grid over for `sample_rate`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for sample_rate to grid over
	hyper_params = {'sample_rate': [.7, .8, 1]}

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