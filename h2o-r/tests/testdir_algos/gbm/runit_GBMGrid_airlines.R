setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



gbm.grid.test <- function() {
    air.hex <- h2o.uploadFile(h2oTest.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
    print(summary(air.hex))
    myX <- c("DayofMonth", "DayOfWeek")
    # Specify grid hyper parameters
    ntrees_opts <- c(5, 10, 15)
    max_depth_opts <- c(2, 3, 4)
    learn_rate_opts <- c(0.1, 0.2)
    size_of_hyper_space <- length(ntrees_opts) * length(max_depth_opts) * length(learn_rate_opts)
    hyper_params = list( ntrees = ntrees_opts, max_depth = max_depth_opts, learn_rate = learn_rate_opts)
    air.grid <- h2o.grid("gbm", y = "IsDepDelayed", x = myX,
                   distribution="bernoulli",
                   training_frame = air.hex,
                   hyper_params = hyper_params)
    print(air.grid)
    expect_equal(length(air.grid@model_ids), size_of_hyper_space)

    # Get models
    grid_models <- lapply(air.grid@model_ids, function(mid) { 
      model = h2o.getModel(mid)
    })
    # Check expected number of models
    expect_equal(length(grid_models), size_of_hyper_space)

    h2oTest.expectModelParam(grid_models, "ntrees", ntrees_opts)
    h2oTest.expectModelParam(grid_models, "max_depth", max_depth_opts)
    h2oTest.expectModelParam(grid_models, "learn_rate", learn_rate_opts)

    #
    # test random/max_models search criterion
    size_of_hyper_space <- 5
    search_criteria = list(strategy = "RandomDiscrete", max_models = size_of_hyper_space)
    air.grid <- h2o.grid("gbm", y = "IsDepDelayed", x = myX,
                         distribution="bernoulli",
                         training_frame = air.hex,
                         hyper_params = hyper_params,
                         search_criteria = search_criteria)
    print(air.grid)
    expect_equal(length(air.grid@model_ids), size_of_hyper_space)
}

h2oTest.doTest("GBM Grid Test: Airlines Smalldata", gbm.grid.test)
