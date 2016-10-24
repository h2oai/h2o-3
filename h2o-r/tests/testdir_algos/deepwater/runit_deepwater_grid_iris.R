setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.deeplearning_hyper <- function() {
  if (!h2o.deepwater.available()) return()

  Log.info("Test checks if Deep Water works well with random grid search")
  
  train <- as.h2o(iris)
  predictors=1:4
  response_col=5
  hidden_opts <- list(c(20, 20), c(50, 50, 50), c(200,200), c(50,50,50,50,50))
  activation_opts <- c("tanh", "rectifier")
  learnrate_opts <- seq(1e-3, 1e-2, 1e-3)
  momentum_opts <- seq(0, 1, 1e-2)
  max_models <- 1000      ## don't build more than this many models (this won't trigger)
  nfolds <- 3             ## use cross-validation to rank models and to find optimal number of epochs for each model
  seed <- 42
  max_runtime_secs <- 30  ## limit overall time (this triggers)
  
  hyper_params <- list(activation = activation_opts, hidden = hidden_opts, learning_rate = learnrate_opts)
  search_criteria = list(strategy = "RandomDiscrete", 
                         max_models = max_models, seed = seed, max_runtime_secs = max_runtime_secs,
                         stopping_rounds=5,          ## enable early stopping of the overall leaderboard
                         stopping_metric="logloss",
                         stopping_tolerance=1e-4)
  
  dw_grid = h2o.grid("deepwater", grid_id="deepwater_grid",
                     x=predictors, y=response_col, training_frame=train, 
                     epochs=500,                 ## long enough to allow early stopping 
                     nfolds=nfolds,
                     stopping_rounds=3,          ## enable early stopping of each model in the hyperparameter search
                     stopping_metric="logloss",
                     stopping_tolerance=1e-3,    ## stop once validation logloss of the cv models doesn't improve enough
                     hyper_params=hyper_params, 
                     search_criteria = search_criteria)
  
  print(dw_grid)
  checkTrue(dw_grid@summary_table$logloss[1] <= 0.5, "Best Logloss is too high!")
}

doTest("Deep Water Random Hyperparameter Search Test", check.deeplearning_hyper)

