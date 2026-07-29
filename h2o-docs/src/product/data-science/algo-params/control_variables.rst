``control_variables``
--------------------

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

**Cross-validation support**

When cross-validation is enabled (``nfolds > 0``), two parallel CV metric views are computed:

- **Restricted** (``cross_validation_metrics``, ``cross_validation_metrics_summary``): CV metrics computed with the control variables zeroed out, consistent with the restricted training and validation metrics.
- **Unrestricted** (``cross_validation_metrics_unrestricted_model``, ``cross_validation_metrics_summary_unrestricted_model``): CV metrics computed with the control variables preserved, matching the unrestricted training and validation metrics.

Calling ``make_unrestricted_glm_model()`` on a model trained with CV propagates the unrestricted CV metrics into the derived model's main ``cross_validation_metrics`` slot, so the derived model presents the full with-control-variables view consistently across training, validation, and CV.

**Combination with remove_offset_effects**

If you set up ``control_variables`` together with the ``remove_offset_effects`` feature, model metrics and scoring history (including under cross-validation) are calculated with both features enabled (that is, with both offset and control-variables effects removed during scoring). Four CV metric views are available in this case: the default restricted view (both effects removed, in ``cross_validation_metrics``), the control-variables-only-restricted view (``cross_validation_metrics_restricted_model_contr_vals``, offset kept), the offset-only-restricted view (``cross_validation_metrics_restricted_model_ro``, control variables kept), and the fully-unrestricted view (``cross_validation_metrics_unrestricted_model``, neither effect removed).
To get a model with only one set of effects excluded, use ``glm.make_derived_glm_model()`` / ``h2o.make_derived_glm_model()`` with exactly one of its two flags set to ``True``:

- ``remove_control_variables_effects=True``: excludes the control-variables effects from scoring and metrics; the offset effects stay included.
- ``remove_offset_effects=True``: excludes the offset effects from scoring and metrics; the control-variables effects stay included.

The two flags cannot both be ``True`` in the same call.
If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation.

**Notes**:

- This option is experimental.
- This option is not supported for multinomial, ordinal, or custom distributions.
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
