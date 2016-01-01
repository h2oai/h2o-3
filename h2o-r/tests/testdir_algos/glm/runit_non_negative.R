setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GLM.nonnegative <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.hex = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), "prostate.hex")
  prostate.sum = summary(prostate.hex)
  print(prostate.sum)  
  prostate.data = read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header = TRUE)    
  myY = 2   
  myX = 3:9  
  h2oTest.logInfo(cat("H2O GLM (binomial) with parameters:\nX:", myX, "\nY:", myY, "\n"))
  prostate.glm.h2o = h2o.glm(y = myY, x = myX, training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5, non_negative=TRUE)
  print(prostate.glm.h2o)
  coefs = prostate.glm.h2o@model$coefficients        
  print(coefs)
  coefs = coefs[-which(names(coefs)=="Intercept")]
  neg_coefs = coefs[which(coefs < 0)]
  checkTrue(length(neg_coefs)==0, "got negative coefficients")
}
h2oTest.doTest("GLM Test: Prostate", test.GLM.nonnegative)
