``non_negative``
----------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

At times when working with real-world data, regression models can yield counterintuitive results, such as when an increase in one variable causes an increase in a response even though they are negatively correlated. To adjust this, you can specify ``non_negative=TRUE``, which instructs GLM to force coefficients (non-intercept) to have non-negative values. When enabled, GLM will return only positive coefficients. 

To enforce the algorithm to only use positive coefficients, you are (in a sense) indicating that you know that the features are all correlated with positive outcomes. As such, this option is generally only useful if you know the predictive features are positively correlated with the outcome. In superlearning, this does hold true. But you should use caution when enabling this command, keeping in mind that your best chance for catching overfitting with some negative coefficients performing worse is when your model has unseen data that looks a little different. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

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

	# try using the `non_negative` parameter:
	airlines_glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, 
	                        non_negative = TRUE)

	# print the AUC for the validation data
	print(h2o.auc(airlines_glm, valid = TRUE))

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

	# try using the `non_negative` parameter:
	# set to 'True', so only positive coefficients are returned
	# initialize your estimator
	airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', non_negative = True)

	# then train your model
	airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_glm.auc(valid=True))
