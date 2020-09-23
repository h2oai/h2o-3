setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure cross-validation runs properly in R
test.model.gam.cross.validation <- function() {
    data <- h2o.importFile(path = locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    data$C1 <- h2o.asfactor(data$C1)
    data$C2 <- h2o.asfactor(data$C2)
    data$C3 <- h2o.asfactor(data$C3)
    data$C4 <- h2o.asfactor(data$C4)
    data$C5 <- h2o.asfactor(data$C5)
    data$C6 <- h2o.asfactor(data$C6)
    data$C7 <- h2o.asfactor(data$C7)
    data$C8 <- h2o.asfactor(data$C8)
    data$C9 <- h2o.asfactor(data$C9)
    data$C10 <- h2o.asfactor(data$C10)
    splits = h2o.splitFrame(data, ratios=c(0.8), seed=12345)
    train = splits[[1]]
    valid = splits[[2]]
    gam_valid <- h2o.gam(y = "C21", x = c(1:20), gam_columns = c("C11"), training_frame = train, validation_frame = valid,
    family = "gaussian", nfolds = 5, fold_assignment="modulo")
    gam_no_valid <- h2o.gam(y = "C21", x = c(1:20), gam_columns = c("C11"), training_frame = train, family = "gaussian", 
                            nfolds = 5, fold_assignment="modulo")
    # coefficients from both models should be the same
    compareResult <- gam_valid@model$coefficients==gam_no_valid@model$coefficients
    expect_equal(sum(compareResult), length(gam_valid@model$coefficients))
}

doTest("General Additive Model cross validation test with Gaussian family", test.model.gam.cross.validation)
