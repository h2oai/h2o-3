``metalearner_transform``
-------------------------

- Available in: Stacked Ensembles
- Hyperparameter: no

Description
~~~~~~~~~~~

H2O's Stacked Ensemble method is a supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking (or Super Learning). The algorithm that learns the optimal combination of the base learners is called the metalearning algorithm or metalearner. 

By default, the Stacked Ensemble metalearner uses base model predictions as an input for the metalearner. This option allows you to specify the transformation used on predictions from the base models in order to make a level one frame which is the input frame for the metalearner.

Options include:

 - ``"NONE"`` (no transform applied)
 - ``"Logit"`` (applicable only to classification tasks; use logit transformation on the predicted probabilities by the base models)


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
                          nfolds = nfolds,
                          keep_cross_validation_predictions = TRUE,
                          seed = 1)

        # Train & Cross-validate an RF model
        my_rf <- h2o.randomForest(x = x,
                                  y = y,
                                  training_frame = train,
                                  nfolds = nfolds,
                                  keep_cross_validation_predictions = TRUE,
                                  seed = 1)


        # Train a stacked ensemble using the default metalearner transform - None
        stack <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_transform = "NONE")
        h2o.auc(h2o.performance(stack, test))
        # 0.777275

        # Train a stacked ensemble using the logit metalearner transform
        stack_logit <- h2o.stackedEnsemble(x = x,
                                           y = y,
                                           training_frame = train,
                                           base_models = list(my_gbm, my_rf),
                                           metalearner_transform = "Logit")
        h2o.auc(h2o.performance(stack_logit, test))
        # 0.7775728

 

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
                                              nfolds=nfolds,
                                              fold_assignment="Modulo",
                                              keep_cross_validation_predictions=True,
                                              seed=1)
        my_gbm.train(x=x, y=y, training_frame=train)

        # Train and cross-validate an RF model
        my_rf = H2ORandomForestEstimator(nfolds=nfolds,
                                         fold_assignment="Modulo",
                                         keep_cross_validation_predictions=True,
                                         seed=1)
        my_rf.train(x=x, y=y, training_frame=train)


        # Train a stacked ensemble using the default metalearner transform - NONE
        stack = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_transform="NONE")
        stack.train(x=x, y=y, training_frame=train)
        stack.model_performance(test).auc()
        # 0.7783405930877485

        # Train a stacked ensemble using the logit metalearner transform
        stack_logit = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_transform="Logit")
        stack_logit.train(x=x, y=y, training_frame=train)
        stack_logit.model_performance(test).auc()
        # 0.7784964063210138
