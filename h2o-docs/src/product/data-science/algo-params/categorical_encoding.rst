``categorical_encoding``
------------------------

- Available in: GBM, DRF, Deep Learning, K-Means
- Hyperparameter: 

Description
~~~~~~~~~~~

This option specifies the encoding scheme to use for handling categorical features. Available schemes include the following:

**GBM/DRF**

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default)
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``one_hot_explicit`` or ``OneHotExplicit`` : N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only

**Deep Learning/K-Means**

  - ``auto`` or ``AUTO``:  Allow the algorithm to decide
  - ``one_hot_internal`` or ``OneHotInternal``: On the fly N+1 new cols for categorical features with N levels (default)
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only

Related Parameters
~~~~~~~~~~~~~~~~~~

- none


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

	# try using the `categorical_encoding` parameter:
	encoding = "OneHotExplicit"

	# train your model
	airlines_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                        categorical_encoding = encoding, seed = 1234)

	# print the auc for the validation set
	print(h2o.auc(airlines_gbm, valid=TRUE))

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

	# try using the `categorical_encoding` parameter:
	encoding = "one_hot_explicit"

	# initialize the estimator 
	airlines_gbm = H2OGradientBoostingEstimator(categorical_encoding = encoding, seed =1234)

	# then train the model
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation set
	airlines_gbm.auc(valid=True)