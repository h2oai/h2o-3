``monotone_constraints``
------------------------

- Available in: GBM, XGBoost
- Hyperparameter: no

Description
~~~~~~~~~~~

A mapping that represents monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint. Note that constraints can only be defined for numerical columns. 

**Note**: This option can only be used when the distribution is either ``gaussian`` or ``bernoulli``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	#import the prostate dataset:
	prostate = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
	#wip
	prostate$CAPSULE <- as.factor(prostate$CAPSULE)
	response <- "CAPSULE"
	#enforce a constraint
	prostate.gbm <- h2o.gbm(y=response, monotone_constraints=list(AGE = 1), seed=1234, training_frame=prostate)


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the prostate dataset:
	prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
	#wip
	prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
	response = "CAPSULE"
	seed = 1234
	#enforce a constraint
	monotone_constraints={"AGE":1}
	gbm_model = H2OGradientBoostingEstimator(seed=seed, monotone_constraints=monotone_constraints)
	gbm_model.train(y=response, ignored_columns=["ID"], training_frame=prostate)