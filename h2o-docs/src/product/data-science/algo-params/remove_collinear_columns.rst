``remove_collinear_columns``
----------------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Collinear columns can cause problems during model fitting. The preferred way to deal with collinearity (and the default H2O behavior) is to add regularization. (See the `Regularization <../glm.html#regularization>`__ topic for more information.) However, if you want a non-regularized solution, you can choose to automatically remove collinear columns by enabling the ``remove_collinear_columns`` option. 

This option can only be used when ``solver=IRLSM`` and with no regularization (``lambda=0``). If enabled, H2O will automatically remove columns when it detects collinearlity. The columns that are removed depend on the order of the columns in the vector of coefficients (intercepts first, then categorical variables ordered by cardinality from largest to smallest, and then numbers).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `solver <solver.html>`__

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

	# try using the `remove_collinear_columns` parameter:
	# must be used with lambda = 0
	airlines.glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, remove_collinear_columns = TRUE, lambda = 0)

	# print the auc for the validation data
	print(h2o.auc(airlines.glm, valid = TRUE))

   
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

	# try using the `remove_collinear_columns` parameter:
	# must be used with lambda_ = 0
	# initialize your estimator
	airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', lambda_ = 0, 
	                                             remove_collinear_columns = True)

	# then train your model
	airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_glm.auc(valid=True))
