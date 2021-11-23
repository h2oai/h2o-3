setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.grid.parallel <- function() {
  iris.hex <- h2o.importFile(path = locate("smalldata/iris/iris.csv"))

  ntrees_opts = c(1, 5)
  learn_rate_opts = c(0.1, 0.01)
  size_of_hyper_space = length(ntrees_opts) * length(learn_rate_opts)
  
  hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  baseline_grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters, parallelism = 0)
  expect_equal(length(baseline_grid@model_ids), length(ntrees_opts) * length(learn_rate_opts))
}

doTest("Parallel Grid Search test", test.grid.parallel)
