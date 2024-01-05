``monotone_constraints``
------------------------

- Available in: AutoML, GBM, XGBoost
- Hyperparameter: no

Description
~~~~~~~~~~~

A mapping that represents monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint. Note that constraints can only be defined for numerical columns. 

**Note**: In GBM and XGBoost, this option can only be used when the distribution is ``gaussian``, ``bernoulli``, ``tweedie``. In GBM also ``quantile`` distribution is supported.

You can enable monotone constraints consistency check using the system property: ``sys.ai.h2o.tree.constraintConsistencyCheck=true``. It checks the parent prediction is in interval of the children predictions. It is disabled by default.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the prostate dataset:
		prostate = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

		# convert the CAPSULE column to a factor
		prostate$CAPSULE <- as.factor(prostate$CAPSULE)
		response <- "CAPSULE"

		# train a model using the monotone_constraints option
		prostate_gbm <- h2o.gbm(y = response, 
	                        	monotone_constraints = list(AGE = 1), 
	                        	seed = 1234, 
	                        	training_frame = prostate)


   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
		h2o.init()

		# import the prostate dataset:
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

		# convert the CAPSULE column to a factor
		prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
		response = "CAPSULE"
		seed = 1234
		
		# train a model using the monotone_constraints option
		monotone_constraints={"AGE":1}
		gbm_model = H2OGradientBoostingEstimator(seed=seed, monotone_constraints=monotone_constraints)
		gbm_model.train(y=response, ignored_columns=["ID"], training_frame=prostate)
