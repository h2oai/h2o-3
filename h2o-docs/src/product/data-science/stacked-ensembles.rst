Stacked Ensembles
-----------------

H2O's Stacked Ensembles method is a loss-based supervised learning method that finds the optimal combination of a collection of prediction algorithms. This method supports regression and binary classification.

Introduction
~~~~~~~~~~~~

Ensemble machine learning methods use multiple learning algorithms to obtain better predictive performance than could be obtained from any of the constituent learning algorithms. 

Many of the popular modern machine learning algorithms are actually ensembles. For example, Random Forest and Gradient Boosting Machine are both ensemble learners.

Common types of ensembles include:

- Bagging
- Boosting
- Stacking

Bagging
'''''''

Bootstrap aggregating, or bagging, is an ensemble method designed to improve the stability and accuracy of machine learning algorithms. It reduces variance and helps to avoid overfitting. Bagging is a special case of the model averaging approach and is relatively robust against noisy data and outliers.

One of the most well known bagging ensembles is the Random Forest algorithm, which applies bagging to decision trees.

Boosting
''''''''

Boosting is an ensemble method designed to reduce bias and variance. A boosting algorithm iteratively learns weak classifiers and adds them to a final strong classifier.

After a weak learner is added, the data is reweighted: examples that are misclassified gain weight, and examples that are classified correctly lose weight. Thus, future weak learners focus more on the examples that the previous weak learners misclassified. This causes boosting methods to be not very robust to noisy data and outliers.

Both bagging and boosting are ensembles that take a collection of weak learners and form a single, strong learner.

Stacking / Super Learning
'''''''''''''''''''''''''

Stacking is a broad class of algorithms that involves training a second-level "metalearner" to ensemble a group of base learners. The type of ensemble learning implemented in H2O is called "super learning", "stacked regression" or "stacking." Unlike bagging and boosting, the goal in stacking is to ensemble strong, diverse sets of learners together.

Defining a Stacked Ensemble Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  **model_id**: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  **training_frame**: Specify the dataset used to build the model. 

-  **validation_frame**: Specify the dataset used to evaluate the accuracy of the model.

-  **selection_strategy**: Specify the strategy for choosing which models to stack. Note that **choose_all** is currently the only selection strategy implemented. 

-  **base_models**: Specify a vector of model IDs that can be stacked together. Models must have been cross-validated using ``nfolds`` > 1, and they all must use the same cross-validation folds.

Training and Testing a Super Learner Ensemble
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The steps below describe the tasks involved in training and testing a Super Learner ensemble.

1. Set up the ensemble.

   a. Specify a list of L base algorithms (with a specific set of model parameters).
   b. Specify a metalearning algorithm.

2. Train the ensemble.

   a. Train each of the L base algorithms on the training set.
   b. Perform k-fold cross-validation on each of these learners and collect the cross-validated predicted values from each of the L algorithms.
   c. The N cross-validated predicted values from each of the L algorithms can be combined to form a new N x L matrix. This matrix, along wtih the original response vector, is called the "level-one" data. (N = number of rows in the training set.)
   d. Train the metalearning algorithm on the level-one data.
      The "ensemble model" consists of the L base learning models and the metalearning model, which can then be used to generate predictions on a test set.

3. Predict on new data.

   a. To generate ensemble predictions, first generate predictions from the base learners.
   b. Feed those predictions into the metalearner to generate the ensemble prediction.

Example
'''''''

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init(nthreads = -1)

	# Import a sample binary outcome train/test set into H2O
	train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
	test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

	y <- "response"
	x <- setdiff(names(train), y)

	# For binary classification, response should be a factor
	train[,y] <- as.factor(train[,y])
	test[,y] <- as.factor(test[,y])
	nfolds <- 5  #number of CV folds (to generate level-one data for stacking)

	# There are a few ways to assemble a list of models to stack toegether:
	# 1. Train individual models and put them in a list
	# 2. Train a grid of models
	# 3. Train several grids of models
	# Note: All base models must have the same cross-validation folds and 
	# the cross-validated predicted values must be kept.



	# 1. Generate a 2-model ensemble (GBM + RF)

	# Train & Cross-validate a GBM
	my_gbm <- h2o.gbm(x = x, 
	               y = y, 
	               training_frame = train, 
	               distribution = "bernoulli",
	               ntrees = 10, 
	               max_depth = 3,
	               min_rows = 2, 
	               learn_rate = 0.2, 
	               nfolds = nfolds, 
	               fold_assignment = "Modulo",
	               keep_cross_validation_predictions = TRUE,
	               seed = 1)

	# Train & Cross-validate a RF
	my_rf <- h2o.randomForest(x = x,
	                       y = y, 
	                       training_frame = train, 
	                       ntrees = 50, 
	                       nfolds = nfolds, 
	                       fold_assignment = "Modulo",
	                       keep_cross_validation_predictions = TRUE,
	                       seed = 1)

	# Train a stacked ensemble using the GBM and RF above
	ensemble <- h2o.stackedEnsemble(x = x, 
	                             y = y, 
	                             training_frame = train,
	                             model_id = "my_ensemble_binomial", 
	                             selection_strategy = "choose_all",
	                             base_models = list(my_gbm@model_id, my_rf@model_id))

	# Eval ensemble performance on a test set
	perf <- h2o.performance(ensemble, newdata = test)

	# Compare to base learner performance on the test set
	perf_gbm_test <- h2o.performance(my_gbm, newdata = test)
	perf_rf_test <- h2o.performance(my_rf, newdata = test)
	baselearner_best_auc_test <- max(h2o.auc(perf_gbm_test), h2o.auc(perf_rf_test))
	ensemble_auc_test <- h2o.auc(perf)
	print(sprintf("Best Base-learner Test AUC:  %s", baselearner_best_auc_test))
	print(sprintf("Ensemble Test AUC:  %s", ensemble_auc_test))

	# Generate predictions on a test set (if neccessary)
	pred <- h2o.predict(ensemble, newdata = test)


	# 2. Generate a random grid of models and stack them together

	# GBM Hyperparamters
	learn_rate_opt <- c(0.01, 0.03) 
	max_depth_opt <- c(3, 4, 5, 6, 9)
	sample_rate_opt <- c(0.7, 0.8, 0.9, 1.0)
	col_sample_rate_opt <- c(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
	hyper_params <- list(learn_rate = learn_rate_opt,
	                  max_depth = max_depth_opt, 
	                  sample_rate = sample_rate_opt,
	                  col_sample_rate = col_sample_rate_opt)

	search_criteria <- list(strategy = "RandomDiscrete", 
	                     max_models = 3,
	                     seed = 1)

	gbm_grid <- h2o.grid(algorithm = "gbm", 
	                  grid_id = "gbm_grid_binomial",
	                  x = x, 
	                  y = y,
	                  training_frame = train,
	                  ntrees = 10,
	                  seed = 1,
	                  nfolds = nfolds,
	                  fold_assignment = "Modulo",
	                  keep_cross_validation_predictions = TRUE,
	                  hyper_params = hyper_params,
	                  search_criteria = search_criteria)

	# Train a stacked ensemble using the GBM grid
	ensemble <- h2o.stackedEnsemble(x = x, 
	                             y = y, 
	                             training_frame = train,
	                             model_id = "ensemble_gbm_grid_binomial",
	                             selection_strategy = c("choose_all"), 
	                             base_models = gbm_grid@model_ids)

	# Eval ensemble performance on a test set
	perf <- h2o.performance(ensemble, newdata = test)

	# Compare to base learner performance on the test set
	.getauc <- function(mm) h2o.auc(h2o.performance(h2o.getModel(mm), newdata = test))
	baselearner_aucs <- sapply(gbm_grid@model_ids, .getauc)
	baselearner_best_auc_test <- max(baselearner_aucs)
	ensemble_auc_test <- h2o.auc(perf)
	print(sprintf("Best Base-learner Test AUC:  %s", baselearner_best_auc_test))
	print(sprintf("Ensemble Test AUC:  %s", ensemble_auc_test))

	# Generate predictions on a test set (if neccessary)
	pred <- h2o.predict(ensemble, newdata = test)

   .. code-block:: python

	import h2o
	h2o.init()
	from h2o.estimators.random_forest import H2ORandomForestEstimator
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
	
	# Specify the column types for your data, then inport the prostate dataset.

	col_types = ["numeric", "numeric", "numeric", "enum", "enum", "numeric", "numeric", "numeric", "numeric"]
	dat = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv", destination_frame="prostate_hex", col_types= col_types)
	
	# Split the data into training and testing 

	train, test = dat.split_frame(ratios=[.8], seed=1)

	# Generate a 2-model ensemble (GBM + RF)

	x = ["CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"]
	y = "AGE"
	folds = 5
	my_gbm = H2OGradientBoostingEstimator(nfolds=folds)
	my_gbm.train(x=x, y=y, training_frame=train)
	my_rf = H2ORandomForestEstimator(nfolds=folds)
	my_rf.train(x=x, y=y, training_frame=train)
	
	# Train a stacked ensemble using the GBM and RF models

	stack = H2OStackedEnsembleEstimator(model_id="my_ensemble_guassian", training_frame=train, validation_frame=test, base_models=[my_gbm.model_id,  my_rf.model_id], selection_strategy="choose_all")

	# Eval ensemble performance on the test data

	stack.train(x=x, y=y, training_frame=train, validation_frame=test)
	stack.model_performance()

Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- An `Ensemble slideset <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/ensembles-stacking/H2O_World_2015_Ensembles.pdf>`__ from H2O World 2015 provides a summary of Stacked Ensembles. 

- An `Ensemble Tutorial <http://learn.h2o.ai/content/tutorials/ensembles-stacking/index.html>`__ from H2O World 2015 provides information about the algorithm and the H2O implementation. 

- `Python Stacked Ensemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.

- `R Stacked Enemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.

References
~~~~~~~~~~

`David H. Wolpert. "Stacked Generalization." Neural Networks. Volume 5. (1992) <http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.56.1533>`__

`Leo Breiman. "Stacked Regressions." Machine Learning, 24, 49-64 (1996) <http://statistics.berkeley.edu/sites/default/files/tech-reports/367.pdf>`__ 

`Mark J van der Laan, Eric C Polley, and Alan E Hubbard. "Super Learner." Journal of the American
Statistical Applications in Genetics and Molecular Biology. Volume 6, Issue 1. (September 2007). <https://doi.org/10.2202/1544-6115.1309>`__

