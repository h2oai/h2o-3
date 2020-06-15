setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.weights_column.test <- function() {


  train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"),
                          destination_frame = "iris_train")
  y <- "species"
  x <- setdiff(names(train), y)
  train["weights"] <- 1
  nfolds <- 3  #number of folds for base learners

  # Train & Cross-validate a GBM
  my_gbm <- h2o.gbm(x = x,
                    y = y,
                    training_frame = train,
                    weights_column = "weights",
                    ntrees = 10,
                    nfolds = nfolds,
                    keep_cross_validation_predictions = TRUE,
                    seed = 1)

  # Train & Cross-validate a RF
  my_rf <- h2o.randomForest(x = x,
                            y = y,
                            training_frame = train,
                            weights_column = "weights",
                            ntrees = 10,
                            nfolds = nfolds,
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)
  warns <- list()
  w <- tryCatch({h2o.stackedEnsemble(x = x,
                                    y = y,
                                    training_frame = train,
                                    base_models = list(my_gbm, my_rf))},
                warning = function (w) warns <<- w$message)

  expect(any(grepl("weights_column=\"weights\"", warns, fixed = TRUE)),
         "SE fails to warn about not having set weights_column when all the base models have the same one")

  # weights_column is propagated to metalearner
  se2 <- h2o.stackedEnsemble(x = x,
                             y = y,
                             training_frame = train,
                             base_models = list(my_gbm, my_rf),
                             weights_column = "weights")
  weights_column <- se2@model$metalearner_model@allparameters$weights_column$column_name
  expect(!is.null(weights_column) && weights_column == "weights",
         "SE fails to propagate weights_column to metalearner")
}
doTest("Stacked Ensemble weights_column Test", stackedensemble.weights_column.test)
