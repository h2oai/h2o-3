.. _custom_distribution_func:

``custom_distribution_func``
----------------------------

- Available in: GBM 
- Hyperparameter: no

Description
~~~~~~~~~~~

Use this option to specify a custom distribution function, which can then be used to customize a loss function calculation.

**Notes**: 

- This option is only supported in the Python client.
- A demo for the custom distribution function is available here: https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/custom_loss_function_demo.ipynb
- Additional information is located at https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/dev/custom_functions.md

Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__
- `upload_custom_distribution <upload_custom_distribution.html>`__

Example
~~~~~~~

.. tabs::

   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
	 	h2o.init()
		h2o.cluster().show_status()

		# Import the airlines dataset:
		# this dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
		# original data can be found at http://www.transtats.bts.gov/
		airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

		# convert columns to factors
		airlines["Year"] = airlines["Year"].asfactor()
		airlines["Month"] = airlines["Month"].asfactor()
		airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
		airlines["Cancelled"] = airlines["Cancelled"].asfactor()
		airlines['FlightNum'] = airlines['FlightNum'].asfactor()

		# set the predictor names and the response column name
		predictors = ["Origin", "Dest", "Year", "UniqueCarrier", 
		              "DayOfWeek", "Month", "Distance", "FlightNum"]
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
		print(airlines_gbm.auc(valid=True))

		# use a custom distribution now
		# create a custom Bernoulli distribution and save as custom_bernoulli.py
		# note that this references a java class java.lang.Math

		class MyBernoulli():

			def exp(self, x):
				import java.lang.Math as Math
				max_exp = 1e19
				return Math.min(max_exp, Math.exp(x))

			def link(self):
				return "logit"

			def init(self, w, o, y):
				return [w * (y - o), w]

			def gradient(self, y, f):
				return y - (1 / (1 + self.exp(-f)))

			def gamma(self, w, y, z, f):
				ff = y - z
				return [w * z, w * ff * (1 - ff)]


		# upload the custom distribution
		custom_dist_func = h2o.upload_custom_distribution(MyBernoulli,
		                                                  func_name="custom_bernoulli",
		                                                  func_file="custom_bernoulli.py")

		# train the model
		airlines_gbm_custom = H2OGradientBoostingEstimator(ntrees=3,
		                                                   max_depth=5,
		                                                   distribution="custom",
		                                                   custom_distribution_func=custom_dist_func,
		                                                   seed=1234)

		airlines_gbm_custom.train(x=predictors, y=response, 
		                          training_frame=train, validation_frame=valid)

		# print the auc for the validation data - the result should be the same
		print(airlines_gbm_custom.auc(valid=True))

		# To customize a distribution for special type of problem we recommend you to inherit from predefined classes:
		# - CustomDistributionGaussian - for regression problems
		# - CustomDistributionBernoulli - for 2-class classification problems
		# - CustomDistributionMultinomial - for n-class classification problems

		# For example if you want to apply asymmetric loss function in a classification problem, you can implement a class
		# which inherits from CustomDistributionBernoulli

		from h2o.utils.distributions import CustomDistributionBernoulli

		class MyBernoulliAsymmetric(CustomDistributionBernoulli):
			def gradient(self, y, f):
				error = y - (1 / (1 + self.exp(-f)))
				return 0.5 * error if error < 0 else 2 * error


		# Upload the custom distribution
		custom_dist_func = h2o.upload_custom_distribution(MyBernoulliAsymmetric,
		                                                  func_name="custom_bernoulli_asym",
		                                                  func_file="custom_bernoulli_asym.py")

		# Train the model
		airlines_gbm_custom_asym = H2OGradientBoostingEstimator(ntrees=3,
		                                                        max_depth=5,
		                                                        distribution="custom",
		                                                        custom_distribution_func=custom_dist_func,
		                                                        seed=1234)

		airlines_gbm_custom_asym.train(x=predictors, y=response, 
		                               training_frame=train, validation_frame=valid)
		print(airlines_gbm_custom_asym.auc(valid=True))
