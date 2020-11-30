.. _stopping_tolerance:

``stopping_tolerance``
----------------------

- Available in: GBM, DRF, Deep Learning, GLM, GAM, AutoML, XGBoost, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the tolerance value by which a model must improve before training ceases. For example, given the following options:

-  ``stopping_rounds=3``
-  ``stopping_metric=misclassification``
-  ``stopping_tolerance=1e-3``

then the moving average for last 4 stopping rounds is calculated (the first moving average is reference value for other 3 moving averages to compare). 

The model will stop if the **ratio** between the best moving average and reference moving average is more or equal **1-1e-3** (the misclassification is the less the better metric, for the more the better metrics the ratio have to be less or equal **1+1e-3** to stop).

These stopping options are used to increase performance by restricting the number of models that get built. 

**Notes**: 

- ``stopping_rounds`` must be enabled for ``stopping_metric`` or ``stopping_tolerance`` to work.
- For all supported algorithms except AutoML and Isolation Forest, this value defaults to 0.001. In AutoML, this value defaults to 0.001 if the  dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate. In Isolation Forest, this value defaults to 0.01.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `stopping_metric <stopping_metric.html>`__
- `stopping_rounds <stopping_rounds.html>`__


Example
~~~~~~~

.. tabs::
   .. code-tab:: r R
   
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
		airlines_splits <- h2o.splitFrame(data =  airlines, ratios = 0.8, seed = 1234)
		train <- airlines_splits[[1]]
		valid <- airlines_splits[[2]]

		# try using the `stopping_tolerance` metric:
		# train your model, where you specify the stopping_metric, stopping_rounds, 
		# and stopping_tolerance
		airlines_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
		                        stopping_metric = "AUC", stopping_rounds = 3,
		                        stopping_tolerance = 1e-2, seed = 1234)

		# print the auc for the validation data
		print(h2o.auc(airlines_gbm, valid = TRUE))


   .. code-tab:: python

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

		# try using the `stopping_tolerance` metric:
		# train your model, where you specify the stopping_metric, stopping_rounds, 
		# and stopping_tolerance
		# initialize the estimator then train the model
		airlines_gbm = H2OGradientBoostingEstimator(stopping_metric = "auc", stopping_rounds = 3,
		                                            stopping_tolerance = 1e-2,
		                                            seed =1234)
		airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		airlines_gbm.auc(valid=True)

