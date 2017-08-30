setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.levelone.test <- function() {

    train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"))
    y <- "species"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    nfolds <- 5
    num_base_models <- 2
    num_col_level_one_frame <- nrow(h2o.unique(train[y])) * num_base_models + 1 #Predicting 3 classes across two base models + response (3*2+1)

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
                                 keep_levelone_frame = TRUE)
    level_one_frame = h2o.getFrame(stack@model$levelone_frame_id$name)
    expect_equal(ncol(level_one_frame), num_col_level_one_frame) #Predicting 3 classes across two base models + response (3*2+1)
    expect_equal(nrow(level_one_frame),nrow(train)) #Should predict for nrow of training frame

    # Train a stacked ensemble using the GBM and RF above
    stack2 <- h2o.stackedEnsemble(x = x,
                                  y = y,
                                  training_frame = train,
                                  model_id = "my_ensemble_no_l1",
                                  base_models = list(my_gbm@model_id, my_rf@model_id))
    expect_equal(is.null(stack2@model$levelone_frame_id), TRUE) #fetching level one frame is off by default

}

doTest("Stacked Ensemble Level-One Frame Test", stackedensemble.levelone.test)
