``blending_frame``
------------------

- Available in: Stacked Ensembles, AutoML
- Hyperparameter: no

Description
~~~~~~~~~~~

H2O's Stacked Ensemble method is supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking (or Super Learning). The algorithm that learns the optimal combination of the base learners is called the metalearning algorithm or metalearner. 

The optional ``blending_frame`` parameter is used to specify a frame to be used for computing the predictions that serve as the training frame for the metalearner. If provided, this triggers blending mode. Blending mode is faster than cross-validating the base learners (though these ensembles may not perform as well as the Super Learner ensemble). In addition, a blending frame adds the ability to train stacked ensembles on time-series data, where holdout data is "future" data compared to "past" data in training set.

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
        higgs <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")

        # split the dataset into training and blending frames
        higgs_splits <- h2o.splitFrame(data =  higgs, ratios = 0.8, seed = 1234)
        train <- higgs_splits[[1]]
        blend <- higgs_splits[[2]]

        # Identify predictors and response
        y <- "response"
        x <- setdiff(names(train), y)

        # Convert the response column in train and test datasets to a factor    
        train[, y] <- as.factor(train[, y])
        blend[, y] <- as.factor(blend[, y])

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

        # Train a stacked ensemble using a blending frame
        stack <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     base_models = list(my_gbm, my_rf),
                                     training_frame = train,
                                     blending_frame = blend,
                                     seed = 1)
        h2o.auc(h2o.performance(stack, blend))
        # [1] 0.7576039

   .. code-tab:: python

        import h2o
        from h2o.estimators.random_forest import H2ORandomForestEstimator
        from h2o.estimators.gbm import H2OGradientBoostingEstimator
        from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
        h2o.init()

        # import the higgs_train_5k train and test datasets
        higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")

        # split the dataset into training and blending
        train, blend = higgs.split_frame(ratios = [.8], seed = 1234)

        # Identify predictors and response
        x = train.columns
        y = "response"
        x.remove(y)

        # Convert the response column in train and test datasets to a factor
        train[y] = train[y].asfactor()
        blend[y] = blend[y].asfactor()


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

        # Train a stacked ensemble using a blending frame
        stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
                                                  seed=1)
        stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
        stack_blend.model_performance(blend).auc()
        # 0.7736312597328088
