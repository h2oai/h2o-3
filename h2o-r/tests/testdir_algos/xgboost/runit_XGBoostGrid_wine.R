setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



xgboost.grid.test <- function() {
    wine.hex <- h2o.uploadFile(locate("smalldata/gbm_test/wine.data"), destination_frame="wine.hex")
    print(summary(wine.hex))
    # Specify grid hyper parameters
    ntrees_opts <- c(5, 10, 15)
    max_depth_opts <- c(2, 3, 4)
    learn_rate_opts <- c(0.1, 0.2)
    size_of_hyper_space <- length(ntrees_opts) * length(max_depth_opts) * length(learn_rate_opts)
    hyper_params = list( ntrees = ntrees_opts, max_depth = max_depth_opts, learn_rate = learn_rate_opts)
    wine.grid <- h2o.grid("xgboost", y = 2, x = c(1, 3:14),
                   distribution='gaussian',
                   training_frame = wine.hex, 
                   hyper_params = hyper_params)
    print(wine.grid)
    expect_equal(length(wine.grid@model_ids), size_of_hyper_space)

    # Get models
    grid_models <- lapply(wine.grid@model_ids, function(mid) { 
      model = h2o.getModel(mid)
    })
    # Check expected number of models
    expect_equal(length(grid_models), size_of_hyper_space)

    expect_model_param(grid_models, "ntrees", ntrees_opts)
    expect_model_param(grid_models, "max_depth", max_depth_opts)
    expect_model_param(grid_models, "learn_rate", learn_rate_opts)

    
}

doTest("XGBoost Grid Test: wine.data from smalldata", xgboost.grid.test)
