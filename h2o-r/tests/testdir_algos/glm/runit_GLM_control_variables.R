setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.controlVariables <- function() {
  Log.info("Read in prostate data.")
  h2o.data <- h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame = "h2o.data")

  myY <- "CAPSULE"
  myX <- c("AGE", "RACE", "DCAPS", "PSA", "VOL", "DPROS", "GLEASON")

  Log.info("Build the model")
  model <- h2o.glm(
    x = myX,
    y = myY,
    training_frame = h2o.data,
    family = "binomial",
    link = "logit"
  )

  # print(model)
  res.dev <- model@model$training_metrics@metrics$residual_deviance
  print(res.dev)
  betas <- model@model$coefficients_table$coefficients
  print(betas)

  Log.info("Build the model with control variables")
  model.cont <- h2o.glm(
    x = myX,
    y = myY,
    training_frame = h2o.data,
    family = "binomial",
    link = "logit",
    control_variables = c("PSA", "AGE")
  )

  # print(model.cont)
  res.dev.cont <- model.cont@model$training_metrics@metrics$residual_deviance
  print(res.dev.cont)
  betas.cont <- model.cont@model$coefficients_table$coefficients
  print(betas.cont)
  expect_true(res.dev != res.dev.cont)
}

# Test all regression and binomial distributions

.expand_factors <- function(df) {
  non_numeric_columns <- names(Filter(identity, lapply(df, Negate(is.numeric))))
  factors <- names(Filter(identity, lapply(df, is.factor)))
  stopifnot("only factors are expected to be non numeric" = non_numeric_columns ==
    factors)

  for (fct in factors) {
    lvls <- levels(df[[fct]])
    for (lvl in lvls) {
      df[[sprintf("%s.%s", fct, lvl)]] <- as.numeric(df[[fct]] == lvl)
    }
    df[[fct]] <- NULL
  }
  df
}

.preprocess_control_variables <- function(df, control_variables) {
  factors <- names(Filter(identity, lapply(df, is.factor)))
  for (fct in factors) {
    if (fct %in% control_variables) {
      lvls <- levels(df[[fct]])
      for (lvl in lvls) {
        control_variables <- c(sprintf("%s.%s", fct, lvl), control_variables)
      }
    }
  }
  control_variables
}


.predict_helper <- function(df,
                            coeffs,
                            link_function,
                            control_variables,
                            offset_column) {
  temp_result <- rep_len((if ("Intercept" %in% names(coeffs)) {
    coeffs[["Intercept"]]
  } else {
    0.0
  }), nrow(df))
  for (coef in names(coeffs)) {
    if (coef %in% control_variables || coef == "Intercept") {
      next
    }
    temp_result <- temp_result + coeffs[[coef]] * df[[coef]]
  }
  if (!is.null(offset_column)) {
    link_function(temp_result + offset_column)
  } else {
    link_function(temp_result)
  }
}

.almost_equal <- function(expected, actual) {
  diff <- abs(expected - actual)
  if (max(diff) > 1e-10) {
    message(
      sprintf(
        "Maximum difference %f, average difference %f, median difference %f.",
        max(diff),
        mean(diff),
        median(diff)
      )
    )
  }
  max(diff) < 1e-8
}

.predict_with_and_without_control_variables <- function(y,
                                                        training_frame,
                                                        test_frame,
                                                        control_variables,
                                                        other_args = list(),
                                                        seed = 42) {
  links_for_families <- list(
    "binomial" = "logit",
    "fractional_binomial" = "logit",
    "quasibinomial" = "logit",
    # "multinomial" = "family_default",
    # "ordinal" = "ologit",
    "gaussian" = "identity",
    "poisson" = "log",
    "gamma" = "inverse",
    "tweedie" = "tweedie",
    "negativebinomial" = "log"
  )
  link_fns <- list(
    "identity" = identity,
    "logit" = function(eta) {
      ifelse(eta < 0, 1 / (1 + exp(-eta)), exp(eta) / (1 + exp(eta)))
    },
    "log" = function(eta) {
      exp(eta)
    },
    "inverse" = function(eta) {
      1 / eta
    },
    "tweedie" = function(eta) {
      var_power <- if ("tweedie_variance_power" %in% names(other_args)) {
        other_args[["tweedie_variance_power"]]
      } else {
        0.0
      }
      # documentation says link power should be 1 - var_power but it doesn't seem to be automatically set so using 1 as default
      link_power <- if ("tweedie_link_power" %in% names(other_args)) {
        other_args[["tweedie_link_power"]]
      } else {
        1
      }
      if (link_power != 0) {
        eta^(1 / link_power)
      } else {
        exp(eta)
      }
    }
  )

  model <- do.call(h2o.glm, c(
    list(
      y = y,
      training_frame = training_frame,
      seed = seed
    ),
    other_args
  ))
  coeffs <- as.list(model@model$coefficients)
  df <- .expand_factors(as.data.frame(test_frame))
  control_variables <- .preprocess_control_variables(as.data.frame(test_frame), control_variables)

  offset_column <- if ("offset_column" %in% names(other_args) && !is.null(other_args[["offset_column"]])) {
    df[[other_args[["offset_column"]]]]
  } else {
    NULL
  }

  link <- if ("link" %in% names(other_args)) {
    other_args[["link"]]
  } else {
    "family_default"
  }

  if (link == "family_default") {
    family <- if ("family" %in% names(other_args)) {
      other_args[["family"]]
    } else {
      "AUTO"
    }
    if (family == "AUTO") {
      family <- if (is.factor(training_frame[[y]])) {
        "binomial"
      } else {
        "gaussian"
      }
    }
    link <- links_for_families[[family]]
  }

  link_function <- link_fns[[link]]

  without_control_variables <- .predict_helper(df, coeffs, link_function, NULL, offset_column)
  without_control_variables_h2o <- predict(model, test_frame)
  h2o_res <- as.data.frame(without_control_variables_h2o)
  stopifnot(
    "Predictions without control variables don't match" = .almost_equal(h2o_res[, ncol(h2o_res)], without_control_variables)
  )

  with_control_variables <- .predict_helper(
    df,
    coeffs,
    link_function,
    control_variables = control_variables,
    offset_column = offset_column
  )

  list(
    predictions = with_control_variables,
    unrestricted_predictions = without_control_variables
  )
}


verify_control_variables_implementation <- function(y,
                                                    training_frame,
                                                    test_frame,
                                                    control_variables,
                                                    other_args = list(),
                                                    seed = 42) {
  preds <- .predict_with_and_without_control_variables(
    y = y,
    training_frame = training_frame,
    test_frame = test_frame,
    control_variables = control_variables,
    other_args = other_args,
    seed = seed
  )

  model_constr <- do.call(h2o.glm, c(
    list(
      y = y,
      training_frame = training_frame,
      seed = seed,
      control_variables = control_variables
    ),
    other_args
  ))
  h2o_constr_res <- as.data.frame(predict(model_constr, test_frame))
  h2o_unconstr_res <- as.data.frame(predict(h2o.make_unrestricted_glm_model(model_constr), test_frame))
  
  expect_equal(h2o_constr_res[, ncol(h2o_constr_res)], preds$predictions, info = deparse(other_args))
  expect_equal(h2o_unconstr_res[, ncol(h2o_unconstr_res)], preds$unrestricted_predictions, info = deparse(other_args))
}



.sanity_check <- function(train_df,
                          test_df,
                          continuous_response,
                          positive_continuous_response,
                          whole_positive_number_response,
                          binary_response,
                          control_variables,
                          offset_column) {
  for (off_col in list(offset_column, NULL)) {
    for (standardize in c(TRUE, FALSE)) {
      cat("gaussian", "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
      verify_control_variables_implementation(
        y = continuous_response,
        training_frame = train_df,
        test_frame = test_df,
        control_variables = control_variables,
        other_args = list(
          family = "gaussian",
          standardize = standardize,
          offset_column = off_col
        )
      )

      pos_continuous_families <- c("gamma", "tweedie")
      for (fam in pos_continuous_families) {
        cat(fam, "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
        verify_control_variables_implementation(
          y = positive_continuous_response,
          training_frame = train_df,
          test_frame = test_df,
          control_variables = control_variables,
          other_args = list(
            family = fam,
            standardize = standardize,
            offset_column = off_col
          )
        )
      }

      for (lp in c(0.0, 0.5, 1, 1.5)) {
        if (!is.null(offset_column) && lp %% 1 != 0) {
          next
        }
        cat("tweedie_link_power: ", lp, "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
        verify_control_variables_implementation(
          y = positive_continuous_response,
          training_frame = train_df,
          test_frame = test_df,
          control_variables = control_variables,
          other_args = list(
            family = "tweedie",
            tweedie_link_power = lp,
            standardize = standardize,
            offset_column = off_col
          )
        )
      }

      for (tvp in c(0.0, 1, 2, 0.5, 1.5)) {
        if (!is.null(offset_column) && tvp %% 1 != 0) {
          next
        }
        cat("tweedie_variance_power: ", tvp, "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
        verify_control_variables_implementation(
          y = positive_continuous_response,
          training_frame = train_df,
          test_frame = test_df,
          control_variables = control_variables,
          other_args = list(
            family = "tweedie",
            tweedie_variance_power = tvp,
            standardize = standardize,
            offset_column = off_col
          )
        )
      }

      for (tvp in c(0.0, 0.5, 1, 1.5, 2)) {
        for (lp in c(0.0, 0.5, 1, 1.5)) {
          if (!is.null(offset_column) && (lp %% 1 != 0 || tvp %% 1 != 0)) {
            next
          }
          cat(
            "tweedie_variance_power: ",
            tvp,
            ";\ttweedie_link_power: ",
            lp, "; offset_column: ", off_col, "; standardize: ", standardize, "\n"
          )
          verify_control_variables_implementation(
            y = positive_continuous_response,
            training_frame = train_df,
            test_frame = test_df,
            control_variables = control_variables,
            other_args = list(
              family = "tweedie",
              tweedie_variance_power = tvp,
              tweedie_link_power = lp,
              standardize = standardize,
              offset_column = off_col
            )
          )
        }
      }

      cat("binomial", "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
      verify_control_variables_implementation(
        y = binary_response,
        training_frame = train_df,
        test_frame = test_df,
        control_variables = control_variables,
        other_args = list(family = "binomial", standardize = standardize, offset_column = off_col)
      )
      cat("poisson", "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
      verify_control_variables_implementation(
        y = whole_positive_number_response,
        training_frame = train_df,
        test_frame = test_df,
        control_variables = control_variables,
        other_args = list(
          family = "poisson",
          standardize = standardize,
          offset_column = off_col
        )
      )
      cat("negativebinomial", "; offset_column: ", off_col, "; standardize: ", standardize, "\n")
      verify_control_variables_implementation(
        y = whole_positive_number_response,
        training_frame = train_df,
        test_frame = test_df,
        control_variables = control_variables,
        other_args = list(
          family = "negativebinomial",
          standardize = standardize,
          offset_column = off_col
        )
      )
    }
  }
}

test.control_variables_work_correctly <- function() {
  h2o_iris <- as.h2o(iris)
  h2o_iris[["bin_response"]] <- as.factor(h2o_iris[["Sepal.Length"]] > 5.8)
  h2o_iris[["poisson_response"]] <- round(h2o_iris[["Sepal.Length"]] * 10)
  h2o_iris[["offset_col"]] <- h2o_iris[["Petal.Length"]] / 10

  .sanity_check(
    train_df = h2o_iris,
    test_df = h2o_iris,
    continuous_response = "Sepal.Width",
    positive_continuous_response = "Sepal.Width",
    whole_positive_number_response = "poisson_response",
    binary_response = "bin_response",
    control_variables = c("Petal.Width", "Species"),
    offset_column = "offset_col"
  )
}


doSuite(
  "Comparison of H2O GLM without and with control variables",
  makeSuite(
    test.controlVariables,
    test.control_variables_work_correctly
  )
)
