Stacked Ensembles
-----------------

Introduction
~~~~~~~~~~~~

Ensemble machine learning methods use multiple learning algorithms to obtain better predictive performance than could be obtained from any of the constituent learning algorithms. Many of the popular modern machine learning algorithms are actually ensembles. For example, `Random Forest <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/drf.html>`__ and `Gradient Boosting Machine (GBM) <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/gbm.html>`__ are both ensemble learners.  Both bagging (e.g. Random Forest) and boosting (e.g. GBM) are methods for ensembling that take a collection of weak learners (e.g. decision tree) and form a single, strong learner.

H2O's Stacked Ensemble method is supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking.  Like all supervised models in H2O, Stacked Enemseble supports regression, binary classification and multiclass classification.

Native support for ensembles of H2O algorithms was added into core H2O in version 3.10.3.1.  A separate implementation, the **h2oEnsemble** R package, is also still `available <https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble>`__, however for new projects we recommend using the native H2O version, documented below.


Stacking / Super Learning
~~~~~~~~~~~~~~~~~~~~~~~~~

Stacking, also called Super Learning [3_] or Stacked Regression [2_], is a class of algorithms that involves training a second-level "metalearner" to find the optimal combination of the base learners.  Unlike bagging and boosting, the goal in stacking is to ensemble strong, diverse sets of learners together. 

Although the concept of stacking was originally developed in 1992 [1_], the theoretical guarantees for stacking were not proven until the publication of a paper titled, `"Super Learner" <https://doi.org/10.2202/1544-6115.1309>`__, in 2007 [3_].  In this paper, it was shown that the Super Learner ensemble represents an asymptotically optimal system for learning.  

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



Training Base Models for the Ensemble
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Before training a stacked ensemble, you will need to train and cross-validate a set of "base models" which will make up the ensemble.  In order to stack these models toegther, a few things are required:

- The models must be cross-validated using the same number of folds (e.g. ``nfolds = 5`` or use the same ``fold_column`` across base learners).

- The cross-validated predictions from all of the models must be preserved by setting ``keep_cross_validation_predictions`` to True.  This is the data which is used to train the metalearner, or "combiner", algorithm in the ensemble. 

- You can train these models manually, or you can use a group of models from a grid search.

- The models must be trained on the same ``training_frame``.  The rows must be identical, but you can use different sets of predictor columns, ``x``, across models if you choose.  Using base models trained on different subsets of the feature space will add more randomness/diversity to the set of base models, which in theory can improve ensemble performance.  However, using all available predictor columns for each base model will often still yield the best results (the more data, the better the models).  


Defining a Stacked Ensemble Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `y <algo-params/y.html>`__: (Required) Specify the index or column name of the column to use as the dependent variable (response column). The response column can be numeric (regression) or categorical (classification).  

-  `x <algo-params/x.html>`__: (Optional) Specify a vector containing the names or indices of the predictor variables to use when building the model.   If ``x`` is missing, then all columns except ``y`` are used.  The only use for ``x`` is to get the correct training set so that we can compute ensemble training metrics.

-  `training_frame <algo-params/training_frame.html>`__ (Required) Specify the dataset used to build the model.  In a Stacked Ensemble model, the training frame is used only to retreive the response column (needed for training the metalearner) and also to compute training metrics for the ensemble model.  

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset to use for tuning the model.  The validation frame will be passed through to the metalearner for tuning.

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  **base_models**: (Required) Specify a list of models (or model IDs) that can be stacked together.  Models must have been cross-validated (i.e. ``nfolds``>1 or ``fold_column`` was specified), they all must use the same cross-validation folds, and ``keep_cross_validation_predictions`` must have been set to True. One way to guarantee identical folds across base models is to set ``fold_assignment = "Modulo"`` in all the base models.  It is also possible to get identical folds by setting ``fold_assignment = "Random"`` when the same seed is used in all base models.

-  `metalearner_algorithm <algo-params/metalearner_algorithm.html>`__ (Optional) Specify the metalearner algorithm type.  Options include:

 - ``"AUTO"`` (GLM with non negative weights, and if ``validation_frame`` is present, then ``lambda_search`` is set to True; may change over time). This is the default.
 - ``"glm"`` (GLM with default parameters)
 - ``"gbm"`` (GBM with default parameters) 
 - ``"drf"`` (Random Forest with default parameters)
 - ``"deeplearning"`` (Deep Learning with default parameters).

-  `metalearner_params <algo-params/metalearner_params.html>`__: (Optional) If a ``metalearner_algorithm`` is specified, then you can also specify a list of customized parameters for that algorithm (for example, a GBM with ``ntrees=100``, ``max_depth=10``, etc.)

-  `metalearner_nfolds <algo-params/nfolds.html>`__: (Optional) Specify the number of folds for cross-validation of the metalearning algorithm.  Defaults to 0 (no cross-validation).  If you want to compare the cross-validated performance of the ensemble model to the cross-validated performance of the base learners or other algorithms, you should make use of this option.

-  `metalearner_fold_assignment <algo-params/fold_assignment.html>`__: (Optional; Applicable only if a value for ``metalearner_nfolds`` is specified) Specify the cross-validation fold assignment scheme for the metalearner. The available options are AUTO (which is Random), Random, Modulo, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `metalearner_fold_column <algo-params/fold_column.html>`__: (Optional; Cannot be used at the same time as ``nfolds``) Specify the name of the column that contains the cross-validation fold assignment per observation for cross-validation of the metalearner.  The column can be numeric (e.g. fold index or other integer value) or it can be categorical.  The number of folds is equal to the number of unique values in this column.

-  **keep_levelone_frame**: (Optional) Keep the level one data frame that's constructed for the metalearning step. Defaults to False.

-  `seed <algo-params/seed.html>`__: (Optional) Seed for random numbers; passed through to the metalearner algorithm. Defaults to -1 (time-based random number).


Also in a `future release <https://0xdata.atlassian.net/browse/PUBDEV-5086>`__, there will be an additional ``metalearner_params`` argument which allows for full customization of the metalearner algorithm hyperparamters.  

You can follow the progress of H2O's Stacked Ensemble development `here <https://0xdata.atlassian.net/issues/?filter=19301>`__.



Example
~~~~~~~

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
                                    base_models = list(my_gbm, my_rf))

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
                                           base_models=[my_gbm, my_rf])
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

   .. code-block:: Scala

    import org.apache.spark.h2o._
    import water.Key
    import java.io.File

    val h2oContext = H2OContext.getOrCreate(sc)
    import h2oContext._
    import h2oContext.implicits._

    // Import data from the local file system as an H2O DataFrame
    val prostateData = new H2OFrame(new File("/Users/jsmith/src/github.com/h2oai/sparkling-water/examples/smalldata/prostate.csv"))

    // Build a Deep Learning model
    import _root_.hex.deeplearning.DeepLearning
    import _root_.hex.deeplearning.DeepLearningModel.DeepLearningParameters
    val dlParams = new DeepLearningParameters()
    dlParams._epochs = 100
    dlParams._train = prostateData
    dlParams._response_column = 'CAPSULE
    dlParams._variable_importances = true
    dlParams._nfolds = 5
    dlParams._seed = 1111
    dlParams._keep_cross_validation_predictions = true;
    val dl = new DeepLearning(dlParams, Key.make("dlProstateModel.hex"))
    val dlModel = dl.trainModel.get

    // Build a GBM model
    import _root_.hex.tree.gbm.GBM
    import _root_.hex.tree.gbm.GBMModel.GBMParameters
    val gbmParams = new GBMParameters()
    gbmParams._train = prostateData
    gbmParams._response_column = 'CAPSULE
    gbmParams._nfolds = 5
    gbmParams._seed = 1111
    gbmParams._keep_cross_validation_predictions = true;
    val gbm = new GBM(gbmParams,Key.make("gbmRegModel.hex"))
    val gbmModel = gbm.trainModel().get()

    // Import required classes for Stacked Ensembles
    import _root_.hex.Model
    import _root_.hex.StackedEnsembleModel
    import _root_.hex.ensemble.StackedEnsemble

    // Define Stacked Ensemble parameters
    val stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters()
    stackedEnsembleParameters._train = prostateData._key
    stackedEnsembleParameters._response_column = 'CAPSULE

    // Pass in the keys for the GBM and Deep Learning using one of the following options
    // Option 1
    stackedEnsembleParameters._base_models = Array(gbmRegModel._key.asInstanceOf[T_MODEL_KEY], dlModel._key.asInstanceOf[T_MODEL_KEY])
    // Option 2
    stackedEnsembleParameters._base_models = Array(gbmRegModel, dlModel).map(model => model._key.asInstanceOf[T_MODEL_KEY])

    // Define the Stacked Ensemble job
    val stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters)

    // Build the Stacked Ensemble model
    val stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

    // Review the Stacked Ensemble model
    stackedEnsembleModel

    // Review the parameters (meta learner) from the Stacked Ensemble model
    stackedEnsembleModel._output._metalearner

FAQ
~~~

-  **How do I save ensemble models?**

  H2O now supports saving and loading ensemble models. The steps are the same as those described in the `Saving and Loading a Model <../save-and-load-model.html>`__ section.  For productionizing Stacked Ensemble models, we recommend using `MOJOs <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/productionizing.html>`__.

-  **Will an stacked ensemble always perform better than a single model?**
  
  Hopefully, but it's not always the case (especially if you have very small data).  That's why it always a good idea to check the performance of your stacked ensemble and compare it against the performance of the individual base learners.  

-  **How do I improve the performance of an ensemble?**
  
  If you find that your ensemble is not performing better than the best base learner, then you can try a few different things.  First make sure to try the default metalearner ("AUTO") and then try the other options for ``metalearner_algorithm``.  Once fully customized `metalearner support <https://0xdata.atlassian.net/browse/PUBDEV-5086>`__ is added, you can try out different hyperparamters for the metalearner algorithm as well.  

  Second, look to see if there are base learners that are performing much worse than the other base learners (for example, a GLM).  If so, remove them from the ensemble and try again.  

  You can also try adding more models to the ensemble, especially models that add diversity to your set of base models.  Training a random grid of models (or multiple random grids, one for each algorithm type) is a good way to generate a diverse set of base learners. 

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

- An `Ensemble slidedeck <https://github.com/h2oai/h2o-meetups/blob/master/2017_01_05_H2O_Ensemble_New_Developments/h2o_ensemble_new_developments_jan2017.pdf>`__ from January 2017 provides a summary of the new Stacked Ensemble method in H2O, along with a comparison to the pre-existing `h2oEnsemble R package <https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble>`__. 

- `Python Stacked Ensemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.

- `R Stacked Enemble tests <https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/stackedensemble>`__ are available in the H2O-3 GitHub repository.


References
~~~~~~~~~~

.. _1:

[1] `David H. Wolpert. "Stacked Generalization." Neural Networks. Volume 5. (1992) <http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.56.1533>`__

.. _2:

[2] `Leo Breiman. "Stacked Regressions." Machine Learning, 24, 49-64 (1996) <http://statistics.berkeley.edu/sites/default/files/tech-reports/367.pdf>`__ 

.. _3:

[3] `Mark J van der Laan, Eric C Polley, and Alan E Hubbard. "Super Learner." Journal of the American
Statistical Applications in Genetics and Molecular Biology. Volume 6, Issue 1. (September 2007). <https://doi.org/10.2202/1544-6115.1309>`__

.. _4:

[4] `LeDell, E. "Scalable Ensemble Learning and Computationally Efficient Variance Estimation" (Doctoral Dissertation). University of California, Berkeley, USA. (2015) <http://www.stat.berkeley.edu/~ledell/papers/ledell-phd-thesis.pdf>`__



