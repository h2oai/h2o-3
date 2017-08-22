``stopping_metric``
-------------------

- Available in: GBM, DRF, Deep Learning, AutoML, XGBoost
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the metric to consider when early stopping is specified (i.e., when ``stopping_rounds`` > 0). For example, given the following options:

- ``stopping_rounds=3``
- ``stopping_metric=misclassification``
- ``stopping_tolerance=1e-3``

then the model will stop training after reaching three scoring events in a row in which a model's missclassication value does not improve by **1e-3**. These stopping options are used to increase performance by restricting the number of models that get built.

Available options for ``stopping_metric`` include the following:

- ``auto``: This defaults to ``logloss`` for classification, ``deviance`` for regression
- ``deviance``
- ``logloss``
- ``mse``
- ``rmse``
- ``mae``
- ``rmsle``
- ``auc``
- ``lift_top_group``
- ``misclassification``
- ``mean_per_class_error``

**Note**: ``stopping_rounds`` must be enabled for ``stopping_metric`` or ``stopping_tolerance`` to work.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `stopping_rounds <stopping_rounds.html>`__
- `stopping_tolerance <stopping_tolerance.html>`__


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

	# try using the `stopping_metric` parameter: 
	# since this is a classification problem we will look at the AUC
	# you could also choose logloss, or misclassification, among other options

	# train your model, where you specify the stopping_metric, stopping_rounds, 
	# and stopping_tolerance
	airlines.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                        stopping_metric = "AUC", stopping_rounds = 3,
	                        stopping_tolerance = 1e-2, seed = 1234)

	# print the auc for the validation data
	print(h2o.auc(airlines.gbm, valid = TRUE))


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

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

	# try using the `stopping_metric` parameter: 
	# since this is a classification problem we will look at the AUC
	# you could also choose logloss, or misclassification, among other options
	# train your model, where you specify the stopping_metric, stopping_rounds, 
	# and stopping_tolerance
	# initialize the estimator then train the model
	airlines_gbm = H2OGradientBoostingEstimator(stopping_metric = "auc", stopping_rounds = 3,
	                                            stopping_tolerance = 1e-2,
	                                            seed =1234)
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	airlines_gbm.auc(valid=True)

