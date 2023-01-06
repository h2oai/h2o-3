setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

get_data <- function(dispersion, link = "log", nrows = 1000, ncols = 5) {
    cat("Generating data with theta=", dispersion," link=", link, " nrows=", nrows, " ncols=", ncols, "...\n", sep="")
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

    list(intercept = intercept, coefs = coefs, data = df, dispersion = dispersion)
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
    tolerance <- 1.05 # 5%

    rglm <- tryCatch(MASS::glm.nb(result ~ ., data), error = function(e) NULL)
    if (is.null(rglm)) return()
    rdiff <- abs(coefficients(rglm) - c(intercept, coefs))
    rdiff_wo_intercept_mean <- mean(rdiff[-1])
    rdiff_mean <- mean(rdiff)

    hdata <- as.h2o(data)
    hglm <- h2o.glm(y = "result", training_frame = hdata, family = "negativebinomial", link = "log", dispersion_parameter_method = "ml", compute_p_values = TRUE)
    hdiff <- abs(hglm@model$coefficients_table$coefficients - c(intercept, coefs))

    hdiff_wo_intercept_mean <- mean(hdiff[-1])
    hdiff_mean <- mean(hdiff)

    cat(" r likelihood: ", logLik(rglm), "; h2o likelihood: ", get_h2o_likelihood(hglm, hdata, data), "\n",
        "r theta: ", rglm$theta, "; h2o theta: ", hglm@params$actual$theta, "; h2o est. dispersion: ", hglm@model$dispersion, "; actual dispersion: ", dispersion, "\n",
        "r coef diff: ", rdiff_mean, "; h2o coef diff: ", hdiff_mean, "; coef_diff: ", mean(abs(hglm@model$coefficients_table$coefficients - coefficients(rglm))), "\n")


    hloglik <- get_h2o_likelihood(hglm, hdata, data)
    rloglik <- logLik(rglm)
    expect_true(hloglik >=  rloglik - abs((tolerance - 1) * rloglik))

    expect_true(abs(hglm@model$dispersion - dispersion) <= tolerance * abs(dispersion - rglm$theta))
    expect_true(abs(MASS::theta.ml(data$result, unlist(as.list(predict(hglm, hdata)[["predict"]])), limit = 10000) - hglm@model$dispersion) <= 1e-6)

    expect_true(hdiff_wo_intercept_mean <= tolerance * rdiff_wo_intercept_mean)
    expect_true(hdiff_mean <= tolerance * rdiff_mean)
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
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0, 0.001)))
}

test_fuzz0p001 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0.001, 0.1)))
}

test_fuzz0p1 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 0.1, 1)))
}

test_fuzz1 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 1, 10)))
}

test_fuzz10 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 10, 100)))
}

test_fuzz100 <- function() {
  for (i in seq_len(10)) helper_test_glm(get_data(runif(1, 100, 1000)))
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