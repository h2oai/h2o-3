setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


glm_control_variables_cv <- function() {
    # 26-row binomial frame; offset column present but NOT passed as a predictor.
    train <- as.h2o(data.frame(
        x1     = factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0)),
        x2     = factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0)),
        offset = c(.1,.2,.2,.2,.1,0,0,.2,.3,.5,.3,.4,.8,.4,.4,.5,0,0,.5,.1,0,0,.1,0,.1,0),
        y      = factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
    ))

    cv_seed <- 42

    # Training must succeed with control_variables set + nfolds=3.
    glm_ctrl <- h2o.glm(
        x = c("x1", "x2"),
        y = "y",
        training_frame = train,
        family = "binomial",
        alpha = 0,
        lambda = 0,
        control_variables = c("x1"),
        nfolds = 3,
        seed = cv_seed
    )
    expect_false(is.null(glm_ctrl), info = "Model must train without error")
    expect_false(is.null(glm_ctrl@model$cross_validation_metrics),
                 info = "CV metrics must be populated")

    # CV residual deviance must differ from the no-control-variables baseline.
    glm_baseline <- h2o.glm(
        x = c("x1", "x2"),
        y = "y",
        training_frame = train,
        family = "binomial",
        alpha = 0,
        lambda = 0,
        nfolds = 3,
        seed = cv_seed
    )
    dev_ctrl     <- h2o.residual_deviance(glm_ctrl,     xval = TRUE)
    dev_baseline <- h2o.residual_deviance(glm_baseline, xval = TRUE)
    expect_true(abs(dev_ctrl - dev_baseline) > 1e-10,
                info = paste("CV residual deviance must differ between control_variables set",
                             sprintf("(%.6f) and unset (%.6f)", dev_ctrl, dev_baseline)))
}


doTest("GLM: control_variables works with cross-validation (GH-16681)", glm_control_variables_cv)
