``interaction_constraints``
-----------------------------

- Available in: XGBoost, GBM
- Hyperparameter: no

Description
~~~~~~~~~~~

Specify the feature column interactions which are allowed to interact during tree building. Use column names to define which features can interact together. This option defaults to None/Null, which means that all column features are included. 

**Note**: This option can only be used when the categorical encoding is set to ``AUTO`` (``one_hot_internal`` or ``OneHotInternal``).

Overlap of interaction constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When the interaction constraints sets overlap, the XGBoost algorithm evaluates them differently then GBM algorithm. In this case the GBM checks the interaction constraints in the each whole branch, but XGBoost does not. 

For example for ``interaction_constraints = [["A", "B", "C"], ["C", "D"]]``:

- XGBoost allows branch with decision path "A" -> "C" -> "D"
- GBM does not allow branch with decision path "A" -> "C" -> "D", because "D" cannot interact with "A"

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

		# train a model using the interaction_constraints option
		prostate_xgb <- h2o.xgboost(y = response, 
	                        	interaction_constraints = list(list("AGE", "DCAPS")),
	                        	seed = 1234, 
	                        	training_frame = prostate)


   .. code-tab:: python

		import h2o
		from h2o.estimators.xgboost import H2OXGBoostEstimator
		h2o.init()

		# import the prostate dataset:
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

		# convert the CAPSULE column to a factor
		prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
		response = "CAPSULE"
		seed = 1234
		
		# train a model using the interaction_constraints option
		xgb_model = H2OXGBoostEstimator(seed=seed, interaction_constraints=[["AGE", "DCAPS"]])
		xgb_model.train(y=response, ignored_columns=["ID"], training_frame=prostate)
