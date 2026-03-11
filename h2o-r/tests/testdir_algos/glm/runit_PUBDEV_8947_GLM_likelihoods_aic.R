setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# checking to see that R clients can access aic and likelihood functions
test.glm.aic.likelihood <- function() {
  # checking for Gaussian
  h2o.data = h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")    
  R.data = as.data.frame(as.matrix(h2o.data))
  myY = "GLEASON"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  R.formula = (R.data[,"GLEASON"]~.) 
  model.h2o.gaussian.identity <- h2o.glm(calc_like=TRUE, x=myX, y=myY, training_frame=h2o.data, family="gaussian", link="identity",alpha=0.5, lambda=0, nfolds=0)
  # R toolbox has a bug in calculating AIC and loglikelihood
  # hand derived correct answer is from R calculation and using the correct formula
  nobs <- 192512
  dev <- 157465
  rRank <- 8 + 1 + 1 # coeffs + intercept + sigma
  loglikeR <- -0.5*((nobs-1) + nobs*log(2*pi*dev/(nobs-1)))
  aicR <- -2*loglikeR+2*rRank
  perf <- h2o.performance(model.h2o.gaussian.identity)
  print("GLM Gaussian")
  print("H2O AIC")
  print(h2o.aic(perf))
  print("H2O log likelihood")
  print(h2o.loglikelihood(perf))
  print("R AIC")
  print(aicR)
  print("R loglikelihood")
  print(loglikeR)
  expect_true(abs(h2o.aic(perf)-aicR) < 1e-1)
  expect_true(abs(h2o.loglikelihood(perf)-loglikeR)<1e-1)
  
  r_glm <- glm(GLEASON ~ ID + AGE + RACE + CAPSULE + DCAPS + PSA + VOL + DPROS, data=R.data)
  print("R GLM AIC")
  print(AIC(r_glm))
  expect_true(abs(h2o.aic(perf) - AIC(r_glm)) < 1e-1)
 }

doTest("Testing AIC/likelihood for GLM", test.glm.aic.likelihood)
