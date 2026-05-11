setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


glm_remove_offset_cv <- function() {
    # 26-row binomial frame with non-zero offset values
    train <- as.h2o(data.frame(
        x1     = factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0)),
        x2     = factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0)),
        offset = c(.1,.2,.2,.2,.1,0,0,.2,.3,.5,.3,.4,.8,.4,.4,.5,0,0,.5,.1,0,0,.1,0,.1,0),
        y      = factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
    ))
    
    cv_seed <- 42
    
    # Training must succeed with remove_offset_effects=TRUE + nfolds=3
    glm_roe <- h2o.glm(
        x = c("x1", "x2"),
        y = "y",
        training_frame = train,
        offset_column = "offset",
        family = "binomial",
        alpha = 0,
        lambda = 0,
        remove_offset_effects = TRUE,
        nfolds = 3,
        seed = cv_seed
    )
    expect_false(is.null(glm_roe), info = "Model must train without error")
    expect_false(is.null(glm_roe@model$cross_validation_metrics),
                 info = "CV metrics must be populated")

    # CV residual deviance must differ from the offset-included baseline
    glm_baseline <- h2o.glm(
        x = c("x1", "x2"),
        y = "y",
        training_frame = train,
        offset_column = "offset",
        family = "binomial",
        alpha = 0,
        lambda = 0,
        remove_offset_effects = FALSE,
        nfolds = 3,
        seed = cv_seed
    )
    dev_roe      <- h2o.residual_deviance(glm_roe,      xval = TRUE)
    dev_baseline <- h2o.residual_deviance(glm_baseline, xval = TRUE)
    expect_true(abs(dev_roe - dev_baseline) > 1e-10,
                info = paste("CV residual deviance must differ between remove_offset_effects=TRUE",
                             sprintf("(%.6f) and FALSE (%.6f)", dev_roe, dev_baseline)))

    # With generate_scoring_history=T, deviance_xval and deviance_se must appear
    glm_sh <- h2o.glm(
        x = c("x1", "x2"),
        y = "y",
        training_frame = train,
        offset_column = "offset",
        family = "binomial",
        alpha = 0,
        lambda = 0,
        remove_offset_effects = TRUE,
        nfolds = 3,
        generate_scoring_history = TRUE,
        score_each_iteration = TRUE
    )
    sh <- glm_sh@model$scoring_history
    expect_false(is.null(sh), info = "Scoring history must be present")
    expect_true("deviance_xval" %in% colnames(sh),
                info = "deviance_xval must appear in scoring history")
    expect_true("deviance_se" %in% colnames(sh),
                info = "deviance_se must appear in scoring history")

    # At least one finite, positive deviance_xval value
    xval_vals <- sh[["deviance_xval"]]
    xval_vals <- xval_vals[!is.na(xval_vals)]
    expect_true(length(xval_vals) > 0, info = "deviance_xval must have finite values")
    expect_true(all(xval_vals > 0),    info = "All deviance_xval values must be positive")
}


doTest("GLM: remove_offset_effects=TRUE works with cross-validation (GH-16807)", glm_remove_offset_cv)
