.. _stopping_metric:

``stopping_metric``
-------------------

- Available in: GBM, DRF, Deep Learning, GLM, GAM, AutoML, XGBoost, Isolation Forest, UpliftDRF
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the metric to consider when early stopping is specified (i.e., when ``stopping_rounds`` > 0). For example, given the following options:

- ``stopping_rounds=3``
- ``stopping_metric=misclassification``
- ``stopping_tolerance=1e-3``

then the model will stop training after reaching three scoring events in a row in which a model's missclassication value does not improve by **1e-3**. These stopping options are used to increase performance by restricting the number of models that get built.

Available options for ``stopping_metric`` include the following:

- ``AUTO``: This defaults to ``logloss`` for classification, ``deviance`` (mean residual deviance) for regression, and ``anomaly_score`` for Isolation Forest.
- ``anomaly_score`` (for Isolation Forest only)
- ``deviance``
- ``logloss``
- ``MSE``
- ``RMSE``
- ``MAE``
- ``RMSLE``
- ``AUC`` (area under the ROC curve)
- ``AUCPR`` (area under the Precision-Recall curve)
- ``lift_top_group``
- ``misclassification``
- ``mean_per_class_error``
- ``AUUC`` (area under the uplift curve, for UpliftDRF only)
- ``qini`` (difference between the Qini AUUC and area under the random uplift curve, for UpliftDRF only)
- ``ATE`` (average treatment effect, for UpliftDRF only)
- ``ATT`` (average treatment effect on the Treated, for UpliftDRF only)
- ``ATC`` (average treatment effect on the Control, for UpliftDRF only)
- ``custom`` (for custom metric functions where "less is better". It is expected that the lower bound is 0.) Note that this is currently only supported in the Python client for GBM and DRF. More information available in Python example below and `here <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/dev/custom_functions.md>`__.
- ``custom_increasing`` (for custom metric functions where "more is better".) Note that this is currently only supported in the Python client for GBM and DRF. More information available in Python example below and `here <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/dev/custom_functions.md>`__.

**Note**: ``stopping_rounds`` must be enabled for ``stopping_metric`` or ``stopping_tolerance`` to work.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `stopping_rounds <stopping_rounds.html>`__
- `stopping_tolerance <stopping_tolerance.html>`__


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
		airlines_splits <- h2o.splitFrame(data = airlines, ratios = 0.8, seed = 1234)
		train <- airlines_splits[[1]]
		valid <- airlines_splits[[2]]

		# try using the `stopping_metric` parameter: 
		# since this is a classification problem we will look at the AUC
		# you could also choose logloss, or misclassification, among other options

		# train your model, where you specify the stopping_metric, stopping_rounds, 
		# and stopping_tolerance
		airlines_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
		                        stopping_metric = "AUC", stopping_rounds = 3,
		                        stopping_tolerance = 1e-2, seed = 1234)

		# print the auc for the validation data
		print(h2o.auc(airlines_gbm, valid = TRUE))


   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
		h2o.init()
		h2o.cluster().show_status()

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
		train, valid= airlines.split_frame(ratios = [.8], seed = 1234)

		# try using the `stopping_metric` parameter: 
		# since this is a classification problem we will look at the AUC
		# you could also choose logloss, or misclassification, among other options
		# train your model, where you specify the stopping_metric, stopping_rounds, 
		# and stopping_tolerance
		# initialize the estimator then train the model
		airlines_gbm = H2OGradientBoostingEstimator(stopping_metric = "auc", stopping_rounds = 3,
		                                            stopping_tolerance = 1e-2,
		                                            seed =1234)
		airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		airlines_gbm.auc(valid=True)

		# Example using a custom metric
		# Create a custom RMSE Model metric and save as mm_rmse.py
		# Note that this references a java class java.lang.Math
		class CustomRmseFunc:
		def map(self, pred, act, w, o, model):
		    idx = int(act[0])
		    err = 1 - pred[idx + 1] if idx + 1 < len(pred) else 1
		    return [err * err, 1]

		def reduce(self, l, r):
		    return [l[0] + r[0], l[1] + r[1]]

		def metric(self, l):
		    # Use Java API directly
		    import java.lang.Math as math
		    return math.sqrt(l[0] / l[1])

		# Upload the custom metric
		custom_mm_func = h2o.upload_custom_metric(CustomRmseFunc, 
		                                          func_name="rmse", 
		                                          func_file="mm_rmse.py")

		# Train the model
		model = H2OGradientBoostingEstimator(ntrees=3, 
		                                     max_depth=5,
		                                     score_each_iteration=True,
		                                     custom_metric_func=custom_mm_func,
		                                     stopping_metric="custom",
		                                     stopping_tolerance=0.1,
		                                     stopping_rounds=3)
		model.train(x=predictors, y=response, training_frame train, validation_frame = valid)
