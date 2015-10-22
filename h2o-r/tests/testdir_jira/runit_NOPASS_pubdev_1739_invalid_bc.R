setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.betaConstraints.illegalBounds <- function(){

  Log.info("Importing prostate dataset...")
  prostate_h2o <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
  myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  Log.info("Create beta constraints frame...")
  lowerbound <- rep(100, times = length(myX))
  upperbound <- rep(0, times = length(myX))
  betaConstraints <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)

  Log.info("Run a Linear Regression with CAPSULE ~ . with illegal bounds beta->[100,0] in H2O...")
  expect_error(h2o.glm(x = myX, y = "CAPSULE", training_frame = prostate_h2o, family = "gaussian", alpha = 0, solver="L_BFGS", beta_constraints = betaConstraints))

  
}

doTest("GLM Test: Beta Constraints Illegal Bounds", test.betaConstraints.illegalBounds)

