setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

expect_ggplot <- function(gg) {
  p <- force(gg)
  expect_true("gg" %in% class(p))
  file <- tempfile(fileext = ".png")
  # try to actually plot it - otherwise ggplot is not evaluated
  tryCatch({ggplot2::ggsave(file, plot = p)}, finally = unlink(file))
}

explanation_test_single_model_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
  y <- "quality"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm <- h2o.gbm(y = y,
                 training_frame = train,
                 seed = 1234)

  # test shap summary
  expect_ggplot(h2o.shap_summary_plot(gbm, train))

  # test shap explain row
  expect_ggplot(h2o.shap_explain_row_plot(gbm, train, 1))

  # test residual analysis
  expect_ggplot(h2o.residual_analysis_plot(gbm, train))

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_plot(gbm, train, col))
  }

  # test ice plot
  for (col in cols_to_test) {
    expect_ggplot(h2o.ice_plot(gbm, train, col))
  }

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1)))
}

explanation_test_automl_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
  y <- "quality"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(aml, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(aml))

  # test shap summary
  expect_error(h2o.shap_summary_plot(aml, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(aml, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(aml, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(aml, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml, train, 1)))
}

explanation_test_list_of_models_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
  y <- "quality"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  models <- lapply(aml@leaderboard$model_id, h2o.getModel)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(models, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(models, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(models, train, 1)))
}


explanation_test_single_model_binomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm <- h2o.gbm(y = y,
                 training_frame = train,
                 seed = 1234)

  # test shap summary
  expect_ggplot(h2o.shap_summary_plot(gbm, train))

  # test shap explain row
  expect_ggplot(h2o.shap_explain_row_plot(gbm, train, 1))

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(gbm, train), "Residual analysis is not implemented for classification.")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_plot(gbm, train, col))
  }

  # test ice plot
  for (col in cols_to_test) {
    expect_ggplot(h2o.ice_plot(gbm, train, col))
  }

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1)))
}

explanation_test_automl_binomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(aml, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(aml))

  # test shap summary
  expect_error(h2o.shap_summary_plot(aml, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(aml, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(aml, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(aml, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml, train, 1)))
}

explanation_test_list_of_models_binomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  models <- lapply(aml@leaderboard$model_id, h2o.getModel)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(models, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(models, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(models, train, 1)))
}


explanation_test_single_model_multinomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/iris/iris2.csv"))
  y <- "response"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm <- h2o.gbm(y = y,
                 training_frame = train,
                 seed = 1234)

  # test shap summary
  expect_error(h2o.shap_summary_plot(gbm, train), "java.lang.UnsupportedOperationException: Calculating contributions is currently not supported for multinomial models.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(gbm, train, 1), "java.lang.UnsupportedOperationException: Calculating contributions is currently not supported for multinomial models.")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(gbm, train), "Residual analysis is not implemented for classification.")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_plot(gbm, train, col, target = "setosa"))
  }

  # test ice plot
  for (col in cols_to_test) {
    expect_ggplot(h2o.ice_plot(gbm, train, col, target = "setosa"))
  }

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1)))
}

explanation_test_automl_multinomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/iris/iris2.csv"))
  y <- "response"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(aml, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(aml))

  # test shap summary
  expect_error(h2o.shap_summary_plot(aml, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(aml, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(aml, train, col, target = "versicolor"))
  }
  # test ice plot
  expect_error(h2o.ice_plot(aml, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml, train, 1)))
}

explanation_test_list_of_models_multinomial_classification <- function() {
  train <- h2o.uploadFile(locate("smalldata/iris/iris2.csv"))
  y <- "response"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  models <- lapply(aml@leaderboard$model_id, h2o.getModel)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "SHAP summary plot requires a tree-based model!")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "SHAP explain_row plot requires a tree-based model!")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(models, train, col, target = "versicolor"))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(models, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(models, train, 1)))
}


doSuite("Explanation Tests", makeSuite(
  explanation_test_single_model_regression
  , explanation_test_automl_regression
  , explanation_test_list_of_models_regression
  , explanation_test_single_model_binomial_classification
  , explanation_test_automl_binomial_classification
  , explanation_test_list_of_models_binomial_classification
  , explanation_test_single_model_multinomial_classification
  , explanation_test_automl_multinomial_classification
  , explanation_test_list_of_models_multinomial_classification
))
