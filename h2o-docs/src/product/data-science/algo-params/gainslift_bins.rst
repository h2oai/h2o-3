``gainslift_bins``
------------------

- Available in: GBM, DRF, Naïve-Bayes, XGBoost, Uplift DRF
- Hyperparameter: no

Description
~~~~~~~~~~~

The `Kolmogorov-Smirnov (KS) <../../performance-and-prediction.html#kolmogorov-smirnov-ks-metric>`__ metric represents the degree of separation between the positive and negative distribution functions for a binomial model. Detailed metrics per each group can be found in the Gains/Lift table. 

The ``gainslift_bins`` option specifies the number of bins for a Gains/Lift table. The default value is ``-1`` and makes the binning automatic. To disable this feature, set to ``0``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
	.. code-tab:: r R

			library(h2o)
			h2o.init()

			# import the airlines dataset:
			airlines <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")

			# build and train the model:
			model <- h2o.gbm(x = c("Origin", "Distance"), 
					 y = "IsDepDelayed", 
					 training_frame = airlines, 
					 ntrees = 1, 
					 gainslift_bins = 20)

			# print the Gains/Lift table for the model:
			print(h2o.gainsLift(model))


	.. code-tab:: python

			import h2o
			from h2o.estimators import H2OGradientBoostingEstimator
			h2o.init()

			# import the airlines dataset:
			airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")

			# build and train the model:
			model = H2OGradientBoostingEstimator(ntrees=1, gainslift_bins=20)
			model.train(x=["Origin", "Distance"], y="IsDepDelayed", training_frame=airlines)

			# print the Gains/Lift table for the model:
			print(model.gains_lift())
