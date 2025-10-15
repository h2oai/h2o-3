``control_variables``
--------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

When control values are enabled, the model is trained as usual GLM model, but during scoring metrics are calculated also with control values excluded. After training the model score with control values excluded from calculation of predictions. In scoring history there are visible all metrics together - metrics where control values are included are marked as "unrestricted" metrics. 

**Notes**:

- This option is applicable only for regression and binomial distribution.
- This option is not available when cross validation is enabled.
- This option is not available when Lambda search is enabled.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

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
		airlines_splits <- h2o.splitFrame(data =  airlines, ratios = 0.8)
		train <- airlines_splits[[1]]
		valid <- airlines_splits[[2]]

		# try using the `control_variables` parameter:
		airlines_glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
		                        validation_frame = valid,
		                        remove_collinear_columns = TRUE,
		                        control_values = ["Year", "DayOfWeek"])

		# print the AUC for the validation data
		print(h2o.auc(airlines_glm, valid = TRUE))

		# take a look at the coefficients_table
		airlines_glm@model$coefficients_table

   .. code-tab:: python

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

		# try using the `control_values` parameter:
		# initialize your estimator
		airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', 
		                                             remove_collinear_columns = True,
		                                             control_values = ["Year", "DayOfWeek"])

		# then train your model
		airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		print(airlines_glm.auc(valid=True))

		# take a look at the coefficients_table
		coeff_table = airlines_glm._model_json['output']['coefficients_table']

		# convert table to a pandas dataframe
		coeff_table.as_data_frame()
