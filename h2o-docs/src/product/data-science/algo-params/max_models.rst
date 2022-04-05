``max_models``
--------------

- Available in: AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

Use this option to specify the maximum number of models to build in the AutoML run, excluding the Stacked Ensemble models. This option defaults to Null/None.
This option should systematically be set if AutoML reproducibility is needed: all models are then trained until convergence and none is constrained by a time budget.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `max_runtime_secs <max_runtime_secs.html>`__
- `max_runtime_secs_per_model <max_runtime_secs_per_model.html>`__
- `seed <seed.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the prostate dataset
		prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")

		# Set the predictor names and the response column name
		y <- "CAPSULE"
		x <- setdiff(names(prostate), c(p_y, "ID"))

		# Train AutoML
		aml <- h2o.automl(x = x,
		                  y = y,
		                  training_frame = prostate,
		                  seed = 1234,
		                  max_models = 5,
		                  max_runtime_secs = 200,
		                  max_runtime_secs_per_model = 40)

		# View the AutoML Leaderboard
		lb <- aml@leaderboard
		lb

			                                             model_id mean_residual_deviance
		1 StackedEnsemble_BestOfFamily_AutoML_20190321_110032           0.0009730593
		2    StackedEnsemble_AllModels_AutoML_20190321_110032           0.0009730593
		3                        DRF_1_AutoML_20190321_110032           0.0012766064
		4                        XRT_1_AutoML_20190321_110032           0.0038347775
		5                    XGBoost_2_AutoML_20190321_110032           0.0064206276
		6                    XGBoost_1_AutoML_20190321_110032           0.0544174809
		        rmse          mse        mae      rmsle
		1 0.03119390 0.0009730593 0.02086672 0.02368844
		2 0.03119390 0.0009730593 0.02086672 0.02368844
		3 0.03572963 0.0012766064 0.01406001 0.02661268
		4 0.06192558 0.0038347775 0.03330358 0.04958889
		5 0.08012882 0.0064206276 0.06873394 0.06112533
		6 0.23327555 0.0544174809 0.18390358 0.16640402

		[7 rows x 6 columns] 

   .. code-tab:: python

		import h2o
		from h2o.automl import H2OAutoML
		h2o.init()

		# Import a sample binary outcome training set into H2O
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")

		# Set the predictor names and the response column name
		response = "CAPSULE"
		predictor = prostate.names[2:9]

		# Train AutoML
		aml = H2OAutoML(max_models = 5,
		                max_runtime_secs = 200,
		                max_runtime_secs_per_model = 40,
		                seed = 1234)
		aml.train(x = predictor, y = response, training_frame = prostate)

		# View the AutoML Leaderboard
		lb = aml.leaderboard
		lb
		model_id                                               mean_residual_deviance       rmse          mse        mae      rmsle
		---------------------------------------------------  ------------------------  ---------  -----------  ---------  ---------
		StackedEnsemble_AllModels_AutoML_20190321_111608                  0.000282073  0.016795   0.000282073  0.0103226  0.0129982
		StackedEnsemble_BestOfFamily_AutoML_20190321_111608               0.000282073  0.016795   0.000282073  0.0103226  0.0129982
		DRF_1_AutoML_20190321_111608                                      0.000334287  0.0182835  0.000334287  0.0076525  0.0140754
		XRT_1_AutoML_20190321_111608                                      0.0015397    0.039239   0.0015397    0.0217268  0.0293752
		XGBoost_2_AutoML_20190321_111608                                  0.0118094    0.108671   0.0118094    0.0888375  0.0804565
		XGBoost_1_AutoML_20190321_111608                                  0.0675672    0.259937   0.0675672    0.213536   0.184793
		GLM_grid_1_AutoML_20190321_111608_model_1                         0.193551     0.439944   0.193551     0.397327   0.306996

		[7 rows x 6 columns]
