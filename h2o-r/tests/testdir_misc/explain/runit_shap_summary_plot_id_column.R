setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

expect_ggplot <- function(gg) {
  p <- force(gg)
  expect_true("gg" %in% class(p))
  file <- tempfile(fileext = ".png")
  tryCatch({ggplot2::ggsave(file, plot = p)}, finally = unlink(file))
}

# GH-16758: h2o.shap_summary_plot fails when data has a column named "id"
# stats::reshape() auto-generates an "id" column, which collides with user's "id" feature
shap_summary_plot_with_numeric_id_column_test <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  # Add a numeric "id" column as a predictor (this is what triggers the bug)
  train$id <- as.h2o(seq_len(nrow(train)))

  gbm <- h2o.gbm(y = y,
                  training_frame = train,
                  seed = 1234,
                  ntrees = 5)

  # This should not fail even though the data has an "id" column
  expect_ggplot(h2o.shap_summary_plot(gbm, train))
}

shap_summary_plot_with_categorical_id_column_test <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  # Add a categorical "id" column to exercise the factor/encode_cols path
  train$id <- as.h2o(as.factor(paste0("row_", seq_len(nrow(train)))))

  gbm <- h2o.gbm(y = y,
                  training_frame = train,
                  seed = 1234,
                  ntrees = 5)

  expect_ggplot(h2o.shap_summary_plot(gbm, train))
}

doSuite("GH-16758: SHAP summary plot with id column", makeSuite(
  shap_summary_plot_with_numeric_id_column_test,
  shap_summary_plot_with_categorical_id_column_test
))
