setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.gbm.grid.cvcol <- function() {
    ntrees_opts = c(1, 5)
    learn_rate_opts = c(0.1, 0.01)
    hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
    iris.hex = as.h2o(iris)
    s = h2o.runif(iris.hex, seed = 1234)
    iris.hex$fold = ifelse(s < 0.3333, 0, ifelse(s < 0.6666, 1, 2))
    iris.hex
    grid <- h2o.grid("gbm",
                     grid_id = "gbm_grid_test", 
                     x = 1:4, 
                     y = 5, 
                     training_frame = iris.hex, 
                     hyper_params = hyper_parameters,
                     #nfolds = 3,
                     fold_column = "fold")
    grid_models <- lapply(grid@model_ids, function(mid) {
      model = h2o.getModel(mid)
    })
    grid_models
    expect_equal(length(grid_models), 4)
}

doTest("GBM Grid Search: iteration over parameters", check.gbm.grid.cvcol)

