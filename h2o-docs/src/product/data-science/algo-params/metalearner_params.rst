``metalearner_params``
----------------------

- Available in: Stacked Ensembles
- Hyperparmeter: no

Description
~~~~~~~~~~~

Stacked Ensembles allows you to specify the algorithm to use when performing stacking. When ``metalearner_algorithm`` is configured, Stacked Ensembles runs with the specified algorithm's default values. The ``metalearner_params`` option allows you to pass in a dictionary/list of parameters to use for that algorithm instead of those defaults.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `metalearner_algorithm <metalearner_algorithm.html>`__


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

    # Train a stacked ensemble using GBM as the metalearner algorithm along with
    # a list of specified GBM parameters
    stack_gbm <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_algorithm = "gbm",
                                     metalearner_params = gbm_params)

    # Train a stacked ensemble using DRF as the metalearner algorithm along with
    # a list of specified DRF parameters
    stack_drf <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_nfolds = 3,
                                     metalearner_algorithm = "drf",
                                     metalearner_params = drf_params)

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

    # Add Metalearner params for gbm and drf
    gbm_params = {"ntrees" : 100, "max_depth" : 6}
    drf_params = {"ntrees" : 100, "max_depth" : 6}

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

    # Train a stacked ensemble using GBM as the metalearner algorithm along with
    # a list of specified GBM parameters
    stack_gbm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                            metalearner_algorithm="gbm",
                                            metalearner_params = gbm_params)
    stack_gbm.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble using DRF as the metalearner algorithm along with
    # a list of specified DRF parameters
    stack_drf = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                            metalearner_algorithm="drf", 
                                            metalearner_nfolds=3, 
                                            metalearner_params = drf_params)
    stack_drf.train(x=x, y=y, training_frame=train)
