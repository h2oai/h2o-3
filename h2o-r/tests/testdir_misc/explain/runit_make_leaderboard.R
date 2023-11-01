setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_make_leaderboard_without_leaderboard_frame <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  aml2 <- h2o.automl(y = y,
                     max_models = 5,
                     training_frame = train,
                     seed = 134)

  grid <- h2o.grid("gbm", y=y, training_frame = train, hyper_params = list(ntrees = c(1, 2, 3)))

  # without leaderboard frame
  for (score_data in c("AUTO", "xval", "valid", "train")){
    expect_true(is.data.frame(h2o.make_leaderboard(aml, scoring_data = score_data)))
    expect_true(is.data.frame(h2o.make_leaderboard(list(aml, aml2), scoring_data = score_data)))

    expect_true(is.data.frame(h2o.make_leaderboard(grid, scoring_data = score_data)))
    expect_true(is.data.frame(h2o.make_leaderboard(list(aml, grid, aml2@leader), scoring_data = score_data)))
  }

  expect_error(h2o.make_leaderboard(aml, extra_columns = "ALL"))
  expect_error(h2o.make_leaderboard(aml, extra_columns = "predict_time_per_row_ms"))
}

test_make_leaderboard_with_leaderboard_frame <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  aml2 <- h2o.automl(y = y,
                     max_models = 5,
                     training_frame = train,
                     seed = 134)

  grid <- h2o.grid("gbm", y=y, training_frame = train, hyper_params = list(ntrees = c(1, 2, 3)))
  # with leaderboard frame
  expected_cols <- c("model_id", "rmse", "mse", "mae", "rmsle", "mean_residual_deviance",
                     "training_time_ms", "predict_time_per_row_ms", "algo")
  ldb <- h2o.make_leaderboard(aml, train, extra_columns = "ALL")
  expect_true(is.data.frame(ldb))
  expect_true(all(expected_cols %in% names(ldb)))

  for (score_data in c("AUTO", "xval", "valid", "train")){
    expect_true(is.data.frame(h2o.make_leaderboard(aml, train, scoring_data = score_data)))
    expect_true(is.data.frame(h2o.make_leaderboard(list(aml, aml2), train, scoring_data = score_data)))

    expect_true(is.data.frame(h2o.make_leaderboard(grid, scoring_data = score_data)))
    expect_true(is.data.frame(h2o.make_leaderboard(list(aml, grid, aml2@leader), train, scoring_data = score_data)))
  }


  # extra columns
  for (ec in c("training_time_ms", "predict_time_per_row_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(grid, train, extra_columns = ec)))

  # extra columns without leaderboard frame
  for (ec in c("training_time_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(grid, extra_columns = ec)))

  # sort metrics
  for (sm in c("rmse", "mse", "mae", "rmsle", "mean_residual_deviance"))
    expect_true(names(h2o.make_leaderboard(grid, train, sort_metric = sm))[[2]] == sm)
}

test_make_leaderboard_grid <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  grid <- h2o.grid("gbm", y=y, training_frame = train, hyper_params = list(ntrees = c(1, 2, 3)))

  for (score_data in c("AUTO", "xval", "valid", "train")){
    expect_true(is.data.frame(h2o.make_leaderboard(grid, scoring_data = score_data)))
  }

  # extra columns
  for (ec in c("training_time_ms", "predict_time_per_row_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(grid, train, extra_columns = ec)))

  # extra columns without leaderboard frame
  for (ec in c("training_time_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(grid, extra_columns = ec)))

  # sort metrics
  for (sm in c("rmse", "mse", "mae", "rmsle", "mean_residual_deviance"))
    expect_true(names(h2o.make_leaderboard(grid, train, sort_metric = sm))[[2]] == sm)
}

test_make_leaderboard_automl <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)

  expected_cols <- c("model_id", "rmse", "mse", "mae", "rmsle", "mean_residual_deviance",
                     "training_time_ms", "predict_time_per_row_ms", "algo")
  ldb <- h2o.make_leaderboard(aml, train, extra_columns = "ALL")
  expect_true(is.data.frame(ldb))
  expect_true(all(expected_cols %in% names(ldb)))

  for (score_data in c("AUTO", "xval", "valid", "train")){
    expect_true(is.data.frame(h2o.make_leaderboard(aml, train, scoring_data = score_data)))
  }


  # extra columns
  for (ec in c("training_time_ms", "predict_time_per_row_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(aml, train, extra_columns = ec)))

  # extra columns without leaderboard frame
  for (ec in c("training_time_ms", "algo"))
    expect_true(ec %in% names(h2o.make_leaderboard(aml, extra_columns = ec)))

  # sort metrics
  for (sm in c("rmse", "mse", "mae", "rmsle", "mean_residual_deviance"))
    expect_true(names(h2o.make_leaderboard(aml, train, sort_metric = sm))[[2]] == sm)
}


doSuite("Leaderboard Tests", makeSuite(
  test_make_leaderboard_without_leaderboard_frame,
  test_make_leaderboard_with_leaderboard_frame,
  test_make_leaderboard_grid,
  test_make_leaderboard_automl
))
