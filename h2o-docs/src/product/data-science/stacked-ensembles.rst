Stacked Ensembles
-----------------

Introduction
~~~~~~~~~~~~

Ensemble machine learning methods use multiple learning algorithms to obtain better predictive performance than could be obtained from any of the constituent learning algorithms. Many of the popular modern machine learning algorithms are actually ensembles. For example, `Random Forest <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/drf.html>`__ and `Gradient Boosting Machine (GBM) <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/gbm.html>`__ are both ensemble learners.  Both bagging (e.g. Random Forest) and boosting (e.g. GBM) are methods for ensembling that take a collection of weak learners (e.g. decision tree) and form a single, strong learner.

H2O's Stacked Ensemble method is supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking.  This method currently supports regression and binary classification, and `multiclass support <https://0xdata.atlassian.net/browse/PUBDEV-3960>`__ is planned for a future release.

Native support for ensembles of H2O algorithms was added into core H2O in version 3.10.3.1.  A separate implementation, the **h2oEnsemble** R package, is also still `available <https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble>`__, however for new projects we recommend using the native H2O version, documented below.


Stacking / Super Learning
~~~~~~~~~~~~~~~~~~~~~~~~~

Stacking, also called Super Learning or Stacked Regression, is a class of algorithms that involves training a second-level "metalearner" to find the optimal combination of the base learners.  Unlike bagging and boosting, the goal in stacking is to ensemble strong, diverse sets of learners together. 

Although the concept of stacking was originally developed in 1992, the theoretical guarantees for stacking were not proven until the publication of a paper titled, `"Super Learner" <http://dx.doi.org/10.2202/1544-6115.1309>`__, in 2007.  In this paper, it was shown that the Super Learner ensemble represents an asymptotically optimal system for learning.  

There are some ensemble methods that are broadly labeled as stacking, however, the Super Learner ensemble is distinguished by the use of cross-validation to form what is called the "level-one" data, or the data that the metalearning or "combiner" algorithm is trained on.  More detail about the Super Learner algorithm is provided below.


Super Learner Algorithm
'''''''''''''''''''''''

The steps below describe the individual tasks involved in training and testing a Super Learner ensemble.  H2O automates most of the steps below so that you can quickly and easily build ensembles of H2O models.

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


Defining an H2O Stacked Ensemble Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__ Specify the dataset used to build the model. 

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the independent variable (response column). The data can be numeric or categorical.

-  **base_models**: Specify a list of model IDs that can be stacked together. Models must have been cross-validated using ``nfolds`` > 1, they all must use the same cross-validation folds, and ``keep_cross_validation_folds`` must be set to True. 

  **Notes regarding** ``base_models``: 

    - One way to guarantee identical folds across base models is to set ``fold_assignment = "Modulo"`` in all the base models.  It is also possible to get identical folds by setting ``fold_assignment = "Random"`` when the same seed is used in all base models.

    - In R, you can specify a list of models in the ``base_models`` parameter. 

-  **keep_levelone_frame**: Keep the level one data frame that's constructed for the metalearning step. This option is disabled by default.

Also in a `future release <https://0xdata.atlassian.net/browse/PUBDEV-3743>`__, there will be an additional **metalearner** parameter which allows for the user to specify the metalearning algorithm used.  Currently, the metalearner is fixed as a default H2O GLM with non-negative weights.

You can follow the progress of H2O's Stacked Ensemble development `here <https://0xdata.atlassian.net/issues/?filter=19301>`__.



Example
'''''''

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # Import a sample binary outcome train/test set into H2O
    train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(train), y)

    # For binary classification, response should be a factor
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])

    # Number of CV folds (to generate level-one data for stacking)
    nfolds <- 5

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
    from h2o.estimators.random_forest import H2ORandomForestEstimator
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
    from h2o.grid.grid_search import H2OGridSearch
    from __future__ import print_function
    h2o.init()

    # Import a sample binary outcome train/test set into H2O
    train = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Number of CV folds (to generate level-one data for stacking)
    nfolds = 5 

    # There are a few ways to assemble a list of models to stack together:
    # 1. Train individual models and put them in a list
    # 2. Train a grid of models
    # 3. Train several grids of models
    # Note: All base models must have the same cross-validation folds and 
    # the cross-validated predicted values must be kept.


    # 1. Generate a 2-model ensemble (GBM + RF)

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", 
                                          ntrees=10,
                                          max_depth=3, 
                                          min_rows=2, 
                                          learn_rate=0.2,
                                          nfolds=nfolds, 
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)


    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=50, 
                                     nfolds=nfolds, 
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True, 
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=train)


    # Train a stacked ensemble using the GBM and GLM above
    ensemble = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial",
                                           base_models=[my_gbm.model_id, my_rf.model_id])
    ensemble.train(x=x, y=y, training_frame=train)  

    # Eval ensemble performance on the test data
    perf_stack_test = ensemble.model_performance(test)
    
    # Compare to base learner performance on the test set
    perf_gbm_test = my_gbm.model_performance(test)
    perf_rf_test = my_rf.model_performance(test)
    baselearner_best_auc_test = max(perf_gbm_test.auc(), perf_rf_test.auc())
    stack_auc_test = perf_stack_test.auc()
    print("Best Base-learner Test AUC:  {0}".format(baselearner_best_auc_test))
    print("Ensemble Test AUC:  {0}".format(stack_auc_test))

    # Generate predictions on a test set (if neccessary)
    pred = ensemble.predict(test)
    
    
    # 2. Generate a random grid of models and stack them together

    # Specify GBM hyperparameters for the grid
    hyper_params = {"learn_rate": [0.01, 0.03],
                    "max_depth": [3, 4, 5, 6, 9],
                    "sample_rate": [0.7, 0.8, 0.9, 1.0],
                    "col_sample_rate": [0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]}
    search_criteria = {"strategy": "RandomDiscrete", "max_models": 3, "seed": 1}

    # Train the grid
    grid = H2OGridSearch(model=H2OGradientBoostingEstimator(ntrees=10, 
                                                            seed=1,
                                                            nfolds=nfolds, 
                                                            fold_assignment="Modulo",
                                                            keep_cross_validation_predictions=True),
                         hyper_params=hyper_params,
                         search_criteria=search_criteria,
                         grid_id="gbm_grid_binomial")
    grid.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble using the GBM grid
    ensemble = H2OStackedEnsembleEstimator(model_id="my_ensemble_gbm_grid_binomial", 
                                           base_models=grid.model_ids)
    ensemble.train(x=x, y=y, training_frame=train)

    # Eval ensemble performance on the test data
    perf_stack_test = ensemble.model_performance(test)

    # Compare to base learner performance on the test set
    baselearner_best_auc_test = max([h2o.get_model(model).model_performance(test_data=test).auc() for model in grid.model_ids])
    stack_auc_test = perf_stack_test.auc()
    print("Best Base-learner Test AUC:  {0}".format(baselearner_best_auc_test))
    print("Ensemble Test AUC:  {0}".format(stack_auc_test))

    # Generate predictions on a test set (if neccessary)
    pred = ensemble.predict(test)

FAQ
~~~

-  **How do I save ensemble models?**

  H2O now supports saving and loading ensemble models. (Refer to `PUBDEV-3970 <https://0xdata.atlassian.net/browse/PUBDEV-3970>`__ for more information.) The steps are the same as those described in the `Saving and Loading a Model <../save-and-load-model.html>`__ section. Note that MOJO support is planned for Stacked Ensemble models in a future release. (See `PUBDEV-3877 <https://0xdata.atlassian.net/browse/PUBDEV-3877>`__.)

-  **Will an stacked ensemble always perform better than a single model?**
  
  Hopefully, but it's not always the case.  That's why it always a good idea to check the performance of your stacked ensemble and compare it against the performance of the individual base learners.  


-  **How do I improve the performance of an ensemble?**
  
  If you find that your ensemble is not performing better than the best base learner, then you can try a few different things.  First, look to see if there are base learners that are performing much worse than the other base learners (for example, a GLM).  If so, remove them from the ensemble and try again.  Second, you can try adding more models to the ensemble, especially models that add diversity to your set of base models.  Once `custom metalearner support <https://0xdata.atlassian.net/browse/PUBDEV-3743>`__ is added, you can try out different metalearners as well.

-  **How does the algorithm handle missing values during training?**

  This is handled by the base algorithms of the ensemble.  See the documentation for those algorithms to find out more information.

-  **How does the algorithm handle missing values during testing?**

  This is handled by the base algorithms of the ensemble.  See the documentation for those algorithms to find out more information.

-  **What happens if the response has missing values?**

  No errors will occur, but nothing will be learned from rows containing missing values in the response column.

-  **What happens when you try to predict on a categorical level not seen during training?**

  This is handled by the base algorithms of the ensemble.  See the documentation for those algorithms to find out more information.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

  In the base learners, specify ``balance_classes``, ``class_sampling_factors`` and ``max_after_balance_size`` to control over/under-sampling.


Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- An `Ensemble slidedeck <https://github.com/h2oai/h2o-meetups/blob/master/2017_01_05_H2O_Ensemble_New_Developments/h2o_ensemble_new_developments_jan2017.pdf>`__ from January 2017 provides a summary of the new Stacked Ensemble method in H2O, along with a comparison to the pre-existing **h2oEnsemble** R package. 

- `Python Stacked Ensemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.

- `R Stacked Enemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.


References
~~~~~~~~~~

`David H. Wolpert. "Stacked Generalization." Neural Networks. Volume 5. (1992) <http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.56.1533>`__

`Leo Breiman. "Stacked Regressions." Machine Learning, 24, 49-64 (1996) <http://statistics.berkeley.edu/sites/default/files/tech-reports/367.pdf>`__ 

`Mark J van der Laan, Eric C Polley, and Alan E Hubbard. "Super Learner." Journal of the American
Statistical Applications in Genetics and Molecular Biology. Volume 6, Issue 1. (September 2007). <https://doi.org/10.2202/1544-6115.1309>`__

`LeDell, E. "Scalable Ensemble Learning and Computationally Efficient Variance Estimation" (Doctoral Dissertation). University of California, Berkeley, USA. (2015) <http://www.stat.berkeley.edu/~ledell/papers/ledell-phd-thesis.pdf>`__


