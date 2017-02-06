``compute_p_values``
--------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Z-score, standard error, and `p-values <https://en.wikipedia.org/wiki/P-value>`__ are classical statistical measures of model quality. p-values are essentially hypothesis tests on the values of each coefficient. A high p-value means that a coefficient is unreliable (insignificant), while a low p-value suggests that the coefficient is statistically significant. You can request GLM to compute the p-values by enabling the ``compute_p_values`` option. 

**Notes**:

- This option is only applicable when regularization is disabled (``lambda=0``) and when ``solver=IRLSM``. 
- If collinear columns exist in the data, then you must also specify ``remove_collinear_columns=TRUE``. Otherwise, H2O will return an error. 
- This option cannot be used with ``family=multinomial``
- GLM auto-standardizes the data by default. This changes the p-value of the constant term (intercept).

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

	# try using the `compute_p_values` parameter:
	airlines_glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, 
	                        lambda = 0,
	                        remove_collinear_columns = TRUE,
	                        compute_p_values = TRUE)

	# print the AUC for the validation data
	print(h2o.auc(airlines_glm, valid = TRUE))

	# take a look at the coefficients_table to see the p_values
	airlines_glm@model$coefficients_table

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

	# try using the `compute_p_values` parameter:
	# initialize your estimator
	airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', lambda_ = 0, 
	                                             remove_collinear_columns = True,
	                                             compute_p_values = True)

	# then train your model
	airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(airlines_glm.auc(valid=True))

	# take a look at the coefficients_table to see the p_values
	coeff_table = airlines_glm._model_json['output']['coefficients_table']

	# convert table to a pandas dataframe
	coeff_table.as_data_frame()