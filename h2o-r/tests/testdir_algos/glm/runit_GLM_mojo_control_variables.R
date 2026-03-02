setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

FAMILIES <- list(
  list(family = "gaussian", data = "regression"),
  list(family = "binomial", data = "binomial"),
  list(family = "tweedie",  data = "regression",
       extra = list(tweedie_variance_power = 1.5, tweedie_link_power = -0.5))
)

make_data <- function(type, three_way = FALSE) {
  if (type == "regression") {
    train <- h2o.createFrame(rows = 5000, cols = 10, factors = 10, has_response = TRUE,
                             response_factors = 1, missing_fraction = 0, seed = 1234)
    train$response <- abs(train$response) + 0.01
  } else if (type == "binomial") {
    train <- h2o.createFrame(rows = 5000, cols = 10, factors = 10, has_response = TRUE,
                             response_factors = 2, missing_fraction = 0, seed = 1234)
  }
  if (three_way) {
    splits <- h2o.splitFrame(train, ratios = c(0.6, 0.2), seed = 1234)
    list(train = splits[[1]], valid = splits[[2]], test = splits[[3]])
  } else {
    splits <- h2o.splitFrame(train, ratios = 0.8, seed = 1234)
    list(train = splits[[1]], test = splits[[2]])
  }
}

check_mojo <- function(model, test, pred_h2o, label) {
  mojo_path <- h2o.save_mojo(model, path = tempdir(), force = TRUE)
  mojo_model <- h2o.import_mojo(mojo_path)
  pred_mojo <- h2o.predict(mojo_model, test[, setdiff(names(test), "response")])
  common_cols <- intersect(names(pred_h2o), names(pred_mojo))
  prob_cols <- intersect(common_cols, c("p0", "p1"))
  cols <- if (length(prob_cols) > 0) prob_cols else common_cols
  max_diff <- max(sapply(cols, function(c) {
    as.numeric(max(abs(as.numeric(pred_h2o[, c]) - as.numeric(pred_mojo[, c]))))
  }))
  expect_true(max_diff < 1e-6, info = sprintf("%s: MOJO max diff = %e", label, max_diff))
}

run_family_test <- function(spec, standardize) {
  Log.info(sprintf("Testing %s MOJO with control_variables, standardize=%s", spec$family, standardize))
  d <- make_data(spec$data)

  args <- c(list(
    x = setdiff(names(d$train), "response"), y = "response", training_frame = d$train,
    family = spec$family, lambda = 0, alpha = 0.001,
    standardize = standardize, control_variables = c("C1", "C2")
  ), spec$extra)
  model <- do.call(h2o.glm, args)

  # h2o.assign gives prediction frames stable DKV keys to prevent eviction
  pred_restricted <- h2o.assign(h2o.predict(model, d$test), "pred_restricted_tmp")
  unrestricted <- h2o.make_unrestricted_glm_model(model)
  pred_unrestricted <- h2o.assign(h2o.predict(unrestricted, d$test), "pred_unrestricted_tmp")

  col <- if ("p0" %in% names(pred_restricted)) "p0" else "predict"
  max_diff <- as.numeric(max(abs(as.numeric(pred_restricted[, col]) - as.numeric(pred_unrestricted[, col]))))
  label <- sprintf("%s (standardize=%s)", spec$family, standardize)
  expect_true(max_diff > 1e-10,
    info = sprintf("%s: restricted vs unrestricted max diff = %e", label, max_diff))

  check_mojo(model, d$test, pred_restricted, paste(label, "restricted"))
  check_mojo(unrestricted, d$test, pred_unrestricted, paste(label, "unrestricted"))
}

run_validation_frame_test <- function(spec, standardize) {
  Log.info(sprintf("Testing %s MOJO with control_variables and validation frame, standardize=%s",
                   spec$family, standardize))
  d <- make_data(spec$data, three_way = TRUE)

  args <- c(list(
    x = setdiff(names(d$train), "response"), y = "response",
    training_frame = d$train, validation_frame = d$valid,
    family = spec$family, lambda = 0, alpha = 0.001,
    standardize = standardize, control_variables = c("C1", "C2"),
    generate_scoring_history = TRUE
  ), spec$extra)
  model <- do.call(h2o.glm, args)

  pred_restricted <- h2o.assign(h2o.predict(model, d$test), "pred_restricted_valid_tmp")
  unrestricted <- h2o.make_unrestricted_glm_model(model)
  pred_unrestricted <- h2o.assign(h2o.predict(unrestricted, d$test), "pred_unrestricted_valid_tmp")

  col <- if ("p0" %in% names(pred_restricted)) "p0" else "predict"
  max_diff <- as.numeric(max(abs(as.numeric(pred_restricted[, col]) - as.numeric(pred_unrestricted[, col]))))
  label <- sprintf("%s (standardize=%s, valid)", spec$family, standardize)
  expect_true(max_diff > 1e-10,
    info = sprintf("%s: restricted vs unrestricted max diff = %e", label, max_diff))

  check_mojo(model, d$test, pred_restricted, paste(label, "restricted"))
  check_mojo(unrestricted, d$test, pred_unrestricted, paste(label, "unrestricted"))
}

test.glm_mojo_control_variables <- function() {
  for (spec in FAMILIES) {
    for (std in c(FALSE, TRUE)) {
      run_family_test(spec, std)
    }
  }
  # Validation frame exercises a separate scoring code path for control variables
  for (spec in FAMILIES) {
    run_validation_frame_test(spec, FALSE)
  }
}

doSuite("GLM MOJO control variables across families", makeSuite(test.glm_mojo_control_variables))
