setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

rf_early_stopping_suite <- function() {

  test_early_stopping_stops_early <- function() {
    train <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
    x <- c(3:8)
    y <- "economy_20mpg"
    train[y] <- as.factor(train[y])
    ref_model <- h2o.randomForest(x=x, y=y, training_frame=train, seed=1)
    ref_trees <- ref_model@model$model_summary$number_of_trees
    stop_model <- h2o.randomForest(x=x, y=y, training_frame=train, seed=1,
                                   stopping_metric="AUC", stopping_rounds=3, stopping_tolerance=0.1,
                                   score_each_iteration=TRUE)
    stop_trees <- stop_model@model$model_summary$number_of_trees
    # print(stop_trees - ref_trees)
    expect_lt(stop_trees, ref_trees)
  }

  test_stopping_metric_is_case_insensitive <- function() {
    train <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
    x <- c(3:8)
    y <- "economy_20mpg"
    train[y] <- as.factor(train[y])
    uc_model <- h2o.randomForest(x=x, y=y, training_frame=train, seed=1,
                                 stopping_metric="AUC", stopping_rounds=3, stopping_tolerance=0.1,
                                 score_tree_interval=5)
    uc_trees <- uc_model@model$model_summary$number_of_trees
    lc_model <- h2o.randomForest(x=x, y=y, training_frame=train, seed=1,
                                 stopping_metric="auc", stopping_rounds=3, stopping_tolerance=0.1,
                                 score_tree_interval=5)
    lc_trees <- lc_model@model$model_summary$number_of_trees
    expect_equal(uc_trees, lc_trees)
  }

  makeSuite(
    test_early_stopping_stops_early,
    test_stopping_metric_is_case_insensitive
  )
}

doSuite("Stopping Metric Suite", rf_early_stopping_suite())
