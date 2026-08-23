``remove_offset_effects``
--------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

This feature allows you to remove offset effects during scoring and metric calculation.

Model metrics and scoring history are calculated for both the restricted model (with offset effects removed) and the unrestricted model (with offset effects included).

To get the unrestricted model with its own metrics, use ``glm.make_unrestricted_glm_model()`` / ``h2o.make_unrestricted_glm_model(glm)``.

Zeroing the offset's contribution does not re-centre the intercept, so (as with ``control_variables``) the restricted view's ``residual_deviance``, ``logloss``, ``MSE``/``RMSE``, ``R²`` and ``AIC`` are not directly comparable to a normally-fitted model -- only rank metrics (AUC, Gini, PR-AUC) stay meaningful. ``cross_validation_metrics`` (what grid search / AutoML sort on) holds the restricted view; early stopping uses the unrestricted view instead. See `control_variables <control_variables.html>`__ for the full explanation, which applies identically here.

**Cross-validation support**

When cross-validation is enabled (``nfolds > 0`` or a ``fold_column``), two parallel CV metric views are computed:

- **Restricted** (``cross_validation_metrics``, ``cross_validation_metrics_summary``): CV metrics computed with the offset zeroed out, consistent with the restricted training and validation metrics.
- **Unrestricted** (``cross_validation_metrics_unrestricted_model``, ``cross_validation_metrics_summary_unrestricted_model``): CV metrics computed with the offset preserved, matching the unrestricted training and validation metrics.

Calling ``make_unrestricted_glm_model()`` on a model trained with CV propagates the unrestricted CV metrics into the derived model's main ``cross_validation_metrics`` slot, so the derived model presents the full with-offset view consistently across training, validation, and CV.


**Combination with control_variables**

If you set up ``remove_offset_effects`` together with ``control_variables``, model metrics and scoring history are calculated with both features enabled (that is, with both offset and control-variables effects removed during scoring).
To get a model with only one set of effects excluded, use ``glm.make_derived_glm_model()`` / ``h2o.make_derived_glm_model()`` with exactly one of its two flags set to ``True``:

- ``remove_control_variables_effects=True``: excludes the control-variables effects from scoring and metrics; the offset effects stay included.
- ``remove_offset_effects=True``: excludes the offset effects from scoring and metrics; the control-variables effects stay included.

The two flags cannot both be ``True`` in the same call.
If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation.
When cross-validation is enabled and both features are set together, four CV metric views are available: the default restricted view (both effects removed, in ``cross_validation_metrics``), the control-variables-only-restricted view (``cross_validation_metrics_restricted_model_contr_vals``, offset kept), the offset-only-restricted view (``cross_validation_metrics_restricted_model_ro``, control variables kept), and the fully-unrestricted view (``cross_validation_metrics_unrestricted_model``, neither effect removed). Each has a matching ``..._summary`` table; see `control_variables <control_variables.html>`__ for the full view/slot table.

**Notes**:

- This option is experimental.
- This option is not supported for multinomial, ordinal, or custom distributions.
- This option is not available when Lambda search is enabled.
- This option is not available when interactions are enabled.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `control_variables <control_variables.html>`__

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
		predictors <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "FlightNum")
		response <- "IsDepDelayed"

		# split into train and validation
		airlines_splits <- h2o.splitFrame(data =  airlines, ratios = 0.8)
		train <- airlines_splits[[1]]
		valid <- airlines_splits[[2]]

		# try using the `remove_offset_effects` parameter:
		airlines_glm <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
                        validation_frame = valid,
                        remove_collinear_columns = TRUE,
                        score_each_iteration = TRUE,
                        generate_scoring_history = TRUE,
                        offset_column = "Distance",
                        remove_offset_effects = TRUE)

		# print the AUC for the validation data
		print(h2o.auc(airlines_glm, valid = TRUE))

		# take a look at the learning curve
		h2o.learning_curve_plot(airlines_glm)

		# get the unrestricted GLM model
		unrestricted_airlines_glm <- h2o.make_unrestricted_glm_model(airlines_glm)

		# remove_offset_effects also works with cross-validation:
		airlines_glm_cv <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
                           offset_column = "Distance",
                           remove_offset_effects = TRUE,
                           nfolds = 5)

		# restricted CV deviance (offset removed during CV scoring)
		print(h2o.residual_deviance(airlines_glm_cv, xval = TRUE))

		# unrestricted CV deviance (offset preserved during CV scoring)
		print(airlines_glm_cv@model$cross_validation_metrics_unrestricted_model$residual_deviance)

		# derived model presents the full with-offset CV view consistently
		unrestricted_cv_glm <- h2o.make_unrestricted_glm_model(airlines_glm_cv)
		print(h2o.residual_deviance(unrestricted_cv_glm, xval = TRUE))


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
		predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "FlightNum"]
		response = "IsDepDelayed"

		# split into train and validation sets
		train, valid= airlines.split_frame(ratios = [.8])

		# try using the `remove_offset_effects` parameter:
		# initialize your estimator
		airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial',
		                                             remove_collinear_columns = True,
		                                             score_each_iteration = True,
		                                             generate_scoring_history = True,
		                                             offset_column = "Distance",
		                                             remove_offset_effects = True)

		# then train your model
		airlines_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		print(airlines_glm.auc(valid=True))

		# take a look at the learning curve
		airlines_glm.learning_curve_plot()

		# get the unrestricted GLM model
		unrestricted_airlines_glm = airlines_glm.make_unrestricted_glm_model()

		# remove_offset_effects also works with cross-validation:
		airlines_glm_cv = H2OGeneralizedLinearEstimator(family = 'binomial',
		                                                offset_column = "Distance",
		                                                remove_offset_effects = True,
		                                                nfolds = 5)
		airlines_glm_cv.train(x = predictors, y = response, training_frame = train)

		# restricted CV deviance (offset removed during CV scoring)
		print(airlines_glm_cv.model_performance(xval=True).residual_deviance())

		# unrestricted CV deviance (offset preserved during CV scoring)
		print(airlines_glm_cv.cross_validation_metrics_unrestricted_model["residual_deviance"])

		# derived model presents the full with-offset CV view consistently
		unrestricted_cv_glm = airlines_glm_cv.make_unrestricted_glm_model()
		print(unrestricted_cv_glm.model_performance(xval=True).residual_deviance())
