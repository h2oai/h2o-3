setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

count_features_in_plot <- function(p) {
  length(levels(droplevels(p$data$feature)))
}

# GH-16757: h2o.shap_summary_plot shows one extra feature when top_n_features is specified
shap_summary_plot_top_n_features_test <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  gbm <- h2o.gbm(y = y,
                  training_frame = train,
                  seed = 1234,
                  ntrees = 20)

  # Determine the actual number of SHAP features from an unfiltered plot,
  # since some model features (e.g. string columns) may not produce contributions.
  p_all <- h2o.shap_summary_plot(gbm, train, top_n_features = -1)
  n_shap_features <- count_features_in_plot(p_all)
  cat("Number of SHAP features:", n_shap_features, "\n")

  # Test various top_n_features values - each should show exactly the requested number
  for (top_n in c(3, 5, 7, 10)) {
    if (top_n >= n_shap_features) next

    p <- h2o.shap_summary_plot(gbm, train, top_n_features = top_n)
    features_in_plot <- count_features_in_plot(p)

    cat("Requested top_n_features =", top_n, ", got", features_in_plot, "features in plot\n")
    expect_equal(features_in_plot, top_n,
                 info = paste0("top_n_features=", top_n, " should show exactly ", top_n,
                               " features but showed ", features_in_plot))
  }

  # Default (top_n_features=20) should work correctly when there are fewer features
  p_default <- h2o.shap_summary_plot(gbm, train)
  features_default <- count_features_in_plot(p_default)
  expect_true(features_default <= 20,
              info = paste0("Default top_n_features=20 showed ", features_default, " features"))
}

shap_summary_plot_top_n_features_corner_cases_test <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  gbm <- h2o.gbm(y = y,
                  training_frame = train,
                  seed = 1234,
                  ntrees = 20)

  # Get actual SHAP feature count from unfiltered plot
  p_all <- h2o.shap_summary_plot(gbm, train, top_n_features = -1)
  n_shap_features <- count_features_in_plot(p_all)

  # top_n_features = 1 should show exactly 1 feature
  p <- h2o.shap_summary_plot(gbm, train, top_n_features = 1)
  expect_equal(count_features_in_plot(p), 1)

  # top_n_features equal to total number of features — all features shown
  p <- h2o.shap_summary_plot(gbm, train, top_n_features = n_shap_features)
  expect_equal(count_features_in_plot(p), n_shap_features)

  # top_n_features greater than total number of features — all features shown
  p <- h2o.shap_summary_plot(gbm, train, top_n_features = n_shap_features + 10)
  expect_equal(count_features_in_plot(p), n_shap_features)

  # top_n_features = -1 means show all features
  p <- h2o.shap_summary_plot(gbm, train, top_n_features = -1)
  expect_equal(count_features_in_plot(p), n_shap_features)

  # top_n_features = 0 should error
  expect_error(h2o.shap_summary_plot(gbm, train, top_n_features = 0))
}

doSuite("GH-16757 SHAP Summary Plot top_n_features", makeSuite(
  shap_summary_plot_top_n_features_test,
  shap_summary_plot_top_n_features_corner_cases_test
))