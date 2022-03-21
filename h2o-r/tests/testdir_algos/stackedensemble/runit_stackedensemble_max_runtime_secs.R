setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.max_runtime_secs.test <- function() {
    max_runtime_secs <- 4
    train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"))
    y <- "species"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    nfolds <- 5

    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "multinomial",
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
    stack <- h2o.stackedEnsemble(x = x,
                                 y = y,
                                 training_frame = train,
                                 model_id = "my_ensemble_l1",
                                 base_models = list(my_gbm@model_id, my_rf@model_id),
                                 max_runtime_secs = max_runtime_secs)

    # metalearner has the set max_runtine_secs
    expect_lte(stack@model$metalearner_model@parameters$max_runtime_secs, max_runtime_secs)

    # stack ensemble has the set max_runtime_secs
    expect_equal(stack@parameters$max_runtime_secs, max_runtime_secs)
}


stackedensemble.max_runtime_secs_is_obeyed.test <- function() {
    max_runtime_secs <- 1
    train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"))
    y <- "species"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    nfolds <- 5

    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "multinomial",
                      ntrees = 10,
                      max_depth = 3,
                      min_rows = 2,
                      learn_rate = 0.2,
                      nfolds = nfolds,
                      fold_assignment = "Modulo",
                      seed = 1)


    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 50,
                              nfolds = nfolds,
                              fold_assignment = "Modulo",
                              seed = 1)

    blending_frame <- train
    for (i in seq_len(15)){
        blending_frame <- h2o.rbind(blending_frame, blending_frame)
    }
    # Train a stacked ensemble using the GBM and RF above
    expect_error(h2o.stackedEnsemble(x = x,
                                 y = y,
                                 training_frame = train,
                                 model_id = "my_ensemble_l1",
                                 base_models = list(my_gbm@model_id, my_rf@model_id),
                                 max_runtime_secs = max_runtime_secs,
                                 blending_frame = blending_frame
                                ))
}

doSuite("Stacked Ensemble max_runtime_secs Test", makeSuite(
  stackedensemble.max_runtime_secs.test,
  stackedensemble.max_runtime_secs_is_obeyed.test,
))
