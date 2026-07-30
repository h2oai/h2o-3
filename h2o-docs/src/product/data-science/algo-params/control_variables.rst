``control_variables``
---------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Control variables are special predictors that are included during model training but automatically excluded during inference/scoring. This feature allows you to account for certain factors during training without having them affect predictions.

Common use cases include:

- Accounting for batch effects or experimental conditions
- Controlling for confounding variables
- Incorporating fixed effects that won't be available at prediction time

When control variables are specified, GLM will exclude them during scoring. Model metrics and scoring history are calculated for both the restricted model (with control variables excluded) and the unrestricted model (with control variables included).

To get the unrestricted model with its own metrics use ``glm.make_unrestricted_glm_model()`` / ``h2o.make_unrestricted_glm_model(glm)``.

The control variables' coefficients are set to zero in the variable importance table. Use the unrestricted model to get the variable importance table with all variables included. 

If you set up the ``control_variables`` together with the ``remove_offset_effects`` feature, model metrics and scoring history are calculated with both features enabled (that is, with both offset and control-variables effects removed during scoring).
To get a model with only one set of effects excluded, use ``glm.make_derived_glm_model()`` / ``h2o.make_derived_glm_model()`` with exactly one of its two flags set to ``True``. The source model only has to have been trained with the matching feature -- you do not need both:

- ``remove_control_variables_effects=True``: excludes the control-variables effects from scoring and metrics; the offset effects, if the source model has any, stay included. Requires the source model to have been trained with ``control_variables``.
- ``remove_offset_effects=True``: excludes the offset effects from scoring and metrics; the control-variables effects, if the source model has any, stay included. Requires the source model to have been trained with ``remove_offset_effects=True``.

The two flags cannot both be ``True`` in the same call. Calling the same derivation twice with the same ``dest`` returns the model created the first time; if a different model already occupies that key, the call is rejected.
If the source model was trained with only one of the two features, the corresponding derived model is equivalent to the source model in every reported number.
If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation.

**Notes**:

- This option is experimental.
- This option is not supported for multinomial, ordinal, or custom distributions.
- This option is not available when cross validation is enabled.
- This option is not available when Lambda search is enabled.
- This option is not available when interactions are enabled.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `remove_offset_effects <remove_offset_effects.html>`__

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
                        score_each_iteration = TRUE,
                        generate_scoring_history = TRUE,
                        control_variables = c("Year", "DayOfWeek"))

		# print the AUC for the validation data
		print(h2o.auc(airlines_glm, valid = TRUE))

		# take a look at the coefficients_table
		airlines_glm@model$coefficients_table

		# take a look at the learning curve
		h2o.learning_curve_plot(airlines_glm)

		# get the unrestricted GLM model
		unrestricted_airlines_glm <- h2o.make_unrestricted_glm_model(airlines_glm)

		# get variable importance
		varimp <- h2o.varimp(airlines_glm)
		varimp_unrestricted <- h2o.varimp(unrestricted_airlines_glm)


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

		# try using the `control_variables` parameter:
		# initialize your estimator
		airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial', 
		                                             remove_collinear_columns = True,
													 score_each_iteration = True,
													 generate_scoring_history = True,
		                                             control_variables = ["Year", "DayOfWeek"])

		# then train your model
		airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		print(airlines_glm.auc(valid=True))

		# take a look at the coefficients
		print(airlines_glm.coef())

		# take a look at the learning curve
		airlines_glm.learning_curve_plot()

		# get the unrestricted GLM model
		unrestricted_airlines_glm = airlines_glm.make_unrestricted_glm_model()

		# get variable importance tables
		varimp = airlines_glm.varimp()
		varimp_unrestricted = unrestricted_airlines_glm.varimp()
