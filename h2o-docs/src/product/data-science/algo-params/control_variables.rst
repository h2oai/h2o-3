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

When control variables are specified, GLM excludes their contribution from **predictions**, not just from reported metrics: their coefficients are zeroed before scoring, so any downstream consumer of ``predict()`` (not only ``model_performance()``) sees the restricted view. Model metrics and scoring history are calculated for both the restricted model (with control variables excluded) and the unrestricted model (with control variables included).

To get the unrestricted model with its own metrics use ``glm.make_unrestricted_glm_model()`` / ``h2o.make_unrestricted_glm_model(glm)``.

The control variables' coefficients are set to zero in the variable importance table. Use the unrestricted model to get the variable importance table with all variables included. Two related points worth knowing about the restricted view: zeroing a control variable's coefficient does **not** re-centre the intercept, so restricted predictions are shifted relative to a model that never saw the control variable at all; and ``coefficients_table``/``coef()`` on the restricted (main) model still show the *fitted* (non-zero) control-variable coefficients -- only scoring and the variable importance table zero them out.

Because zeroing a coefficient without refitting the intercept is a deliberate miscalibration (for a canonical-link GLM, dropping a coefficient without adjusting ``b0`` breaks ``mean(pred) == mean(y)``), not every restricted metric means what it normally would:

- Rank metrics (AUC, Gini, PR-AUC) stay meaningful -- they're invariant to a monotone level shift.
- ``residual_deviance``, ``logloss``, ``MSE``/``RMSE``, ``R²`` and ``AIC`` are **not** directly comparable to a normally-fitted model of the same shape; they're inflated by an amount that depends on the reference level chosen, not on predictive quality.
- ``null_deviance`` is still computed from the unrestricted response mean, so ``residual_deviance > null_deviance`` (and hence a negative explained deviance) is possible for the restricted view.
- Despite this, ``cross_validation_metrics`` -- the slot grid search and AutoML leaderboards sort on -- holds the restricted view, while early stopping (when ``control_variables`` and/or ``remove_offset_effects`` is set) uses the unrestricted view instead. Keep that difference in mind if you're comparing model selection behavior with and without this feature.

**Cross-validation support**

When cross-validation is enabled (``nfolds > 0`` or a ``fold_column``) and ``remove_offset_effects`` is not also set, two parallel CV metric views are computed:

- **Restricted** (``cross_validation_metrics``, ``cross_validation_metrics_summary``): CV metrics computed with the control variables zeroed out, consistent with the restricted training and validation metrics.
- **Unrestricted** (``cross_validation_metrics_unrestricted_model``, ``cross_validation_metrics_summary_unrestricted_model``): CV metrics computed with the control variables preserved, matching the unrestricted training and validation metrics.

Calling ``make_unrestricted_glm_model()`` on a model trained with CV propagates the unrestricted CV metrics into the derived model's main ``cross_validation_metrics`` slot, so the derived model presents the full with-control-variables view consistently across training, validation, and CV.

``control_variables`` runs a second full per-fold scoring pass (plus a holdout-predictions merge) even on its own, and for binomial models the per-fold prediction frames are materialized regardless of ``keep_cross_validation_predictions`` -- the "four times higher" cost note below is specifically about combining it with ``remove_offset_effects``.

**Combination with remove_offset_effects**

If you set up ``control_variables`` together with the ``remove_offset_effects`` feature, model metrics and scoring history (including under cross-validation) are calculated with both features enabled (that is, with both offset and control-variables effects removed during scoring). Four CV metric views are available in this case:

.. list-table::
   :header-rows: 1

   * - View
     - Slot
     - Control variables
     - Offset
   * - Default restricted
     - ``cross_validation_metrics``
     - zeroed
     - zeroed
   * - Control-variables-only-restricted
     - ``cross_validation_metrics_restricted_model_contr_vals``
     - zeroed
     - kept
   * - Offset-only-restricted
     - ``cross_validation_metrics_restricted_model_ro``
     - kept
     - zeroed
   * - Fully-unrestricted
     - ``cross_validation_metrics_unrestricted_model``
     - kept
     - kept

Each view has a matching ``..._summary`` table. Of these four, the fully-unrestricted view is the one directly comparable to a model that never used either feature; the other three carry the miscalibration caveats above for whichever effect(s) they zero.

To get a model with only one set of effects excluded, use ``glm.make_derived_glm_model()`` / ``h2o.make_derived_glm_model()`` with exactly one of its two flags set to ``True``:

- ``remove_control_variables_effects=True``: excludes the control-variables effects from scoring and metrics; the offset effects stay included.
- ``remove_offset_effects=True``: excludes the offset effects from scoring and metrics; the control-variables effects stay included.

The two flags cannot both be ``True`` in the same call.
If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation, and with ``keep_cross_validation_predictions=True`` a single ``train()`` call also materializes up to ``4 * (nfolds + 1)`` prediction frames -- worth budgeting memory for on large data.

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

		# control_variables also works with cross-validation:
		airlines_glm_cv <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
                           control_variables = c("Year", "DayOfWeek"),
                           nfolds = 5)

		# restricted CV deviance (control variables zeroed during CV scoring) -- this is the slot
		# grid search / AutoML would sort on, and it carries the miscalibration caveats above
		print(h2o.residual_deviance(airlines_glm_cv, xval = TRUE))

		# unrestricted CV deviance (control variables preserved during CV scoring) -- the directly
		# comparable number if you also trained a baseline model without control_variables
		print(airlines_glm_cv@model$cross_validation_metrics_unrestricted_model$residual_deviance)

		# derived model presents the full with-control-variables CV view consistently
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

		# control_variables also works with cross-validation:
		airlines_glm_cv = H2OGeneralizedLinearEstimator(family = 'binomial',
		                                                control_variables = ["Year", "DayOfWeek"],
		                                                nfolds = 5)
		airlines_glm_cv.train(x = predictors, y = response, training_frame = train)

		# restricted CV deviance (control variables zeroed during CV scoring) -- this is the slot
		# grid search / AutoML would sort on, and it carries the miscalibration caveats above
		print(airlines_glm_cv.model_performance(xval=True).residual_deviance())

		# unrestricted CV deviance (control variables preserved during CV scoring) -- the directly
		# comparable number if you also trained a baseline model without control_variables
		print(airlines_glm_cv.cross_validation_metrics_unrestricted_model["residual_deviance"])

		# derived model presents the full with-control-variables CV view consistently
		unrestricted_cv_glm = airlines_glm_cv.make_unrestricted_glm_model()
		print(unrestricted_cv_glm.model_performance(xval=True).residual_deviance())
