setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_glm_tweedies <- function() {
    require(statmod)
    require(tweedie)
    if (requireNamespace("tweedie")) {
        num_rows <- 1000
        num_cols <- 5
        f1 <- random_dataset_real_only(num_rows, num_cols) # generate dataset containing the predictors.
        f1R <- as.data.frame(h2o.abs(f1))
        weights <- c(0.1, 0.2, 0.3, 0.4, 0.5, 1) # weights to generate the mean
        mu <- generate_mean(f1R, num_rows, num_cols, weights)
        pow <- c(1.1) # variance power range
        phi <- c(1) # dispersion factor range
        y <- "resp" # response column
        x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")
        for (ind in c(1:length(pow))) { # generate dataset with each variance power and dispersion factor
            trainF <- generate_dataset(f1R, num_rows, num_cols, pow[ind], phi[ind], mu)
            print(paste("Compare H2O, R GLM model coefficients and standard error for var_power=", pow[ind], "link_power=", 1 - pow[ind], sep = " "))
            compareH2ORGLM(pow[ind], 1 - pow[ind], x, y, trainF, as.data.frame(trainF), phi[ind])
        }
    } else {
        print("test_glm_tweedies is skipped. Need to install tweedie package.")
    }
}

generate_dataset <- function(f1R, numRows, numCols, pow, phi, mu) {
    resp <- tweedie::rtweedie(numRows, xi = pow, mu, phi, power = pow)
    f1h2o <- as.h2o(f1R)
    resph2o <- as.h2o(as.data.frame(resp))
    finalFrame <- h2o.cbind(f1h2o, resph2o)
    return(finalFrame)
}

generate_mean <- function(f1R, numRows, numCols, weights) {
    y <- c(1:numRows)
    for (rowIndex in c(1:numRows)) {
        tempResp = 0.0
        for (colIndex in c(1:numCols)) {
            tempResp = tempResp + weights[colIndex] * f1R[rowIndex, colIndex]
        }
        y[rowIndex] = tempResp
    }
    return(y)
}

compareH2ORGLM <-
    function(vpower, lpower, x, y, hdf, df, truedisp, tolerance = 2e-4) {
        print("Define formula for R")
        formula <- (df[, "resp"] ~ .)
        rmodel <- glm(
            formula = formula,
            data = df[, x],
            family = tweedie(var.power = vpower, link.power =
                lpower),
            na.action = na.omit
        )
        rAIC <- AICtweedie(rmodel)
        h2omodel <-
            h2o.glm(
                x = x,
                y = y,
                training_frame = hdf,
                family = "tweedie",
                link = "tweedie",
                tweedie_variance_power = vpower,
                tweedie_link_power = lpower,
                alpha = 0.5,
                lambda = 0,
                nfolds = 0,
                compute_p_values = TRUE,
                calc_like = TRUE,
                fix_tweedie_variance_power = TRUE
            )
        h2oAIC <- h2o.aic(h2omodel)
        print("Comparing H2O and R GLM model AIC.")
        print("R AIC")
        print(rAIC)
        print("h2o model AIC")
        print(h2oAIC)
        expect_true(abs(rAIC - h2oAIC) / h2oAIC < 1e-2)
    }

doTest("Comparison of H2O to R TWEEDIE family AIC with tweedie dataset", test_glm_tweedies)
