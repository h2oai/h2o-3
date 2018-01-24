setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



xgboost.grid.test <- function() {
    air.hex <- h2o.uploadFile(locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
    print(summary(air.hex))
    myX <- c("DayofMonth", "DayOfWeek")
    # Specify grid hyper parameters
    ntrees_opts <- c(5, 10, 15)
    max_depth_opts <- c(2, 3, 4)
    learn_rate_opts <- c(0.1, 0.2)
    size_of_hyper_space <- length(ntrees_opts) * length(max_depth_opts) * length(learn_rate_opts)
    hyper_params = list( ntrees = ntrees_opts, max_depth = max_depth_opts, learn_rate = learn_rate_opts)
    air.grid <- h2o.grid("xgboost", y = "IsDepDelayed", x = myX,
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

    expect_model_param(grid_models, "ntrees", ntrees_opts)
    expect_model_param(grid_models, "max_depth", max_depth_opts)
    expect_model_param(grid_models, "learn_rate", learn_rate_opts)

    #
    # test random/max_models search criterion: max_models
    max_models <- 5
    search_criteria = list(strategy = "RandomDiscrete", max_models = max_models, seed=1234)
    air.grid <- h2o.grid("xgboost", y = "IsDepDelayed", x = myX,
                         distribution="bernoulli",
                         training_frame = air.hex,
                         hyper_params = hyper_params,
                         search_criteria = search_criteria)
    print(air.grid)
    expect_equal(length(air.grid@model_ids), max_models)

    # test random/max_models search criterion: asymptotic
    search_criteria = list(strategy = "RandomDiscrete", stopping_metric = "AUTO", stopping_tolerance = 0.01, stopping_rounds = 3, seed=1234)
    air.grid <- h2o.grid("xgboost", y = "IsDepDelayed", x = myX,
                         distribution="bernoulli",
                         training_frame = air.hex,
                         hyper_params = hyper_params,
                         search_criteria = search_criteria,
                         nfolds = 5, fold_assignment = 'Modulo',
                         keep_cross_validation_predictions = TRUE,
                         seed = 5678)
    print(air.grid)
    expect_that(length(air.grid@model_ids) < size_of_hyper_space, is_true())

    # stacker.grid <- h2o.grid("stackedensemble", y = "IsDepDelayed", x = myX,
    #                         training_frame = air.hex,
    #                         model_id = "my_ensemble",
    #                         base_models = air.grid@model_ids)
    stacker <- h2o.stackedEnsemble(x = myX, y = "IsDepDelayed", training_frame = air.hex,
                                   model_id = "my_ensemble",
                                   base_models = air.grid@model_ids)

    predictions = h2o.predict(stacker, air.hex) # training data
    print("preditions for ensemble are in: ")
    print(h2o.getId(predictions))
}

doTest("XGBoost Grid Test: Airlines Smalldata", xgboost.grid.test)
