.. _custom_metric_func:

``custom_metric_func``
----------------------

- Available in: GBM, DRF, Deeplearning, Stacked Ensembles, GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Use this option to specify a custom evaluation function. A custom metric function can be used to produce adhoc scoring metrics if actuals are presented.

**Note**: This option is only supported in the Python client.

**Note**: In Deeplearning, custom metric is not supported for Auto-encoder option.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `upload_custom_metric <upload_custom_metric.html>`__
- `stopping_metric <stopping_metric.html>`__

Example
~~~~~~~

.. tabs::

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
		airlines["Year"] = airlines["Year"].asfactor()
		airlines["Month"] = airlines["Month"].asfactor()
		airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
		airlines["Cancelled"] = airlines["Cancelled"].asfactor()
		airlines['FlightNum'] = airlines['FlightNum'].asfactor()

		# set the predictor names and the response column name
		predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
		response = "IsDepDelayed"

		# split into train and validation sets 
		train, valid = airlines.split_frame(ratios=[.8], seed=1234)

		# try using the `stopping_metric` parameter: 
		# since this is a classification problem we will look at the AUC
		# you could also choose logloss, or misclassification, among other options
		# train your model, where you specify the stopping_metric, stopping_rounds, 
		# and stopping_tolerance
		# initialize the estimator then train the model
		airlines_gbm = H2OGradientBoostingEstimator(stopping_metric="auc",
		                                            stopping_rounds=3,
		                                            stopping_tolerance=1e-2,
		                                            seed=1234)
		airlines_gbm.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

		# print the auc for the validation data
		airlines_gbm.auc(valid=True)

		# Use a custom metric
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
		model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
