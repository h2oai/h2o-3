setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.ecology <- function() {
  expect_true(h2o.xgboost.available())
  
  n.trees <- 10
  max_depth <- 5
  min_rows <- 10
  learn_rate <- 0.7
  distribution <- "bernoulli"
  
  ecology.hex <- h2o.importFile(locate("smalldata/gbm_test/ecology_model.csv"))
  ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)

  #Train H2O XGBoost Model:
  ecology.h2o <- h2o.xgboost(x = 3:13, 
                             y = "Angaus", 
                             training_frame = ecology.hex,
                             ntrees = n.trees,
                             max_depth = max_depth,
                             min_rows = min_rows,
                             learn_rate = learn_rate,
                             distribution = distribution)
  model.params <- getParms(ecology.h2o)
  print(model.params)
  
  expect_equal(model.params$ntrees, n.trees)
  expect_equal(model.params$max_depth, max_depth)
  expect_equal(model.params$learn_rate, learn_rate)
  expect_equal(model.params$min_rows, min_rows)
  expect_equal(model.params$distribution, distribution)
}

doTest("XGBoost: Ecology Data", test.XGBoost.ecology)

