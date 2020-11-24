setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure gam will build the same model regardless of how gam columns are specified
test.model.gam.dual.modes <- function() {
    train <- h2o.importFile(path = locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    test <- h2o.importFile(path = locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    train$C1 <- h2o.asfactor(train$C1)
    train$C2 <- h2o.asfactor(train$C2)
    test$C1 <- h2o.asfactor(test$C1)
    test$C2 <- h2o.asfactor(test$C2)
    xL <- c("C1", "C2")
    yR = "C21"
    gam_col1 <- list("C11", c("C12", "C13"), c("C14", "C15", "C16"), "C17", "C18")
    gam_col2 <- list(c("C11"), c("C12", "C13"), c("C14", "C15", "C16"), c("C17"), c("C18"))
    bsT <- c(1,1,1,0,0)
    gam_model <- h2o.gam(x = xL, y = yR, gam_columns = gam_col1, training_frame = train, validation_frame = test, 
    family = "gaussian", lambda_search=TRUE)
    gam_model2 <- h2o.gam(x = xL, y = yR, gam_columns = gam_col2, training_frame = train, validation_frame = test, 
    family = "gaussian", lambda_search=TRUE)
    coeff1 <- gam_model@model$coefficients
    coeff2 <- gam_model2@model$coefficients
    
    # coefficients from both models should be the same
    for (ind in c(1:length(coeff1))) {
        temp <- abs(coeff1[[ind]]-coeff2[[ind]])
        expect_true(temp < 1e-6, "coefficient comparison failed.")
    }
    
    # check validation metrics
    expect_true(abs(h2o.mse(gam_model, valid=TRUE)-h2o.mse(gam_model2, valid=TRUE)) < 1e-6)
}

doTest("General Additive Model test dual model specification with Gaussian family", test.model.gam.dual.modes)
