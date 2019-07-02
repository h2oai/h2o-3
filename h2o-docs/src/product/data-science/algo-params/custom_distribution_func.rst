.. _custom_distribution_func:

``custom_distribution_func``
----------------------------

- Available in: GBM 
- Hyperparameter: no

Description
~~~~~~~~~~~

Use this option to specify a custom distribution function. A custom distribution function can be used to customize a loss function calculation.

**Note**: This option is only supported in the Python client.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__

Example
~~~~~~~

.. example-code::

   .. code-block:: python

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

	# initialize the estimator then train the model
	airlines_gbm = H2OGradientBoostingEstimator(ntrees=3, 
	                                            max_depth=5,
	                                            distribution="bernoulli", 
	                                            seed=1234)
	                                            
	airlines_gbm.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

	# print the auc for the validation data
	airlines_gbm.auc(valid=True)

	# Use a custom distribution
	# Create a custom Bernoulli distribution and save as custom_bernoulli.py
	# Note that this references a java class java.lang.Math
	class CustomDistributionBernoulli:
	    def link(self):
	        return "logit"
	    
	    def init(self, w, o, y):
	        return [w * (y - o), w]
	    
	    def gradient(self, y, f):
	        return y - self.inversion(f)
	    
	    def gamma(self, w, y, z, f):
	        ff = y - z
	        return [w * z, w * ff * (1 - ff)]

	# Upload the custom distribution
	custom_dist_func = h2o.upload_custom_distribution(CustomDistributionBernoulli, 
	                                                  func_name="custom_bernoulli", 
	                                                  func_file="custom_bernoulli.py")

	# Train the model
	airlines_gbm_custom = H2OGradientBoostingEstimator(ntrees=3, 
	                                                   max_depth=5,
	                                                   distribution="custom",
	                                                   custom_distribution_func=custom_dist_func,
	                                                   seed=1234)
	                                     
	airlines_gbm_custom.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

	# print the auc for the validation data - the result should be the same
	airlines_gbm_custom.auc(valid=True)
