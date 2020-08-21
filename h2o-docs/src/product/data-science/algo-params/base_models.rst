``base_models``
----------------

- Available in: Stacked Ensembles
- Hyperparameter: no

Description
~~~~~~~~~~~

H2O's Stacked Ensemble method is a supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking (or Super Learning). The algorithm that learns the optimal combination of the base learners is called the metalearning algorithm or metalearner. 

The ``base_models`` parameter is used to specify a list of models (or model IDs) that can be stacked together. Models must have been cross-validated (i.e., ``nfolds``>1 or ``fold_column`` was specified), they all must use the same cross-validation folds, and ``keep_cross_validation_predictions`` must have been set to ``True``. One way to guarantee identical folds across base models is to set ``fold_assignment = "Modulo"`` in all the base models. It is also possible to get identical folds by setting ``fold_assignment = "Random"`` when the same seed is used in all base models.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

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

        # Train a stacked ensemble using the default metalearner algorithm
        stack <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf))
        h2o.auc(h2o.performance(stack, test))
        # 0.7570171

        # Train a stacked ensemble using GBM as the metalearner algorithm
        # The metalearner will use GBM default values
        stack_gbm <- h2o.stackedEnsemble(x = x,
                                         y = y,
                                         training_frame = train,
                                         base_models = list(my_gbm, my_rf),
                                         metalearner_algorithm = "gbm")
        h2o.auc(h2o.performance(stack_gbm, test))
        # 0.7511055                                 

        # Train a stacked ensemble using RF as the metalearner algorithm
        # The metelearner will use RF default values
        stack_rf <- h2o.stackedEnsemble(x = x,
                                        y = y,
                                        training_frame = train,
                                        base_models = list(my_gbm, my_rf),
                                        metalearner_algorithm = "drf")
        h2o.auc(h2o.performance(stack_rf, test))
        # 0.7232461

        # Train a stacked ensemble using Deep Learning as the metalearner algorithm
        # The metelearner will use RF default values
        stack_dl <- h2o.stackedEnsemble(x = x,
                                        y = y,
                                        training_frame = train,
                                        base_models = list(my_gbm, my_rf),
                                        metalearner_algorithm = "deeplearning")
        h2o.auc(h2o.performance(stack_dl, test))
        # 0.7571556                          


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

        # Train a stacked ensemble using the default metalearner algorithm
        stack = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf])
        stack.train(x=x, y=y, training_frame=train)
        stack.model_performance(test).auc()
        # 0.7522591310013634

        # Train a stacked ensemble with a GBM metalearner algorithm
        # The metelearner will use GBM default values
        stack_gbm = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                                metalearner_algorithm="gbm")
        stack_gbm.train(x=x, y=y, training_frame=train)
        stack_gbm.model_performance(test).auc()
        # 0.7522591310013634

        # Train a stacked ensemble with a RF metalearner algorithm
        # The metelearner will use RF default values
        stack_rf = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                               metalearner_algorithm="drf")
        stack_rf.train(x=x, y=y, training_frame=train)
        stack_rf.model_performance(test).auc()
        # 0.7016302070136065

        # Train a stacked ensemble with a Deep Learning metalearner algorithm
        # The metelearner will use Deep Learning default values
        stack_dl = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], 
                                               metalearner_algorithm="deeplearning")
        stack_dl.train(x=x, y=y, training_frame=train)
        stack_dl.model_performance(test).auc()
        # 0.7634122856763638
