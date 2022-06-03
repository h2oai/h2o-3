setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.grid.sequential <- function() {
  iris.hex <- h2o.importFile(path = locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")

  ntrees_opts <- 1:10
  learn_rate_opts <- 10**(-(1:10))
  size_of_hyper_space <- length(ntrees_opts)

  hyper_parameters <- list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  grid1 <- h2o.grid("gbm",
                    x=1:4, y=5,
                    training_frame=iris.hex,
                    hyper_params = hyper_parameters,
                    seed=1,
                    search_criteria = list(strategy = "Sequential", early_stopping = FALSE, stopping_tolerance=1e5, stopping_rounds=2))
  expect_equal(length(grid1@model_ids), size_of_hyper_space)

  grid2 <- h2o.grid("gbm",
                    x=1:4, y=5,
                    training_frame=iris.hex,
                    hyper_params = hyper_parameters,
                    seed=1,
                    search_criteria = list(strategy = "Sequential", early_stopping = TRUE, stopping_tolerance=1e5, stopping_rounds=2))
  expect_equal(length(grid2@model_ids), 5)

  grid3 <- h2o.grid("gbm",
                    x=1:4, y=5,
                    training_frame=iris.hex,
                    hyper_params = hyper_parameters,
                    seed=1,
                    search_criteria = list(strategy = "Sequential", early_stopping = FALSE, max_models = 3))
  expect_equal(length(grid3@model_ids), 3)

  ntrees_opts <- 1:10000
  learn_rate_opts <- 10**(-(1:10000))
  hyper_parameters <- list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  grid4 <- h2o.grid("gbm",
                    x=1:4, y=5,
                    training_frame=iris.hex,
                    hyper_params = hyper_parameters,
                    seed=1,
                    search_criteria = list(strategy = "Sequential", early_stopping = FALSE, max_runtime_secs=1))
  expect_less_than(length(grid4@model_ids), 1000)

}

doTest("Test sequential grid", test.grid.sequential)
