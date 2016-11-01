``histogram_type``
------------------

- Available in: GBM, DRF
- Hyperparameter: yes

Description
~~~~~~~~~~~

By default (AUTO) GBM/DRF bins from min...max in steps of (max-min)/N.  Use this option to specify the type of histogram to use for finding optimal split points. Available types include:

- AUTO
- UniformAdaptive
- Random
- QuantilesGlobal
- RoundRobin

When ``histogram_type="UniformAdaptive"`` is specified, each feature is binned into buckets of equal step size (not population). This is the fastest method, and usually performs well, but can lead to less accurate splits if the distribution is highly skewed.

When ``histogram_type="Random"`` is specified, the algorithm will sample N-1 points from min...max and use the sorted list of those to find the best split.

When ``histogram_type="QuantilesGlobal"`` is specified, the feature distribution is taken into account with a quantile-based binning (where buckets have equal population). This computes ``nbins`` quantiles for each numeric (non-binary) column, then refines/pads each bucket (between two quantiles) uniformly (and randomly for remainders) into a total of ``nbins_top_level`` bins. This set of split points is then used for all levels of the tree: each leaf node histogram gets min/max-range adjusted (based on its population range) and also linearly refined/padded to end up with exactly ``nbins`` (level) bins to pick the best split from. For integer columns where this ends up with more than the unique number of distinct values, the algorithm falls back to the pure-integer buckets.

When ``histogram_type="RoundRobin"`` is specified, the algorithm will cycle through all histogram types (one per tree).


Related Parameters
~~~~~~~~~~~~~~~~~~

- `nbins <nbins.html>`__
- `nbins_top_level <nbins_top_level.html>`__


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

	# try using the `histogram_type` parameter:
	airlines.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, histogram_type = "UniformAdaptive" , 
	                        seed = 1234)

	# print the value used and AUC for the validation data
	print(h2o.auc(airlines.gbm, train = TRUE))


	# Example of values to grid over for `histogram_type`
	hyper_params <- list( histogram_type = c("UniformAdaptive", "Random", "QuantilesGlobal", "RoundRobin") )

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

	# try using the `histogram_type` parameter: 
	# initialize your estimator
	airlines_gbm = H2OGradientBoostingEstimator(histogram_type = "UniformAdaptive", seed =1234) 

	# then train your model
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_gbm.auc(valid=True))


	# Example of values to grid over for `histogram_type`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for histogram_type to grid over
	hyper_params = {'histogram_type': ["UniformAdaptive", "Random", "QuantilesGlobal", "RoundRobin"]}

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
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid, seed = 1234)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by = 'auc', decreasing = True)
	print(sorted_grid)