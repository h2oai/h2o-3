setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)
options(warn=-1)


testProstate <- function() {
    prostate_train = h2o.importFile(locate("smalldata/extdata/prostate.csv"))
    prostate_train["AGE"] <-  h2o.asfactor(prostate_train["AGE"])

    myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
    lowerbound <- c(0.1, -0.5, -0.3, -0.4, -0.2, -0.5, -0.5)#rep(-1, times = length(myX))
    upperbound <- c(0.5, 0.5, 0.3, 0.4, 0.5, 0.5, 0.5)#rep(1, times = length(myX))
    betaConstraints <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)

    glm <- h2o.glm(x = myX, y = "CAPSULE", training_frame = prostate_train, family = "gaussian", alpha = 0,
    beta_constraints = betaConstraints)

    glm@model$coefficients_table$coefficients

    for (i in 1:length(glm@model$coefficients_table$coefficients)) {
        for (j in 1:length(betaConstraints$names)) {
            if (grepl(paste('^',betaConstraints$names[j], sep=""), glm@model$coefficients_table$names[i])) {
                expect_true(glm@model$coefficients_table$coefficients[i] >= betaConstraints$lower_bounds[j])
                expect_true(glm@model$coefficients_table$coefficients[i] <= betaConstraints$upper_bounds[j])
            }
        }   
    }
}

doTest("GLM: Allow beta constraints for categorical column names", testProstate)
