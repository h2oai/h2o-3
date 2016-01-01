setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.betaConstraints.illegalBounds <- function(){

  h2oTest.logInfo("Importing prostate dataset...")
  prostate_h2o <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  h2oTest.logInfo("Create beta constraints frame...")
  lowerbound <- rep(100, times = length(myX))
  upperbound <- rep(0, times = length(myX))
  betaConstraints <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)

  h2oTest.logInfo("Run a Linear Regression with CAPSULE ~ . with illegal bounds beta->[100,0] in H2O...")
  expect_error(h2o.glm(x = myX, y = "CAPSULE", training_frame = prostate_h2o, family = "gaussian", alpha = 0, solver="L_BFGS", beta_constraints = betaConstraints))

  
}

h2oTest.doTest("GLM Test: Beta Constraints Illegal Bounds", test.betaConstraints.illegalBounds)

