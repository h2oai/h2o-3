setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure gam will run without predictor with just gam columns.
test.model.gam.null.predictors <- function() {
    data <- h2o.importFile(path = locate("smalldata/gam_test/gamGaussian1Col.csv"))
    gam_model <- h2o.gam(y = "response", gam_columns = c("C1"), training_frame = data, family = "gaussian")
    gam_model2 <- h2o.gam(x = c(), y = "response", gam_columns = c("C1"), training_frame = data, family = "gaussian")
    coeff1 <- gam_model@model$coefficients
    coeff2 <- gam_model2@model$coefficients
    
    # coefficients from both models should be the same
    compareResult <- coeff1==coeff2
    expect_equal(sum(compareResult), length(coeff1))
    
    # make sure feature name C1 not in coefficient names
    coeff_names <- gam_model@model$names
    expect_true(!("C1" %in% coeff_names))
    
}

doTest("General Additive Model test null predictor with Gaussian family", test.model.gam.null.predictors)
