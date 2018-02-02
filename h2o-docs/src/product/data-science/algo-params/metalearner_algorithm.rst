``metalearner_algorithm``
-------------------------

- Available in: Stacked Ensembles
- Hyperparameter: no

Description
~~~~~~~~~~~

H2O's Stacked Ensemble method is supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking. By default, the Stacked Ensemble metalearner is a default H2O GLM with non-negative weights. The ``metalearner_algorithm`` option allows you to specify a different metalearner algorithm.  Options include:

 - ``"AUTO"`` (GLM with non negative weights, and if ``validation_frame`` is present, ``lambda_search`` is set to True; may change over time). This is the default.
 - ``"glm"`` (GLM with default parameters)
 - ``"gbm"`` (GBM with default parameters) 
 - ``"drf"`` (Random Forest with default parameters)
 - ``"deeplearning"`` (Deep Learning with default parameters).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `metalearner_params <metalearner_params.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # import the higgs_train_5k train and test datasets
    train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
    test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_test_5k.csv")


    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(train), y)

    # Convert the response column in train and test datasets to a factor    
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])

    # Set number of folds for base learners   
    nfolds <- 3  

    # Add metalearner parameters for gbm and drf
    gbm_params <- list(ntrees=100, max_depth = 6)
    drf_params <- list(ntrees=100, max_depth = 6)

    # Train & Cross-validate a GBM model
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "bernoulli",
                      ntrees = 10,
                      nfolds = nfolds,
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)

    # Train & Cross-validate an RF model
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 10,
                              nfolds = nfolds,
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)

    # Train a stacked ensemble using GBM as the metalearner algorithm
    # The metalearner will use GBM default values
    stack_gbm <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_algorithm = "gbm")

    # Train a stacked ensemble using DRF as the metalearner algorithm
    # The metelearner will use DRF default values
    stack_drf <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_nfolds = 3,
                                     metalearner_algorithm = "drf")

   .. code-block:: python

    import(h2o)
    h2o.init()
    from h2o.estimators.random_forest import H2ORandomForestEstimator
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator

    # import the higgs_train_5k train and test datasets
    train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_test_5k.csv")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Convert the response column in train and test datasets to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds for base learners
    nfolds = 3

    # Train and cross-validate a GBM model
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)

    # Train and cross-validate an RF model
    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble with a GBM metalearner algorithm
    # The metelearner will use GBM default values
    stack_gbm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                            metalearner_algorithm="gbm",
    stack_gbm.train(x=x, y=y, training_frame=train)


    # Train a stacked ensemble with a DRF metalearner algorithm
    # The metelearner will use DRF default values
    stack_drf = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                            metalearner_algorithm="drf", 
                                            metalearner_nfolds=3, 
    stack_drf.train(x=x, y=y, training_frame=train)
