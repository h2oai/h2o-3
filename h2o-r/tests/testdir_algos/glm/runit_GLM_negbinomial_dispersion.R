setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

get_data <- function(dispersion, link = "log", nrows = 1000, ncols = 5, seed = 1234) {
    set.seed(seed)
    msg <- paste0("Generated data with theta=", dispersion," link=", link, " nrows=", nrows, " ncols=", ncols," seed=", seed,  "\n")
    df <- data.frame(col0 = rnorm(nrows))
    for (i in seq_len(ncols - 1)) {
        df[[paste0("col", i)]] <- rnorm(nrows)
    }

    coefs <- seq_len(ncols) * c(0.5, -0.5)

    intercept <-  sample(1:1000, 1) / 100


    if (link == "log") {
      lin_combination <- rowSums(mapply(`*`, df, coefs)) + intercept
      df[["result"]] <- MASS::rnegbin(nrows, exp(lin_combination), dispersion)
    } else if (link == "identity") {
      lin_combination <- rowSums(mapply(`*`, df, coefs))
      intercept <- - min(lin_combination)
      lin_combination <- lin_combination + intercept
      df[["result"]] <- MASS::rnegbin(nrows, lin_combination, dispersion)
    }

    list(intercept = intercept, coefs = coefs, data = df, dispersion = 1/dispersion, msg = msg)
}

get_h2o_likelihood <- function(hglm, hdata, data) {
  mu <- unlist(as.list(predict(hglm, hdata)$predict))
  y <- data$result
  theta <- hglm@params$actual$theta
  invTheta <- 1./theta
  sum(lgamma(y+invTheta)-lgamma(invTheta)-lgamma(y+1)+y*log(theta*mu)-(y+invTheta)*log(1+theta*mu))
}


helper_test_glm <- function(data_env) {
    attach(data_env)
    tolerance <- 1.01 # 1.05 == 5% tolerance, 1.005 = 0.5% etc.

    rglm <- tryCatch(MASS::glm.nb(result ~ ., data), error = function(e) NULL)
    if (is.null(rglm)) return()
    rdiff <- abs(coefficients(rglm) - c(intercept, coefs))
    rdiff_mean <- mean(rdiff)

    hdata <- as.h2o(data)
    hglm <- h2o.glm(y = "result", training_frame = hdata, family = "negativebinomial", link = "log", dispersion_parameter_method = "ml", standardize = FALSE)
    hdiff <- abs(hglm@model$coefficients_table$coefficients - c(intercept, coefs))
    hdiff_mean <- mean(hdiff)

    hloglik <- get_h2o_likelihood(hglm, hdata, data)
    rloglik <- logLik(rglm)

    likelihood_test <- hloglik >=  rloglik - abs((tolerance - 1) * rloglik)
    dispersion_test <- abs(hglm@params$actual$theta - dispersion) <= tolerance * abs(dispersion - 1/rglm$theta)
    coef_test <- hdiff_mean <= tolerance * rdiff_mean

    if (!likelihood_test || !dispersion_test || !coef_test) {
        cat(msg, "\n")
        cat("Likelihood test: ", likelihood_test, "; Dispersion test: ", dispersion_test, "; Coefficient test: ", coef_test, "\n")
        cat(" r likelihood: ", logLik(rglm), "; h2o likelihood: ", get_h2o_likelihood(hglm, hdata, data), "\n",
            "r 1/theta: ", 1/rglm$theta, "; h2o theta: ", hglm@params$actual$theta, "; actual dispersion: ", dispersion, "\n",
            "r coef diff: ", rdiff_mean, "; h2o coef diff: ", hdiff_mean, "; coef_diff: ", mean(abs(hglm@model$coefficients_table$coefficients - coefficients(rglm))), "\n")
        print(hglm@model$coefficients_table)
        print(coefficients(rglm))
        print(summary(data$result))
    }

    expect_true(likelihood_test)
    expect_true(dispersion_test)
    expect_true(coef_test)
}

test_dispersion_01 <- function() {
    helper_test_glm(get_data(0.1))
}

test_dispersion_02 <- function() {
    helper_test_glm(get_data(0.2))
}

test_dispersion_05 <- function() {
    helper_test_glm(get_data(0.5))
}

test_dispersion_1 <- function() {
    helper_test_glm(get_data(1))
}

test_dispersion_2 <- function() {
    helper_test_glm(get_data(2))
}

test_dispersion_5 <- function() {
    helper_test_glm(get_data(5))
}

test_dispersion_10 <- function() {
    helper_test_glm(get_data(10))
}

test_fuzz0 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0, 0.001), seed = i))
}

test_fuzz0p001 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0.001, 0.1), seed = i))
}

test_fuzz0p1 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0.1, 1), seed = i))
}

test_fuzz1 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 1, 10), seed = i))
}

test_fuzz10 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 10, 100), seed = i))
}

test_fuzz100 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 100, 1000), seed = i))
}

doSuite("Negative Binomial Dispersion Estimation tests",
        makeSuite(
            test_dispersion_01,
            test_dispersion_02,
            test_dispersion_05,
            test_dispersion_1,
            test_dispersion_2,
            test_dispersion_5,
            test_dispersion_10,
            test_fuzz0,
            test_fuzz0p001,
            test_fuzz0p1,
            test_fuzz1,
            test_fuzz10,
            test_fuzz100
        ))

test_custom <- function() {
  helper_test_glm(get_data(runif(1, 0.001, 0.1), seed = 3))
#   helper_test_glm(get_data(runif(1, 10, 100), seed = 1))
}

#doTest("Used for debugging", test_custom)