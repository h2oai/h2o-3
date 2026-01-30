setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is used to test that we can obtain glm model coefficients without actually running the GLM model
# building process by setting max_iterations=0.  Coefficient names with and without building the models
# are extracted and compared and they should be the same.
test <- function(h) {
    training_frame <- h2o.importFile(locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    training_frame[,1] <- as.factor(training_frame[,1])
    training_frame[,2] <- as.factor(training_frame[,2])
    training_frame[,3] <- as.factor(training_frame[,3])
    training_frame[,4] <- as.factor(training_frame[,4])
    training_frame[,5] <- as.factor(training_frame[,5])
    training_frame[,6] <- as.factor(training_frame[,6])
    training_frame[,7] <- as.factor(training_frame[,7])
    training_frame[,8] <- as.factor(training_frame[,8])
    training_frame[,9] <- as.factor(training_frame[,9])
    training_frame[,10] <- as.factor(training_frame[,10])
    x=c(1:20)
    model <- h2o.glm(x = x, y=21, training_frame=training_frame, max_iterations=0)
    coeffs <- h2o.coef_names(model)
    
    model2 <- h2o.glm(x = x, y=21, training_frame=training_frame)
    coeffs2 <- model2@model$coefficients_table$names
    coeffs2NoIntercept <- coeffs2[-1]
    
    expect_equal(coeffs, coeffs2NoIntercept)
}

doTest("GLM extract model coefficients", test)
