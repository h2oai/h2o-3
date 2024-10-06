``include_algos``
-----------------

- Available in: AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

This option allows you to specify a list of algorithms to include in an AutoML run during the model-building phase. This option defaults to None/Null, which means that  all algorithms are included unless any algorithms are specified in the ``exclude_algos`` option. Note that these two options cannot both be specified.

The algorithms that can be specified include:

- ``DRF`` (including both the Random Forest and Extremely Randomized Trees (XRT) models)
- ``GLM``
- ``XGBoost`` (XGBoost GBM)
- ``GBM`` (H2O GBM)
- ``DeepLearning`` (Fully-connected multi-layer artificial neural network)
- ``StackedEnsemble``

Related Parameters
~~~~~~~~~~~~~~~~~~

- `exclude_algos <exclude_algos.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R
   

		library(h2o)
		h2o.init()

		# Import a sample binary outcome training set into H2O
		train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_10k.csv")

		# Identify predictors and response
		x <- setdiff(names(train), y)
		y <- "response"

		# For binary classification, response should be a factor
		train[, y] <- as.factor(train[, y])

		# Train AutoML using only GLM, DeepLearning, and DRF
		aml <- h2o.automl(x = x, y = y,
		                  training_frame = train,
		                  max_runtime_secs = 30,
		                  sort_metric = "logloss",
		                  include_algos = c("GLM", "DeepLearning", "DRF"))

		# View the AutoML Leaderboard
		lb <- aml@leaderboard
		lb

		                                            model_id       auc   logloss
		1                       XRT_1_AutoML_20190321_094944 0.7402090 0.6051397
		2                       DRF_1_AutoML_20190321_094944 0.7431221 0.6057202
		3              DeepLearning_1_AutoML_20190321_094944 0.6994255 0.6309644
		4          GLM_grid_1_AutoML_20190321_094944_model_1 0.6826481 0.6385205
		5 DeepLearning_grid_1_AutoML_20190321_094944_model_1 0.6707953 0.7042976
		  mean_per_class_error      rmse       mse
		1            0.3545519 0.4539312 0.2060535
		2            0.3683363 0.4527405 0.2049739
		3            0.3892368 0.4687153 0.2196940
		4            0.3972341 0.4726827 0.2234290
		5            0.4385448 0.4911634 0.2412415

		[5 rows x 6 columns] 


   .. code-tab:: python

		import h2o
		from h2o.automl import H2OAutoML
		h2o.init()

		# Import a sample binary outcome training set into H2O
		train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_10k.csv")

		# Identify predictors and response
		x = train.columns
		y = "response"
		x.remove(y)

		# For binary classification, response should be a factor
		train[y] = train[y].asfactor()

		# Train AutoML using only GLM, DeepLearning, and DRF
		aml = H2OAutoML(max_runtime_secs = 30, sort_metric = "logloss",
		                include_algos = ["GLM", "DeepLearning", "DRF"])
		aml.train(x = x, y = y, training_frame = train)

		# View the AutoML Leaderboard
		lb = aml.leaderboard
		lb

		model_id                                                 auc    logloss    mean_per_class_error      rmse       mse
		--------------------------------------------------  --------  ---------  ----------------------  --------  --------
		XRT_1_AutoML_20190321_095341                        0.741603   0.60012                 0.342847  0.453342  0.205519
		DRF_1_AutoML_20190321_095341                        0.740674   0.60294                 0.375423  0.453271  0.205454
		DeepLearning_grid_1_AutoML_20190321_095341_model_1  0.711473   0.620394                0.387857  0.463987  0.215284
		GLM_grid_1_AutoML_20190321_095341_model_1           0.682648   0.63852                 0.397234  0.472683  0.223429
		DeepLearning_1_AutoML_20190321_095341               0.684733   0.639195                0.418683  0.472425  0.223185

		[5 rows x 6 columns]
