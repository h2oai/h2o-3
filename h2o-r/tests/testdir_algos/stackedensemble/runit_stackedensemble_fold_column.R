setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.fold_column.test <- function() {
  train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"),
                          destination_frame = "iris_train")
  y <- names(train[4])
  x <- names(train[1:3])

  # Train & Cross-validate a GBM
  my_gbm <- h2o.gbm(x = x,
                    y = y,
                    training_frame = train,
                    ntrees = 10,
                    fold_column = "species",
                    keep_cross_validation_predictions = TRUE,
                    seed = 1)

  # Train & Cross-validate a RF
  my_rf <- h2o.randomForest(x = x,
                            y = y,
                            training_frame = train,
                            ntrees = 10,
                            fold_column = "species",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)

  my_se <- h2o.stackedEnsemble(x = x,
                      y = y,
                      training_frame = train,
                      metalearner_fold_column = "species",
                      base_models = list(my_gbm, my_rf))

  train["species"] <- NULL
  w <- tryCatch({predict(my_se, train)}, warning = function (w) w)

  expect_false(is(w, "warning"))
}
doTest("Stacked Ensemble fold column Test", stackedensemble.fold_column.test)
