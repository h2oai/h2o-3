.. _sort_metric:

``sort_metric``
-------------------

- Available in: AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

This option specifies the metric used to sort the Leaderboard by at the end of an AutoML run.  Since the "leader model" is the model which has the "best" score on the leaderboard, the leader may change if you change this metric.  Most of the time, a Stacked Ensemble will remain in the leader spot even if you change the sorting metric, however, the ranking of the base models will likely change.

Available options for ``sort_metric`` include the following:

- ``AUTO``: This defaults to ``AUC`` for binary classification, ``mean_per_class_error`` for multinomial classification, and ``deviance`` for regression.
- ``deviance`` (mean residual deviance)
- ``logloss``
- ``MSE``
- ``RMSE``
- ``MAE``
- ``RMSLE``
- ``AUC`` (area under the ROC curve)
- ``AUCPR`` (area under the Precision-Recall curve)
- ``mean_per_class_error``

For binomial classification choose between ``AUC``, ``"logloss"``, ``"mean_per_class_error"``, ``"RMSE"``, ``"MSE"``. For multinomial classification choose between ``"mean_per_class_error"``, ``"logloss"``, ``"RMSE"``, ``"MSE"``.  For regression choose between ``"deviance"``, ``"RMSE"``, ``"MSE"``, ``"MAE"``, ``"RMLSE"``.


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

		aml <- h2o.automl(x = x, y = y,
		                  training_frame = train,
		                  max_runtime_secs = 30,
		                  sort_metric = "logloss")

		# View the AutoML Leaderboard
		lb <- aml@leaderboard
		lb

		#                                                model_id       auc   logloss
		# 1    StackedEnsemble_AllModels_0_AutoML_20180604_224722 0.7816898 0.5603201
		# 2 StackedEnsemble_BestOfFamily_0_AutoML_20180604_224722 0.7789486 0.5627137
		# 3             GBM_grid_0_AutoML_20180604_224722_model_4 0.7772998 0.5643765
		# 4             GBM_grid_0_AutoML_20180604_224722_model_0 0.7725441 0.5674791
		# 5             GBM_grid_0_AutoML_20180604_224722_model_1 0.7699201 0.5696827
		# 6             GBM_grid_0_AutoML_20180604_224722_model_2 0.7700669 0.5707551
		#   mean_per_class_error      rmse       mse
		# 1            0.3343797 0.4361733 0.1902471
		# 2            0.3324797 0.4373470 0.1912724
		# 3            0.3267255 0.4380983 0.1919301
		# 4            0.3323849 0.4398505 0.1934685
		# 5            0.3281922 0.4412005 0.1946579
		# 6            0.3438066 0.4412901 0.1947369
		# 
		# [10 rows x 6 columns] 


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

		# Run AutoML for 30 seconds
		aml = H2OAutoML(max_runtime_secs = 30, sort_metric = "logloss")
		aml.train(x = x, y = y,
		          training_frame = train)

		# View the AutoML Leaderboard
		lb = aml.leaderboard
		lb

		# model_id                                                    auc    logloss    mean_per_class_error      rmse       mse
		# -----------------------------------------------------  --------  ---------  ----------------------  --------  --------
		# StackedEnsemble_AllModels_0_AutoML_20180605_001915     0.783325   0.558667                0.313514  0.435453  0.18962
		# StackedEnsemble_BestOfFamily_0_AutoML_20180605_001915  0.780711   0.56117                 0.317926  0.436721  0.190726
		# GBM_grid_0_AutoML_20180605_001915_model_0              0.777781   0.562631                0.330729  0.437568  0.191466
		# GBM_grid_0_AutoML_20180605_001915_model_1              0.775025   0.56548                 0.329763  0.438794  0.19254
		# GBM_grid_0_AutoML_20180605_001915_model_2              0.769711   0.569923                0.334983  0.441401  0.194835
		# GBM_grid_0_AutoML_20180605_001915_model_3              0.761701   0.579553                0.345298  0.445009  0.198033
		# DRF_0_AutoML_20180605_001915                           0.743439   0.594876                0.35481   0.452465  0.204725
		# XRT_0_AutoML_20180605_001915                           0.735455   0.605614                0.370628  0.455573  0.207547
		# GLM_grid_0_AutoML_20180605_001915_model_0              0.68048    0.639935                0.393134  0.473447  0.224152
		#
		# [9 rows x 6 columns]


