setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.gbm.grid <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")
  print(summary(iris.hex))

  pretty.list <- function(ll) {
    str <- lapply(ll, function(x) { paste("(", paste(x, collapse = ","), ")", sep = "") })
    paste(str, collapse = ",")
  }
  ntrees_opts = c(1, 5)
  learn_rate_opts = c(0.1, 0.01)
  size_of_hyper_space = length(ntrees_opts) * length(learn_rate_opts)

  hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  Log.info(paste("GBM grid with the following hyper_parameters:", pretty.list(hyper_parameters)))
  gg <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
  expect_equal(length(gg@model_ids), size_of_hyper_space)

  # Get models
  gg_models <- lapply(gg@model_ids, function(mid) { 
    model = h2o.getModel(mid)
  })
  # Check expected number of models
  expect_equal(length(gg_models), size_of_hyper_space)

  # Check parameters coverage
  # ntrees
  expect_model_param(gg_models, "ntrees", ntrees_opts)

  # Learn rate
  expect_model_param(gg_models, "learn_rate", learn_rate_opts)

  cat("\n\n Grid search results:")
  print(gg)

  testEnd()
}

doTest("GBM Grid Search: iteration over parameters", check.gbm.grid)

