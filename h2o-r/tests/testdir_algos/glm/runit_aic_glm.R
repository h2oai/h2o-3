setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))

source("../../../scripts/h2o-r-test-setup.R")
library(tweedie)
library(statmod)

header <- function(title, level = 0) {
    if (level < 2) {
        cat("\n\n")
    }
    cat(title, "\n")
    cat(paste(rep(c("=", "-", "~")[min(3, level + 1)], nchar(title)), collapse = ""), "\n")
}

.assert_equal <- function(h2o_model, r_model, coef_tolerance = 1e-6, aic_tolerance = 1e-6, dk_params = 0, loglik_tolerance = 1e-6, r_llh) {
    #' @param h2o_model h2o generalized linear model
    #' @param r_model R's glm or tweedie.profile object
    #' @param coef_tolerance max difference for betas
    #' @param aic_tolerance max difference for AIC
    #' @param dk_params Used to include information about other estimated params such as dispersion


    h2o_aic <- h2o.aic(h2o_model)

    if (missing(r_llh)) {
        r_llh <- as.numeric(logLik(r_model))
        if (is.na(r_llh)) {
            # For tweedie models
            r_llh <- as.numeric(logLiktweedie(r_model))
            r_aic <- -2 * logLik(r_model) + 2 * (length(coef(r_model)) + dk_params)
        } else {
            r_aic <- AIC(r_model) + 2 * dk_params
        }
    } else {
        r_aic <- -2 * r_llh + 2 * (length(coef(r_model)) + dk_params)
    }
    # Compare likelihoods
    if (h2o_model@params$actual$calc_like) {
        h2o_likelihood <- h2o.loglikelihood(h2o_model)
        if (abs(h2o_likelihood - r_llh) > loglik_tolerance) {
            cat(sprintf(
                "Likelihood differs by %f; h2o loglikelihood: %f, r loglikelihood: %f\n",
                abs(h2o_likelihood - r_llh), h2o_likelihood, r_llh
            ))
            expect_equal(h2o_likelihood, r_llh)
        } else {
            cat(sprintf("Differences in log likelihoods were smaller than %f.\n", loglik_tolerance))
            expect_true(TRUE)
        }
    }
    # Compare coefficients
    h2o_coefs <- h2o.coef(h2o_model)
    r_coefs <- coef(r_model)
    diff_coef <- FALSE
    for (name in names(h2o_coefs)) {
        r_name <- ifelse(name == "Intercept", "(Intercept)", name)
        if (r_name %in% names(r_coefs)) {
            diff <- abs(h2o_coefs[name] - r_coefs[r_name])
            if (diff > coef_tolerance) {
                diff_coef <- TRUE
                cat(sprintf(
                    "Coefficient '%s' differs by %.6e; h2o: %.6f; R: %.6f\n",
                    name, h2o_coefs[name] - r_coefs[r_name], h2o_coefs[name], r_coefs[r_name]
                ))
            }
        }
    }
    if (!diff_coef) {
        cat(sprintf("Differences in coefficients were smaller than %f.\n", coef_tolerance))
    }

    # Compare AIC
    aic_diff <- abs(h2o_aic - r_aic)
    if (is.na(aic_diff)) {
        cat(sprintf("Missing value h2o=%f, r=%f\n", h2o_aic, r_aic))
    } else if (aic_diff > aic_tolerance) {
        message <- sprintf(
            "H2O's and R's AIC estimates don't match by %.6f. AIC(h2o)=%.6f, AIC(R)=%.6f",
            h2o_aic - r_aic, h2o_aic, r_aic
        )
        cat(message)
        expect_equal(h2o_aic, r_aic)
    } else {
        expect_true(TRUE)
        cat(sprintf(
            "Differences between AIC is smaller than threshold: %.6e. AIC(h2o)=%.6f, AIC(R)=%.6f\n",
            h2o_aic - r_aic, h2o_aic, r_aic
        ))
    }
}

test_glm_aic_binomial_no_regularization <- function() {
    header("Binomial regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate_train.csv"))
    y <- "CAPSULE"
    train_h2o[, y] <- as.factor(train_h2o[, y])
    train_df <- as.data.frame(train_h2o)

    r_glm_no_reg <- glm(CAPSULE ~ ., data = train_df, family = binomial())

    header("Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "binomial", calc_like = TRUE
    )
    .assert_equal(glm_no_reg, r_glm_no_reg)

    header("Don't Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "binomial", calc_like = FALSE
    )
    .assert_equal(glm_no_reg, r_glm_no_reg)
}

test_glm_aic_gamma_no_regularization <- function() {
    header("Gamma regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    y <- "PSA"
    train_df <- as.data.frame(train_h2o)

    header("Calculate Likelihood", 1)

    r_glm_no_reg <- glm(PSA ~ ., data = train_df, family = Gamma(link = "log"))

    # Sadly R's llf does not estimate dispersion so we have to calculate loglikelihood "by hand"
    dispersion <- sum(residuals(r_glm_no_reg, type = "deviance")^2) / r_glm_no_reg$df.residual
    mu <- fitted(r_glm_no_reg)
    n <- length(train_df[[y]])
    shape <- 1 / dispersion
    r_llh <- sum(dgamma(train_df[[y]], shape = shape, scale = mu / shape, log = TRUE))

    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "gamma", calc_like = TRUE, link = "log",
        fix_dispersion_parameter = TRUE, standardize = T,
        init_dispersion_parameter = dispersion
    )
    .assert_equal(glm_no_reg, r_glm_no_reg, coef_tolerance = 1e-3, aic_tolerance = 1e-4, r_llh = r_llh)
}

test_glm_aic_gaussian_no_regularization <- function() {
    header("Gaussian regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    y <- "PSA"
    train_df <- as.data.frame(train_h2o)

    header("Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "gaussian", calc_like = TRUE,
        dispersion_parameter_method = "deviance"
    )

    r_glm_no_reg <- glm(PSA ~ ., data = train_df, family = gaussian())
    # R's AIC includes dispersion estimation so no need to set dk_params
    .assert_equal(glm_no_reg, r_glm_no_reg, aic_tolerance = 0.2, loglik_tolerance = 0.1)

    header("Don't Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "gaussian", calc_like = FALSE
    )
    .assert_equal(glm_no_reg, r_glm_no_reg, aic_tolerance = 1e-5)
}

test_glm_aic_negative_binomial_no_regularization <- function() {
    header("Negative Binomial regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    y <- "GLEASON"
    train_df <- as.data.frame(train_h2o)

    theta <- 34.8

    header("Calculate Likelihood", 1)
    header("Fixed Dispersion", 2)

    glm_no_reg_theta_est <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "negativebinomial", calc_like = TRUE,
        link = "log", theta = theta, fix_dispersion_parameter = TRUE
    )

    r_glm_no_reg <- glm(GLEASON ~ ., data = train_df, family = MASS::negative.binomial(theta))

    .assert_equal(glm_no_reg_theta_est, r_glm_no_reg,
        coef_tolerance = 1e-2, aic_tolerance = 2e-2, loglik_tolerance = 0.01
    )

    header("Estimated Dispersion", 2)


    glm_no_reg_theta_est <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "negativebinomial", calc_like = TRUE,
        link = "log", theta = theta, fix_dispersion_parameter = FALSE
    )
    theta <- glm_no_reg_theta_est@model$dispersion
    r_glm_no_reg <- glm(GLEASON ~ ., data = train_df, family = MASS::negative.binomial(theta))

    .assert_equal(glm_no_reg_theta_est, r_glm_no_reg,
        coef_tolerance = 1e-2, aic_tolerance = 2e-2, loglik_tolerance = 0.01, dk_params = 1
    )
}

test_glm_aic_poisson_no_regularization <- function() {
    header("Poisson regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    y <- "GLEASON"
    train_df <- as.data.frame(train_h2o)

    header("Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "poisson", calc_like = TRUE
    )

    r_glm_no_reg <- glm(GLEASON ~ ., data = train_df, family = poisson())
    .assert_equal(glm_no_reg, r_glm_no_reg)

    header("Don't Calculate Likelihood", 1)
    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "poisson", calc_like = FALSE
    )
    .assert_equal(glm_no_reg, r_glm_no_reg)
}

test_glm_aic_tweedie_no_regularization <- function() {
    header("Tweedie regression")
    train_h2o <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    y <- "PSA"
    train_df <- as.data.frame(train_h2o)

    header("Calculate Likelihood", 1)

    # Fit Tweedie model in R using tweedie package
    r_glm_no_reg <- glm(PSA ~ .,
        data = train_df,
        family = tweedie(var.power = 1.5, link.power = 0)
    )

    tp <- tweedie.profile(PSA ~ .,
        p.vec = 1.5,
        link.power = 0,
        data = train_df,
        phi.method = "mle",
        do.smooth = FALSE,
        verbose = 0
    )
    rdispersion <- tp$phi.max

    r_llh <- sum(log(
        dtweedie(
            y = r_glm_no_reg$y,
            mu = r_glm_no_reg$fitted.values,
            phi = rdispersion,
            power = 1.5
        )
    ))

    glm_no_reg <- h2o.glm(
        y = y, training_frame = train_h2o, lambda = 0,
        family = "tweedie", calc_like = TRUE,
        link = "tweedie", tweedie_variance_power = 1.5,
        tweedie_link_power = 0
    )

    # variance power is given, only dispersion is estimated  dk_params => 1
    .assert_equal(glm_no_reg, r_glm_no_reg, coef_tolerance = 1e-3, aic_tolerance = 1e-4, r_llh = r_llh, dk_params = 1)
}


doSuite(
    "Test AIC implementation",
    makeSuite(
        test_glm_aic_binomial_no_regularization,
        test_glm_aic_gamma_no_regularization,
        test_glm_aic_gaussian_no_regularization,
        test_glm_aic_negative_binomial_no_regularization,
        test_glm_aic_poisson_no_regularization,
        test_glm_aic_tweedie_no_regularization,
    )
)
