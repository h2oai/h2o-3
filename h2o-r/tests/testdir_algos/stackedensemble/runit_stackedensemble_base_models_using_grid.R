setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

prepare_test_env <- function() {
  train <- h2o.uploadFile(locate("smalldata/junit/weather.csv"),
                          destination_frame = "weather_train")

  y <- "RainTomorrow"
  x <- setdiff(names(train), y)
  train[,y] <- as.factor(train[,y])
  nfolds <- 5
  env <- list()
  env$train <- train
  env$x <- x
  env$y <- y
  env$my_gbm <- h2o.gbm(x = x,
                        y = y,
                        training_frame = train,
                        distribution = "bernoulli",
                        ntrees = 10,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE,
                        seed = 1)

  hyper_params <- list(ntrees = c(3, 5))

  env$grid1 <- h2o.grid("gbm", x = x, y = y,
                        training_frame = train,
                        validation_frame = train,
                        seed = 1,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE,
                        hyper_params = hyper_params)

  env$grid2 <- h2o.grid("drf", x = x, y = y,
                        training_frame = train,
                        validation_frame = train,
                        seed = 1,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE,
                        hyper_params = hyper_params)
  return(env)
}

get_base_models <- function(ensemble) {
  unlist(lapply(ensemble@parameters$base_models, function (base_model) base_model$name))
}

expect_equal_unordered <- function (lhs, rhs) {
  expect_equal(sort(lhs), sort(rhs))
}

# PUBDEV-4534
stackedensemble_base_models_accept_list_of_models_test <- function() {
  attach(prepare_test_env())
  stack0 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm))
  expect_equal_unordered(get_base_models(stack0), my_gbm@model_id)
}

stackedensemble_base_models_accept_grid_test <- function() {
  # Single Grid
  attach(prepare_test_env())
  stack1 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = grid1
  )

  stack2 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = grid1@model_ids
  )

  expect_equal_unordered(get_base_models(stack1), get_base_models(stack2))
}

stackedensemble_base_models_accept_list_of_grids_test <- function() {
  # List of Grids
  attach(prepare_test_env())
  stack3 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = list(grid1, grid2)
  )

  stack4 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = as.list(c(grid1@model_ids, grid2@model_ids))
  )

  expect_equal_unordered(get_base_models(stack3), get_base_models(stack4))
}

stackedensemble_base_models_accept_mixture_of_grids_and_models_test <- function() {
  # List of grids and models
  attach(prepare_test_env())
  stack5 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = list(grid1, my_gbm, grid2)
  )

  stack6 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = as.list(c(grid1@model_ids, my_gbm@model_id, grid2@model_ids))
  )

  expect_equal_unordered(get_base_models(stack5), get_base_models(stack6))
}

stackedensemble_base_models_accept_mixture_of_grids_and_models_and_their_ids_test <- function() {
  # List of grids and models using model_id
  attach(prepare_test_env())
  stack7 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = list(grid1, my_gbm@model_id, grid2)
  )

  stack8 <- h2o.stackedEnsemble(x=x,
                                y=y,
                                training_frame = train,
                                base_models = as.list(c(grid1@model_ids, my_gbm@model_id, grid2@model_ids))
  )

  expect_equal_unordered(get_base_models(stack7), get_base_models(stack8))
}

stackedensemble_base_models_validate_test <- function() {
  # Check that we validate base models
  # For some reason the error is printed so to make cleaner test logs I capture the output here as
  # it could be confusing seeing exception in passing test
  attach(prepare_test_env())
  expect_error(capture.output(h2o.stackedEnsemble(x=x,
                                                  y=y,
                                                  training_frame = train,
                                                  base_models = as.list(c(grid1@model_ids, h2o.keyof(train))))),
               paste0("Unsupported type of base model \"", h2o.keyof(train), "\". Should be either a model or a grid."))
}

doSuite("Stacked Ensemble accept both models and grid as base models", makeSuite(
  stackedensemble_base_models_accept_list_of_models_test,
  stackedensemble_base_models_accept_grid_test,
  stackedensemble_base_models_accept_list_of_grids_test,
  stackedensemble_base_models_accept_mixture_of_grids_and_models_and_their_ids_test,
  stackedensemble_base_models_accept_mixture_of_grids_and_models_test,
  stackedensemble_base_models_validate_test
))
