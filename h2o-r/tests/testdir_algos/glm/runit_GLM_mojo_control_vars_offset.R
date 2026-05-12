setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm_mojo_control_vars_offset <- function() {
  # Test GLM MOJO for all combinations of remove_offset_effects and control_variables
  # across binomial, gaussian, and tweedie families.

  h2o.data <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  h2o.data$CAPSULE <- as.factor(h2o.data$CAPSULE)
  h2o.data$RACE <- as.factor(h2o.data$RACE)
  h2o.data$DCAPS <- as.factor(h2o.data$DCAPS)
  h2o.data$DPROS <- as.factor(h2o.data$DPROS)
  h2o.data$offset_col <- h2o.data$AGE / 100.0
  h2o.data$positive_vol <- abs(h2o.data$VOL) + 1

  binomial_x <- c("RACE", "DCAPS", "PSA", "VOL", "DPROS", "GLEASON")
  regression_x <- c("RACE", "DCAPS", "PSA", "DPROS", "GLEASON")

  # Binomial
  for (combo in list(
    list(label = "binomial_baseline", roe = FALSE, cv = NULL),
    list(label = "binomial_roe", roe = TRUE, cv = NULL),
    list(label = "binomial_contr_vars", roe = FALSE, cv = c("PSA")),
    list(label = "binomial_both", roe = TRUE, cv = c("PSA"))
  )) {
    args <- list(family = "binomial", offset_column = "offset_col", lambda = 0)
    if (combo$roe) args$remove_offset_effects <- TRUE
    if (!is.null(combo$cv)) args$control_variables <- combo$cv
    compare_mojo(combo$label, binomial_x, "CAPSULE", h2o.data, args)
  }

  # Gaussian
  for (combo in list(
    list(label = "gaussian_baseline", roe = FALSE, cv = NULL),
    list(label = "gaussian_roe", roe = TRUE, cv = NULL),
    list(label = "gaussian_contr_vars", roe = FALSE, cv = c("PSA")),
    list(label = "gaussian_both", roe = TRUE, cv = c("PSA"))
  )) {
    args <- list(family = "gaussian", offset_column = "offset_col", lambda = 0)
    if (combo$roe) args$remove_offset_effects <- TRUE
    if (!is.null(combo$cv)) args$control_variables <- combo$cv
    compare_mojo(combo$label, regression_x, "VOL", h2o.data, args)
  }

  # Verify that features actually change predictions (gaussian as representative family)
  verify_features_change_predictions("gaussian", regression_x, "VOL", h2o.data,
    list(family = "gaussian", offset_column = "offset_col", lambda = 0))

  # Tweedie
  for (combo in list(
    list(label = "tweedie_baseline", roe = FALSE, cv = NULL),
    list(label = "tweedie_roe", roe = TRUE, cv = NULL),
    list(label = "tweedie_contr_vars", roe = FALSE, cv = c("PSA")),
    list(label = "tweedie_both", roe = TRUE, cv = c("PSA"))
  )) {
    args <- list(family = "tweedie", offset_column = "offset_col", lambda = 0,
                 tweedie_variance_power = 1.5, tweedie_link_power = 0)
    if (combo$roe) args$remove_offset_effects <- TRUE
    if (!is.null(combo$cv)) args$control_variables <- combo$cv
    compare_mojo(combo$label, regression_x, "positive_vol", h2o.data, args)
  }
}

compare_mojo <- function(label, x, y, data, args) {
  Log.info(label)
  model <- do.call(h2o.glm, c(list(x = x, y = y, training_frame = data), args))
  pred_h2o <- h2o.predict(model, data)

  mojo_path <- h2o.save_mojo(model, path = tempdir(), force = TRUE)
  mojo_model <- h2o.import_mojo(mojo_path)
  pred_mojo <- h2o.predict(mojo_model, data)

  compareFrames(pred_h2o, pred_mojo, prob = 1, tolerance = 1e-8)
  Log.info(paste("  PASSED:", label))
  return(pred_h2o)
}

assert_predictions_differ <- function(pred1, pred2, label) {
  col <- if ("p0" %in% colnames(pred1)) "p0" else "predict"
  max_diff <- max(abs(as.data.frame(pred1[[col]]) - as.data.frame(pred2[[col]])))
  if (max_diff <= 1e-10) {
    stop(paste0(label, ": predictions should differ but max diff = ", max_diff))
  }
  Log.info(paste("  DIFFER OK:", label, "max_diff =", max_diff))
}

verify_features_change_predictions <- function(family_label, x, y, data, base_args) {
  pred_base <- compare_mojo(paste0(family_label, "_base_check"), x, y, data, base_args)
  pred_ro   <- compare_mojo(paste0(family_label, "_ro_check"), x, y, data,
    c(base_args, list(remove_offset_effects = TRUE)))
  pred_contr_vars   <- compare_mojo(paste0(family_label, "_contr_vars_check"), x, y, data,
    c(base_args, list(control_variables = c("PSA"))))
  pred_both <- compare_mojo(paste0(family_label, "_both_check"), x, y, data,
    c(base_args, list(remove_offset_effects = TRUE, control_variables = c("PSA"))))

  assert_predictions_differ(pred_base, pred_ro,   paste(family_label, "baseline vs RO"))
  assert_predictions_differ(pred_base, pred_contr_vars,   paste(family_label, "baseline vs ContrVals"))
  assert_predictions_differ(pred_base, pred_both, paste(family_label, "baseline vs RO+ContrVals"))
  assert_predictions_differ(pred_ro,   pred_contr_vars,   paste(family_label, "RO vs ContrVals"))
  assert_predictions_differ(pred_ro,   pred_both, paste(family_label, "RO vs RO+ContrVals"))
  assert_predictions_differ(pred_contr_vars,   pred_both, paste(family_label, "ContrVals vs RO+ContrVals"))
}

doTest("GLM MOJO with remove_offset_effects and control_variables", test.glm_mojo_control_vars_offset)
