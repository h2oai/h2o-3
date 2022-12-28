setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

get_data <- function(dispersion, link = "log", nrows = 1000, ncols = 5) {
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

helper_test_glm <- function(data_env) {
    attach(data_env)
    tolerance <- 1.1 # 10%

    rglm <- MASS::glm.nb(result ~ ., data)
    rdiff <- abs(coefficients(rglm) - c(intercept, coefs))
    rdiff_wo_intercept_mean <- mean(rdiff[-1])
    rdiff_mean <- mean(rdiff)

    hdata <- as.h2o(data)
    hglm <- h2o.glm(y = "result", training_frame = hdata, family = "negativebinomial", link = "log", lambda=0, dispersion_parameter_method = "ml")
    hdiff <- abs(hglm@model$coefficients_table$coefficients - c(intercept, coefs))

    hdiff_wo_intercept_mean <- mean(hdiff[-1])
    hdiff_mean <- mean(hdiff)

    expect_true(hdiff_wo_intercept_mean <= tolerance * rdiff_wo_intercept_mean)
    expect_true(hdiff_mean <= tolerance * rdiff_mean)

    expect_true(abs(hglm@model$dispersion - dispersion) <= tolerance * abs(dispersion - rglm$theta))
    #expect_true(abs(MASS::theta.ml(data$result, unlist(as.list(predict(hglm, hdata)[["predict"]])), limit = 10000) - dispersion) <= tolerance * abs(dispersion - rglm$theta))
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


doSuite("Negative Binomial Dispersion Estimation tests",
        makeSuite(
            test_dispersion_01,
            test_dispersion_02,
            test_dispersion_05,
            test_dispersion_1,
            test_dispersion_2,
            test_dispersion_5,
            test_dispersion_10
        ))