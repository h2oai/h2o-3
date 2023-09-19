``metalearner_params``
----------------------

- Available in: Stacked Ensembles
- Hyperparmeter: no

Description
~~~~~~~~~~~

Stacked Ensemble allows you to specify the metalearning algorithm to use when training the ensemble. When ``metalearner_algorithm`` is set to a non-default value (e.g. "GBM"), Stacked Ensemble runs with the specified algorithm's default hyperparameter values.  The ``metalearner_params`` option allows you to pass in a dictionary/list of hyperparameters to use for that algorithm instead of the defaults.

The default parameters for the metalearning algorithms may not be the best choice, so it's a good idea to experiment a bit with different parameters using ``metalearner_params``.  In the `next release <https://github.com/h2oai/h2o-3/issues/12153>`__ of H2O, there will be an option to easily do a grid search over metalearner parameters using the standard H2O Grid interface, which will make tuning the metalearner a bit easier.

Note: The ``seed`` argument in Stacked Ensemble is passed through to the metalearner automatically.  If you define ``seed`` in ``metalearner_params``, it will use that value instead of value defined by the ``seed`` argument.  If the only parameter that you want to customze for the metalearner is ``seed``, then it's simpler to just use top-level argument instead.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `metalearner_algorithm <metalearner_algorithm.html>`__


Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # import the higgs_train_5k train and test datasets
        train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
        test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_test_5k.csv")

        # Identify predictors and response
        y <- "response"
        x <- setdiff(names(train), y)

        # Convert the response column in train and test datasets to a factor    
        train[, y] <- as.factor(train[, y])
        test[, y] <- as.factor(test[, y])

        # Set number of folds for base learners   
        nfolds <- 3  


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


        # Next we can train a few different ensembles using different metalearners

        # Add metalearner parameters for GBM and RF
        gbm_params <- list(ntrees = 100, max_depth = 2)
        rf_params <- list(ntrees = 500, max_depth = 8)                          

        # Train a stacked ensemble using GBM as the metalearner algorithm along with
        # a list of specified GBM parameters
        stack_gbm <- h2o.stackedEnsemble(x = x,
                                         y = y,
                                         training_frame = train,
                                         base_models = list(my_gbm, my_rf),
                                         metalearner_algorithm = "gbm",
                                         metalearner_params = gbm_params)
        h2o.auc(h2o.performance(stack_gbm, test))
        # 0.7563162
                                    

        # Train a stacked ensemble using DRF as the metalearner algorithm along with
        # a list of specified DRF parameters
        stack_rf <- h2o.stackedEnsemble(x = x,
                                        y = y,
                                        training_frame = train,
                                        base_models = list(my_gbm, my_rf),
                                        metalearner_algorithm = "drf",
                                        metalearner_params = rf_params)
        h2o.auc(h2o.performance(stack_rf, test))
        # 0.7498578
                                

   .. code-tab:: python

        import h2o
        from h2o.estimators.random_forest import H2ORandomForestEstimator
        from h2o.estimators.gbm import H2OGradientBoostingEstimator
        from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
        h2o.init()

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


        # Next we can train a few different ensembles using different metalearners

        # Add custom metalearner params for GBM and RF
        gbm_params = {"ntrees": 100, "max_depth": 3}
        rf_params = {"ntrees": 500, "max_depth": 8}

        # Train a stacked ensemble using GBM as the metalearner algorithm along with
        # a list of specified GBM parameters
        stack_gbm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                                metalearner_algorithm="gbm",
                                                metalearner_params=gbm_params)
        stack_gbm.train(x=x, y=y, training_frame=train)
        stack_gbm.model_performance(test).auc()
        # 0.7576578946309993


        # Train a stacked ensemble using RF as the metalearner algorithm along with
        # a list of specified RF parameters
        stack_rf = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                               metalearner_algorithm="drf", 
                                               metalearner_params=rf_params)
        stack_rf.train(x=x, y=y, training_frame=train)
        stack_rf.model_performance(test).auc()
        # 0.7525306981028109

