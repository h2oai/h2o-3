``remove_offset_effects``
-------------------------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

This feature allows you to remove offset effects during scoring and metric calculation.

Model metrics and scoring history are calculated for both the restricted model (with offset effects removed) and the unrestricted model (with offset effects included).

To get the unrestricted model with its own metrics, use ``glm.make_unrestricted_glm_model()`` / ``h2o.make_unrestricted_glm_model(glm)``.

**Cross-validation support**

When cross-validation is enabled (``nfolds`` or ``fold_column``), two parallel CV metric views are computed:

- **Restricted** (``cross_validation_metrics``, ``cross_validation_metrics_summary``): CV metrics computed with the offset zeroed out, consistent with the restricted training and validation metrics.
- **Unrestricted** (``cross_validation_metrics_unrestricted_model``, ``cross_validation_metrics_summary_unrestricted_model``): CV metrics computed with the offset preserved, matching the unrestricted training and validation metrics.

Calling ``make_unrestricted_glm_model()`` on a model trained with CV propagates the unrestricted CV metrics into the derived model's main ``cross_validation_metrics`` slot, so the derived model presents the full with-offset view consistently across training, validation, and CV.

**Lambda search support**

When ``lambda_search=True``, two parallel per-lambda scoring histories are produced:

- **Restricted** (``scoring_history``, titled *Scoring History offset-removed model*): one row per lambda with the offset removed from ``deviance_train``, ``deviance_test`` and ``deviance_xval``. Note that the two tables carry identical column names, and the Python client's ``scoring_history()`` returns a ``pandas.DataFrame``, which does not show the title -- so keep track of which slot you read.
- **Unrestricted** (``scoring_history_unrestricted_model``, titled *Scoring History unrestricted model*): the same rows with the offset preserved. These match a plain offset model trained with the same parameters.

``remove_offset_effects`` changes only the reported metrics, never the fit, so the model is identical to the one you would get without the option. Consequently **the best lambda is selected on the offset-preserved (unrestricted) deviance**, which guarantees that ``lambda_best`` and the coefficients match the equivalent model trained without ``remove_offset_effects``. One consequence is worth noting: the ``deviance_test`` column of the restricted ``scoring_history`` is *not* necessarily minimized at the selected lambda. To see the deviance that selection is based on, read ``scoring_history_unrestricted_model`` (or the derived model returned by ``make_unrestricted_glm_model()``).

Combining ``lambda_search`` with cross-validation is supported; the restricted history's ``deviance_xval`` is then the offset-removed cross-validated deviance, on the same scale as its ``deviance_test``.

.. note::

	``deviance_xval`` and ``deviance_test`` are true deviances (non-negative) for every family. ``deviance_train``, however, reports the negative log-likelihood rather than the deviance for the ``tweedie`` and ``negativebinomial`` families, and the ``tweedie`` value additionally omits a normalization term and can be negative. For those two families ``deviance_train`` is therefore **not** on the same scale as ``deviance_test``/``deviance_xval`` and the three columns should not be compared with each other. This applies equally to the restricted and unrestricted histories, and to models trained without ``remove_offset_effects``.
An empty ``deviance_xval``/``deviance_se`` cell in the restricted history means the offset-removed cross-validated deviance could not be computed (for example when a fold was resumed from a checkpoint); a warning is issued in that case, and ``scoring_history_unrestricted_model`` still reports the offset-included values.
Note that the per-lambda ``deviance_xval``/``deviance_se`` columns require ``nfolds``; with a ``fold_column`` the CV metric slots above are still populated and cross-validation still constrains the search (the alpha and the explored lambda range are narrowed to the cross-validated optimum), but those columns are omitted from the scoring history and the final ``lambda_best`` is taken from the training/validation path rather than from the cross-validated deviance.

**Combination with control_variables**

If you set up ``remove_offset_effects`` together with ``control_variables``, model metrics and scoring history are calculated with both features enabled (that is, with both offset and control-variables effects removed during scoring).
Neither cross-validation nor ``lambda_search`` is supported in combination with ``control_variables`` yet, so those two combinations are rejected.
If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation.

**Deriving a single-effect model**

``glm.make_derived_glm_model()`` / ``h2o.make_derived_glm_model()`` excludes exactly one set of effects from scoring and metrics. The source model only has to have been trained with the matching feature -- you do not need both:

- ``remove_control_variables_effects=True``: excludes the control-variables effects; the offset effects, if the source model has any, stay included. Requires the source model to have been trained with ``control_variables``.
- ``remove_offset_effects=True``: excludes the offset effects; the control-variables effects, if the source model has any, stay included. Requires the source model to have been trained with ``remove_offset_effects=True``.

The two flags cannot both be ``True`` in the same call. Calling the same derivation twice with the same ``dest`` returns the model created the first time; if a different model already occupies that key, the call is rejected.
If the source model was trained with only one of the two features, the corresponding derived model is equivalent to the source model in every reported number.

A derived model reports the source's selected ``lambda`` and ``alpha``, but it does **not** carry a regularization path of its own: ``h2o.getGLMFullRegularizationPath()`` / ``getGLMRegularizationPath()`` on a derived model returns a single row with ``NaN`` explained deviances. Read the regularization path from the source model instead.

Metric-based early stopping (``stopping_rounds``) is evaluated on the offset-preserved (unrestricted) metrics, so the stopping iteration and the selected lambda match the equivalent plain offset model. Note that ``stopping_rounds`` cannot be combined with ``lambda_search``, which has its own early-stopping mechanism (``early_stopping``).

**Notes**:

- This option is experimental.
- This option is not supported for multinomial, ordinal, or custom distributions.
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

		# remove_offset_effects also works with lambda_search:
		airlines_glm_ls <- h2o.glm(family = 'binomial', x = predictors, y = response, training_frame = train,
                           validation_frame = valid,
                           offset_column = "Distance",
                           remove_offset_effects = TRUE,
                           lambda_search = TRUE)

		# per-lambda history with the offset removed (this is what scoring_history shows)
		print(airlines_glm_ls@model$scoring_history)

		# per-lambda history with the offset preserved - this is the deviance lambda selection uses
		print(airlines_glm_ls@model$scoring_history_unrestricted_model)

		# the selected lambda matches the plain offset model, because the fit is unchanged
		print(h2o.getLambdaBest(airlines_glm_ls))


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

		# remove_offset_effects also works with lambda_search:
		airlines_glm_ls = H2OGeneralizedLinearEstimator(family = 'binomial',
		                                                offset_column = "Distance",
		                                                remove_offset_effects = True,
		                                                lambda_search = True)
		airlines_glm_ls.train(x = predictors, y = response, training_frame = train,
		                      validation_frame = valid)

		# per-lambda history with the offset removed (this is what scoring_history shows)
		print(airlines_glm_ls.scoring_history())

		# per-lambda history with the offset preserved - this is the deviance lambda selection uses
		print(airlines_glm_ls.scoring_history_unrestricted_model)

		# the selected lambda matches the plain offset model, because the fit is unchanged
		print(H2OGeneralizedLinearEstimator.getLambdaBest(airlines_glm_ls))
