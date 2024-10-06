``exclude_algos``
-----------------

- Available in: AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

This option allows you to specify a list of algorithms that should not be included in an AutoML run during the model-building phase. This option defaults to None/Null, which means that  all algorithms are included. However, if the ``include_algos`` option is used, then the AutoML run will include only those specified algorithms. Note that these two options cannot both be specified.

The algorithms that can be specified include:

- ``DRF`` (including both the Random Forest and Extremely Randomized Trees (XRT) models)
- ``GLM``
- ``XGBoost`` (XGBoost GBM)
- ``GBM`` (H2O GBM)
- ``DeepLearning`` (Fully-connected multi-layer artificial neural network)
- ``StackedEnsemble``

Related Parameters
~~~~~~~~~~~~~~~~~~

- `include_algos <include_algos.html>`__

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

		# Train AutoML, omitting DeepLearning and DRF
		aml <- h2o.automl(x = x, y = y,
		                  training_frame = train,
		                  max_runtime_secs = 30,
		                  sort_metric = "logloss",
		                  exclude_algos = c("DeepLearning", "DRF"))

		# View the AutoML Leaderboard
		lb <- aml@leaderboard
		lb

		                                             model_id       auc   logloss
		1    StackedEnsemble_AllModels_AutoML_20190321_095825 0.7866967 0.5550255
		2 StackedEnsemble_BestOfFamily_AutoML_20190321_095825 0.7848515 0.5569458
		3                    XGBoost_1_AutoML_20190321_095825 0.7846668 0.5578654
		4                    XGBoost_2_AutoML_20190321_095825 0.7820392 0.5586830
		5           GLM_grid_1_AutoML_20190321_095825_model_1 0.6826481 0.6385205
		  mean_per_class_error      rmse       mse
		1            0.3309041 0.4338530 0.1882284
		2            0.3231440 0.4346720 0.1889397
		3            0.3324049 0.4349659 0.1891953
		4            0.3269806 0.4356756 0.1898132
		5            0.3972341 0.4726827 0.2234290

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

		# Train AutoML, omitting DeepLearning and DRF
		aml = H2OAutoML(max_runtime_secs = 30, sort_metric = "logloss",
		                exclude_algos = ["DeepLearning", "DRF"])
		aml.train(x = x, y = y, training_frame = train)

		# View the AutoML Leaderboard
		lb = aml.leaderboard
		lb

		model_id                                                 auc    logloss    mean_per_class_error      rmse       mse
		--------------------------------------------------  --------  ---------  ----------------------  --------  --------
		DRF_1_AutoML_20190321_100107                        0.744882   0.597348                0.360293  0.452093  0.204388
		XRT_1_AutoML_20190321_095341                        0.741603   0.60012                 0.342847  0.453342  0.205519
		XRT_1_AutoML_20190321_100107                        0.740636   0.600695                0.356075  0.453646  0.205795
		DRF_1_AutoML_20190321_095341                        0.740674   0.60294                 0.375423  0.453271  0.205454
		DeepLearning_grid_1_AutoML_20190321_095341_model_1  0.711473   0.620394                0.387857  0.463987  0.215284
		DeepLearning_1_AutoML_20190321_100107               0.703753   0.628472                0.401192  0.467294  0.218363
		GLM_grid_1_AutoML_20190321_095341_model_1           0.682648   0.63852                 0.397234  0.472683  0.223429
		GLM_grid_1_AutoML_20190321_100107_model_1           0.682648   0.63852                 0.397234  0.472683  0.223429
		DeepLearning_1_AutoML_20190321_095341               0.684733   0.639195                0.418683  0.472425  0.223185
		DeepLearning_grid_1_AutoML_20190321_100107_model_1  0.670713   0.643133                0.434458  0.475507  0.226107

		[10 rows x 6 columns]

