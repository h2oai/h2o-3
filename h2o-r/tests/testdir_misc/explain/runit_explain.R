setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

expect_ggplot <- function(gg) {
  p <- force(gg)
  expect_true("gg" %in% class(p))
  file <- tempfile(fileext = ".png")
  # try to actually plot it - otherwise ggplot is not evaluated
  tryCatch({ggplot2::ggsave(file, plot = p)}, finally = unlink(file))
}

varimp_test <- function () {
  train <- h2o.uploadFile(locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
  y <- "quality"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  expect_equal(dim(h2o.varimp(aml)), c(5, 12))
  expect_equal(dim(h2o.varimp(aml, top_n = 3, num_of_features = 4)), c(3, 4))

  expect_ggplot(h2o.varimp_heatmap(aml))
  expect_ggplot(h2o.varimp_heatmap(aml, top_n = 3, num_of_features = 4))
}

explanation_test_single_model_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm <- h2o.gbm(y = y,
                 training_frame = train,
                 seed = 1234,
                 model_id = "my_awesome_model")

  # test shap summary
  expect_ggplot(h2o.shap_summary_plot(gbm, train))

  # test shap explain row
  expect_ggplot(h2o.shap_explain_row_plot(gbm, train, 1))

  # test residual analysis
  expect_ggplot(h2o.residual_analysis_plot(gbm, train))

  # test partial dependences
  for (col in cols_to_test) {
    if (col == "name")  # a string column
      expect_error(expect_ggplot(h2o.pd_multi_plot(models, train, col)))
    else
      expect_ggplot(h2o.pd_plot(gbm, train, col))
  }

  # test ice plot
  for (col in cols_to_test) {
    if (col == "name")  # a string column
      expect_error(expect_ggplot(h2o.pd_multi_plot(models, train, col)))
    else
      expect_ggplot(h2o.ice_plot(gbm, train, col))
  }

  # test learning curve plot
  expect_ggplot(h2o.learning_curve_plot(gbm))
  for (metric in c("auto", "rmse", "deviance")) {
    expect_ggplot(h2o.learning_curve_plot(gbm, metric = metric))
    expect_ggplot(h2o.learning_curve_plot(gbm, metric = toupper(metric)))
  }

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1)))
}

explanation_test_automl_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

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
  expect_error(h2o.shap_summary_plot(aml, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(aml, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    if (col == "name")  # a string column
      expect_error(expect_ggplot(h2o.pd_multi_plot(models, train, col)))
    else
      expect_ggplot(h2o.pd_multi_plot(aml, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(aml, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml, train, 1)))

  # Leaderboard slice works
  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml@leaderboard[-1,], train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml@leaderboard[-1,], train, 1)))
}

explanation_test_list_of_models_regression <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)
  models <- lapply(aml@leaderboard$model_id, h2o.getModel)
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    if (col == "name")  # a string column
      expect_error(expect_ggplot(h2o.pd_multi_plot(models, train, col)))
    else
      expect_ggplot(h2o.pd_multi_plot(models, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test learning curve plot
  for (model in models) {
    expect_ggplot(h2o.learning_curve_plot(model))
  }

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
                 seed = 1234,
                 model_id = "my_awesome_model")

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

  # test learning curve plot
  expect_ggplot(h2o.learning_curve_plot(gbm))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(gbm, train, top_n_features = -1)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(gbm, train, 1, top_n_features = -1)))
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
  expect_error(h2o.shap_summary_plot(aml, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

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

  # Leaderboard slice works
  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(aml@leaderboard[-1,], train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(aml@leaderboard[-1,]))

  # test shap summary
  expect_error(h2o.shap_summary_plot(aml@leaderboard[-1,], train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml@leaderboard[-1,], train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test partial dependences
  expect_ggplot(h2o.pd_multi_plot(aml@leaderboard[-1,], train, cols_to_test[[1]]))

  # test ice plot
  expect_error(h2o.ice_plot(aml@leaderboard[-1,], train, cols_to_test[[1]]), "Only one model is allowed!")

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(aml@leaderboard[-1,], train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(aml@leaderboard[-1,], train, 1)))
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
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(models, train, col))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test learning curve plot
  for (model in models) {
    expect_ggplot(h2o.learning_curve_plot(model))
  }

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
                 seed = 1234,
                 model_id = "my_awesome_model")

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

  # test learning curve plot
  expect_ggplot(h2o.learning_curve_plot(gbm))

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
  expect_error(h2o.shap_summary_plot(aml, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(aml, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

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

  # test shortening model ids work
  model_ids <- as.character(as.list(aml@leaderboard$model_id))
  shortened_model_ids <- .shorten_model_ids(model_ids)
  expect_true(length(model_ids) == length(shortened_model_ids))
  for (i in seq_along(model_ids)) {
    expect_true(nchar(model_ids[i]) > nchar(shortened_model_ids[i]))
  }
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
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

  # test model correlation
  expect_ggplot(h2o.model_correlation_heatmap(models, train))

  # test variable importance heatmap
  expect_ggplot(h2o.varimp_heatmap(models))

  # test shap summary
  expect_error(h2o.shap_summary_plot(models, train), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test shap explain row
  expect_error(h2o.shap_explain_row_plot(models, train, 1), "Calculation of feature contributions without a background frame requires a tree-based model.")

  # test residual analysis
  expect_error(h2o.residual_analysis_plot(models, train), "Residual analysis works only on a single model!")

  # test partial dependences
  for (col in cols_to_test) {
    expect_ggplot(h2o.pd_multi_plot(models, train, col, target = "versicolor"))
  }
  # test ice plot
  expect_error(h2o.ice_plot(models, train, cols_to_test[[1]]), "Only one model is allowed!")

  # test learning curve plot
  for (model in models) {
    expect_ggplot(h2o.learning_curve_plot(model))
  }

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain(models, train)))

  # test explanation
  expect_true("H2OExplanation" %in% class(h2o.explain_row(models, train, 1)))
}

learning_curve_plot_test_of_models_not_included_in_automl <- function() {
  # GLM without lambda search
  prostate <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))

  prostate$CAPSULE <- as.factor(prostate$CAPSULE)
  prostate$RACE <- as.factor(prostate$RACE)
  prostate$DCAPS <- as.factor(prostate$DCAPS)
  prostate$DPROS <- as.factor(prostate$DPROS)

  predictors <- c("AGE", "RACE", "VOL", "GLEASON")
  response <- "CAPSULE"

  glm_model <- h2o.glm(family = "binomial",
                       x = predictors,
                       y = response,
                       training_frame = prostate,
                       lambda = 0,
                       compute_p_values = TRUE)
  expect_ggplot(h2o.learning_curve_plot(glm_model))

  # GAM
  knots1 <- c(-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290)
  frame_Knots1 <- as.h2o(knots1)
  knots2 <- c(-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589)
  frame_Knots2 <- as.h2o(knots2)
  knots3 <- c(-1.999675688, -0.979893796, 0.007573327, 1.011437347, 1.999611676)
  frame_Knots3 <- as.h2o(knots3)

  h2o_data <- h2o.uploadFile(locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
  h2o_data["C1"] <- as.factor(h2o_data["C1"])
  h2o_data["C2"] <- as.factor(h2o_data["C2"])
  h2o_data["C11"] <- as.factor(h2o_data["C11"])
  splits <- h2o.splitFrame(data = h2o_data, ratios = 0.8)
  train <- splits[[1]]
  test <- splits[[2]]
  predictors <- colnames(train[1:2])
  response <- 'C11'
  numKnots <- c(5, 5, 5)
  gam_model <- h2o.gam(x = predictors,
                       y = response,
                       training_frame = train,
                       validation_frame = test,
                       family = 'multinomial',
                       gam_columns = c("C6", "C7", "C8"),
                       scale = c(1, 1, 1),
                       num_knots = numKnots,
                       knot_ids = c(h2o.keyof(frame_Knots1), h2o.keyof(frame_Knots2), h2o.keyof(frame_Knots3)))
  expect_ggplot(h2o.learning_curve_plot(gam_model))

  # GLRM
  arrests <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
  glrm_model <- h2o.glrm(training_frame = arrests,
                         k = 4,
                         loss = "Quadratic",
                         gamma_x = 0.5,
                         gamma_y = 0.5,
                         max_iterations = 700,
                         recover_svd = TRUE,
                         init = "SVD",
                         transform = "STANDARDIZE")
  expect_ggplot(h2o.learning_curve_plot(glrm_model))

  # CoxPH
  heart <- h2o.uploadFile(locate("smalldata/coxph_test/heart.csv"))
  coxph_model <- h2o.coxph(x = "age",
                           event_column = "event",
                           start_column = "start",
                           stop_column = "stop",
                           ties = "breslow",
                           training_frame = heart)
  expect_ggplot(h2o.learning_curve_plot(coxph_model))
  expect_ggplot(h2o.learning_curve_plot(coxph_model, metric = "loglik"))

  # IsolationForest
  if_model <- h2o.isolationForest(training_frame = prostate,
                                  sample_rate = 0.1,
                                  max_depth = 5,
                                  ntrees = 5)
  expect_ggplot(h2o.learning_curve_plot(if_model))
}

explanation_test_timeseries <- function() {
  train <- h2o.uploadFile(locate("smalldata/timeSeries/CreditCard-ts_train.csv"))
  x <- c("MONTH", "LIMIT_BAL", "SEX", "EDUCATION", "MARRIAGE", "AGE", "PAY_STATUS", "PAY_AMT", "BILL_AMT")
  y <- "DEFAULT_PAYMENT_NEXT_MONTH"

  train[c(5, 7, 11, 13, 17), "MONTH"] <- NA

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) %in% x]
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

explanation_test_automl_pareto_front <- function() {
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])


  aml <- h2o.automl(y = y,
                    max_models = 5,
                    training_frame = train,
                    seed = 1234)

  expect_true(is.data.frame(h2o.pareto_front(aml)@pareto_front))
  expect_ggplot(plot(h2o.pareto_front(aml)))
  # Non-default criteria
  expect_ggplot(plot(h2o.pareto_front(aml, x_metric = "training_time_ms", y_metric = "rmse")))
}

explanation_test_grid_pareto_front <- function() {
  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])

  grid <- h2o.grid("gbm", y = y, training_frame = train,
                   hyper_params = list(ntrees = 1:6),
                   seed = 1234)

  expect_true(is.data.frame(h2o.pareto_front(grid, train)@pareto_front))
  expect_ggplot(plot(h2o.pareto_front(grid, train)))
  # Non-default criteria
  expect_ggplot(plot(h2o.pareto_front(grid, x_metric = "auc", y_metric = "rmse")))
}

explanation_test_some_dataframe_pareto_front <- function() {
  expect_true(is.data.frame(h2o.pareto_front(iris, x_metric = "Petal.Length", y_metric = "Sepal.Width")@pareto_front))
  expect_ggplot(plot(h2o.pareto_front(iris, x_metric = "Petal.Length", y_metric = "Sepal.Width")))

  iris_h2o <- as.h2o(iris)
  expect_true(is.data.frame(h2o.pareto_front(iris_h2o, x_metric = "Petal.Length", y_metric = "Sepal.Width")@pareto_front))
  expect_ggplot(plot(h2o.pareto_front(iris_h2o, x_metric = "Petal.Length", y_metric = "Sepal.Width")))
}

pareto_front_corner_cases_test <- function() {
  df <- data.frame(
    name = c("top left", "left", "left", "bottom left", "bottom", "bottom", "bottom right", "right", "right", "top right", "top", "top", "inner"),
    x    = c(         0,      0,      0,             0,      0.3,      0.6,              1,       1,       1,           1,   0.7,   0.4,    0.5),
    y    = c(         1,    0.8,    0.2,             0,        0,        0,              0,    0.35,    0.65,           1,     1,     1,    0.5)
  )

  tl <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "top left")
  tr <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "top right")
  bl <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "bottom left")
  br <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "bottom right")

  expect_true(nrow(tl) == 1)
  expect_true(nrow(tr) == 1)
  expect_true(nrow(bl) == 1)
  expect_true(nrow(br) == 1)

  expect_true(all(tl$name == "top left"))
  expect_true(all(tr$name == "top right"))
  expect_true(all(bl$name == "bottom left"))
  expect_true(all(br$name == "bottom right"))

  df <- data.frame(
    name = c("top left", "top left", "bottom left", "bottom left", "bottom left", "bottom right", "bottom right", "bottom right", "top right", "top right", "top right", "top left", "inner"),
    x    = c(       0.1,          0,             0,           0.1,           0.3,      0.6,                  0.9,              1,           1,         0.9,         0.7,        0.4,    0.5),
    y    = c(       0.9,        0.8,           0.2,           0.1,             0,        0,                  0.1,           0.35,        0.65,         0.9,           1,          1,    0.5)
  )

  tl <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "top left")
  tr <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "top right")
  bl <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "bottom left")
  br <- .calculate_pareto_front(df, x = "x", y = "y", optimum = "bottom right")

  expect_true(nrow(tl) == 3)
  expect_true(nrow(tr) == 3)
  expect_true(nrow(bl) == 3)
  expect_true(nrow(br) == 3)

  expect_true(all(tl$name == "top left"))
  expect_true(all(tr$name == "top right"))
  expect_true(all(bl$name == "bottom left"))
  expect_true(all(br$name == "bottom right"))
}


fairness_plots_test <- function() {
  data <- h2o.uploadFile(locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))

  x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3')
  y <- "default payment next month"
  protected_columns <- c('SEX', 'EDUCATION', 'MARRIAGE')

  for (col in c(y, protected_columns))
    data[[col]] <- h2o.asfactor(data[[col]])

  splits <- h2o.splitFrame(data, 0.98)
  train <- splits[[1]]
  test <- splits[[2]]
  reference <- c("1", "2", "2")  # university educated single man
  favorable_class <- "0"  # no default next month

  aml <- h2o.automl(x, y, train, max_models=12)

  models <- lapply(aml@leaderboard$model_id, h2o.getModel)
  da <- h2o.disparate_analysis(models, test, protected_columns, reference, favorable_class)

  expect_ggplot(plot(h2o.pareto_front(da, "auc", "air_min", optimum="top right")))

  expect_true("H2OExplanation" %in% class(h2o.inspect_model_fairness(h2o.get_best_model(aml, "deeplearning"),  test, protected_columns, reference, favorable_class, c("auc", "f1", "p.value", "selectedRatio", "total"))))
  expect_true("H2OExplanation" %in% class(h2o.inspect_model_fairness(h2o.get_best_model(aml, "drf"),  test, protected_columns, reference, favorable_class, c("auc", "f1", "p.value", "selectedRatio", "total"))))
  expect_true("H2OExplanation" %in% class(h2o.inspect_model_fairness(h2o.get_best_model(aml, "gbm"),  test, protected_columns, reference, favorable_class, c("auc", "f1", "p.value", "selectedRatio", "total"))))
  expect_true("H2OExplanation" %in% class(h2o.inspect_model_fairness(h2o.get_best_model(aml, "glm"),  test, protected_columns, reference, favorable_class, c("auc", "f1", "p.value", "selectedRatio", "total"))))
  expect_true("H2OExplanation" %in% class(h2o.inspect_model_fairness(h2o.get_best_model(aml, "xgboost"),  test, protected_columns, reference, favorable_class, c("auc", "f1", "p.value", "selectedRatio", "total"))))
}


shap_plots_work_with_background_frame_test <- function(){
 data <- h2o.uploadFile(locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))

  x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1', 'BILL_AMT2', 'BILL_AMT3')
  y <- "default payment next month"
  protected_columns <- c('SEX', 'EDUCATION', 'MARRIAGE')

  for (col in c(y, protected_columns))
    data[[col]] <- h2o.asfactor(data[[col]])

  splits <- h2o.splitFrame(data[1:500,])
  train <- splits[[1]]
  test <- splits[[2]]
  reference <- c("1", "2", "2")  # university educated single man
  favorable_class <- "0"  # no default next month

  aml <- h2o.automl(x, y, train, max_models=12)


  ALGOS <- c("deeplearning", "drf", "gbm", "glm", "stackedensemble", "xgboost")
  models <- c()
  for (algo in ALGOS) {
    cat(algo, "\n")
    model <- h2o.get_best_model(aml, algo)
    models <- c(model, models)
    expect_ggplot(plot(h2o.shap_summary_plot(model, test, background_frame = train)))
    expect_ggplot(plot(h2o.shap_explain_row_plot(model, test, 2, background_frame = train)))
    lapply(h2o.fair_shap_plot(model, test, protected_columns = protected_columns, column="AGE", background_frame = train), 
        function (gg) expect_ggplot(plot(gg))
    )

    imf <- h2o.inspect_model_fairness(model, test, protected_columns = protected_columns,
                                      reference = reference, favorable_class = favorable_class, background_frame = train)
    expect_true(length(imf[["SHAP"]][["plots"]]) > 1)
  }

  cat("explain\n")
  ex <- h2o.explain(models, test)
  exb <- h2o.explain(models, test, background_frame = train)

  expect_true(length(ex$shap_summary$plots) < length(exb$shap_summary$plots))

  expect_true(!any(grepl("GLM|DeepLearning|StackedEnsemble", names(ex$shap_summary$plots))))
  expect_true(any(grepl("GLM|DeepLearning|StackedEnsemble", names(exb$shap_summary$plots))))

  cat("explain_row\n")
  ex <- h2o.explain_row(models, test, 1)
  exb <- h2o.explain_row(models, test, 1, background_frame = train)

  expect_true(length(ex$shap_explain_row$plots) < length(exb$shap_explain_row$plots))

  expect_true(!any(grepl("GLM|DeepLearning|StackedEnsemble", names(ex$shap_explain_row$plots))))
  expect_true(any(grepl("GLM|DeepLearning|StackedEnsemble", names(exb$shap_explain_row$plots))))

}


doSuite("Explanation Tests", makeSuite(
   varimp_test
   , explanation_test_single_model_regression
   , explanation_test_automl_regression
   , explanation_test_list_of_models_regression
   , explanation_test_single_model_binomial_classification
   , explanation_test_automl_binomial_classification
   , explanation_test_list_of_models_binomial_classification
   , explanation_test_single_model_multinomial_classification
   , explanation_test_automl_multinomial_classification
   , explanation_test_list_of_models_multinomial_classification
   , learning_curve_plot_test_of_models_not_included_in_automl
   , explanation_test_timeseries
   , explanation_test_automl_pareto_front
   , explanation_test_grid_pareto_front
   , explanation_test_some_dataframe_pareto_front
   , pareto_front_corner_cases_test
   , fairness_plots_test
   , shap_plots_work_with_background_frame_test
))
