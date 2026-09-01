``remove_offset_effects``
-------------------------

- Available in: GLM, GBM, XGBoost, GAM, CoxPH, Deep Learning, HGLM, Stacked Ensemble
- Hyperparameter: no

Description
~~~~~~~~~~~

This feature allows you to remove offset effects during scoring and metric calculation. The model is still trained with the offset; only scoring and metrics behave as if the offset were 0.

Throughout this page, the **restricted** view means predictions/metrics computed with the offset effects removed (as if the offset were 0), and the **unrestricted** view means predictions/metrics computed with the offset applied (the way a plain offset model scores). Model metrics are calculated for both views:

- The model's primary metrics, scoring history, and early stopping use the **restricted** view. (Exception: GLM's early stopping uses the offset-applied view.)
- The parallel **unrestricted** metrics are reported in the ``training_metrics_unrestricted_model``, ``validation_metrics_unrestricted_model``, and ``cross_validation_metrics_unrestricted_model`` fields of the model output. Retrieve them with ``model.unrestricted_model_performance(train/valid/xval)`` in Python or ``h2o.unrestricted_model_performance(model, train/valid/xval)`` in R.

Because the offset is ignored at scoring time, the offset column may be omitted from frames passed to ``predict``; a zero column is substituted automatically.

GLM-specific behavior:

- To get the unrestricted GLM model with its own metrics use ``glm.make_unrestricted_glm_model()`` / ``h2o.make_unrestricted_glm_model(glm)``.
- If you set up the ``remove_offset_effects`` together with the ``control_variables`` model metrics and scoring history are calculated with both features enabled (that is, with both offset and control-variable effects removed during scoring).
  If you need to get a model with only one feature enabled, you can get it using ``glm.make_derived_glm_model(remove_control_variables_effects=True)`` or ``glm.make_derived_glm_model(remove_offset_effects=True)``.
  If both features are enabled and ``score_each_iteration=True`` or ``generate_scoring_history=True``, training the model on big data can be slowed down. The complexity is four times higher than the standard GLM metric calculation.

**Notes**:

- This option is experimental.
- Stacked Ensemble inherits this option from its base models: an ensemble built on base models that remove the offset removes it too, and the offset column is kept out of the level-one frame so the metalearner cannot add it back. All base models must agree on the setting; mixing offset-removed and plain base models is rejected.
- Not available for DRF (which rejects ``offset_column`` outright), nor for ANOVAGLM and ModelSelection (which do not support scoring at all -- they report only predictor relevance, so there is nothing for this option to affect).
- This option requires an ``offset_column``. Setting ``remove_offset_effects=True`` without one is rejected during validation.
- GLM only: this option is not supported for multinomial, ordinal, or custom distributions, and is not available when cross validation, Lambda search, or interactions are enabled. GAM enforces the same restrictions (it trains an internal GLM).
- Cross validation is supported for GBM and XGBoost. It is not available for GAM (GAM performs cross validation inside its internal GLM). CoxPH does not support cross validation at all.
- The unrestricted cross-validation view is not available for ``distribution="huber"``, because huber's ``mean_residual_deviance`` is derived from the combined holdout predictions, which the aggregated view does not produce. The primary (offset-removed) cross-validation metrics are unaffected.
- The unrestricted cross-validation metric is a pooled holdout metric built from the per-fold metric builders rather than from combined holdout predictions, so it has no gains/lift table. Every scalar metric (including AUC) is computed by the same accumulator as its restricted twin over the same holdout rows, so the two remain directly comparable.
- With ``balance_classes`` enabled (GBM), the primary metrics are computed on the balanced (resampled) training frame while the offset-applied metrics are computed on the original one, so the two views cover different rows and are not directly comparable. A warning is added to the model when this happens.
- The unrestricted view is only produced for a validation frame that actually contains the offset column. A remove_offset model accepts frames without it (a zero column is substituted), which would make the "offset applied" view numerically identical to its twin, so it is omitted instead.
- Computing the unrestricted view adds one extra scoring pass over the training (and validation) frame after training, and one extra holdout-scoring pass per fold under cross validation.
- For classification models, the default prediction threshold is derived from the restricted training metrics, so predicted *labels* can differ from an equivalent plain offset model near the decision boundary even where the predicted probabilities agree.
- A MOJO built from a remove_offset model advertises no offset column and scores without it, consistent with in-cluster predictions; an offset supplied to such a MOJO by a caller is ignored.
- Deep Learning applies the offset as ``(offset - mean(response)) * scale`` in standardized response space, and only for strictly positive offsets. Scoring with an offset of 0 would therefore take the "skip" branch and leave the response-mean term in place, biasing every prediction by ``mean(response)``; instead the zero offset is pushed through the same formula, which yields the genuinely offset-free prediction. Training is unchanged, so the fit is identical to a plain offset model.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `offset_column <offset_column.html>`__
- `control_variables <control_variables.html>`__

Example (GBM)
~~~~~~~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()
		# import the prostate dataset
		prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# train a GBM with an offset but report offset-free predictions and metrics
		prostate_gbm <- h2o.gbm(x = c("CAPSULE", "RACE", "PSA", "VOL", "DPROS"), y = "AGE",
		                        training_frame = prostate,
		                        offset_column = "GLEASON",
		                        remove_offset_effects = TRUE)

		# primary (restricted, offset-removed) metrics
		h2o.performance(prostate_gbm, train = TRUE)

		# parallel unrestricted (offset-applied) metrics
		h2o.unrestricted_model_performance(prostate_gbm, train = TRUE)

		# the offset column may be omitted from scoring frames
		h2o.predict(prostate_gbm, prostate[setdiff(names(prostate), "GLEASON")])

   .. code-tab:: python

		import h2o
		from h2o.estimators import H2OGradientBoostingEstimator
		h2o.init()

		# import the prostate dataset
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# train a GBM with an offset but report offset-free predictions and metrics
		prostate_gbm = H2OGradientBoostingEstimator(offset_column="GLEASON",
		                                            remove_offset_effects=True)
		prostate_gbm.train(x=["CAPSULE", "RACE", "PSA", "VOL", "DPROS"], y="AGE",
		                   training_frame=prostate)

		# primary (restricted, offset-removed) metrics
		prostate_gbm.model_performance(train=True)

		# parallel unrestricted (offset-applied) metrics
		prostate_gbm.unrestricted_model_performance(train=True)

		# the offset column may be omitted from scoring frames
		prostate_gbm.predict(prostate.drop("GLEASON"))

Example (GLM)
~~~~~~~~~~~~~

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
