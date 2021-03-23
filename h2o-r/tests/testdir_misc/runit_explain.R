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
    expect_ggplot(h2o.pd_plot(gbm, train, col))
  }

  # test ice plot
  for (col in cols_to_test) {
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
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

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
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

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
  gbm <- h2o.gbm(y=y, training_frame = train, model_id = "my_awesome_model")
  models <- c(models, gbm)

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

  # HGLM
  h2odata <- h2o.uploadFile(locate("smalldata/glm_test/semiconductor.csv"))
  yresp <- "y"
  xlist <- c("x1", "x3", "x5", "x6")
  z <- c(1)
  h2odata$Device <- h2o.asfactor(h2odata$Device)
  hglm_model <- h2o.glm(x = xlist,
                        y = yresp,
                        family = "gaussian",
                        rand_family = c("gaussian"),
                        rand_link = c("identity"),
                        training_frame = h2odata,
                        HGLM = TRUE,
                        random_columns = z,
                        calc_like = TRUE)
  expect_ggplot(h2o.learning_curve_plot(hglm_model))

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

  # IsolationForest
  if_model <- h2o.isolationForest(training_frame = prostate,
                                  sample_rate = 0.1,
                                  max_depth = 5,
                                  ntrees = 5)
  expect_ggplot(h2o.learning_curve_plot(if_model))
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
  , learning_curve_plot_test_of_models_not_included_in_automl
))
