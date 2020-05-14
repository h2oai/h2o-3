setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.varimp.test <- function() {
  col_types <- c("Numeric","Numeric","Numeric","Enum","Enum","Numeric","Numeric","Numeric","Numeric")
  dat <- h2o.uploadFile(locate("smalldata/extdata/prostate.csv"),
                        destination_frame = "prostate.hex",
                        col.types = col_types)
  ss <- h2o.splitFrame(dat, ratios = 0.8, seed = 1)
  train <- ss[[1]]
  test <- ss[[2]]
  x <- c("CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL")
  y <- "AGE"
  nfolds <- 5

  my_rf <- h2o.randomForest(x = x,
                            y = y,
                            training_frame = train,
                            ntrees = 10,
                            nfolds = nfolds,
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)

  my_xrf <- h2o.randomForest(x = x,
                             y = y,
                             training_frame = train,
                             ntrees = 10,
                             histogram_type = "Random",
                             nfolds = nfolds,
                             fold_assignment = "Modulo",
                             keep_cross_validation_predictions = TRUE,
                             seed = 1)

  stack <- h2o.stackedEnsemble(x = x,
                               y = y,
                               training_frame = train,
                               validation_frame = test,  #also test that validation_frame is working
                               model_id = "my_ensemble_gaussian",
                               base_models = list(my_rf, my_xrf))

  w = tryCatch(h2o.varimp(stack), warning=function(w) return(w))
  expect_equal(w$message, "This model doesn't have variable importances")

  w2 = tryCatch(h2o.varimp_plot(stack), warning=function(w) return(w))
  expect_equal(w2$message, "This model doesn't have variable importances")
}

doTest("Stacked Ensemble warns about unimplemented variable importance", stackedensemble.varimp.test)
