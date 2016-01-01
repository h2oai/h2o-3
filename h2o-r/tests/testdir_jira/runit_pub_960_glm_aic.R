setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing AIC value for GLM families gamma and tweedie
# Test for JIRA PUB-907
# 'AIC Calculation for GLM Gamma & Tweedie'
##






test <- function() {

  print("Read prostate data into R.")
  prostate.data <-  h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"), destination_frame="prostate.data")

  print("Set variables for h2o.")
  myY = "DPROS"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","GLEASON")

  print("Testing for family: GAMMA")
  model.h2o.Gamma.inverse <- h2o.glm(x=myX, y=myY, training_frame=prostate.data, family="gamma", link="inverse", alpha=1, lambda_search=T,
                                     # variable_importance=TRUE,
                                     nfolds=0, standardize = TRUE)
  print(model.h2o.Gamma.inverse)      #AIC is NaN

  print("Testing for family: TWEEDIE")
  model.h2o.tweedie <- h2o.glm(x=myX, y=myY, training_frame=prostate.data, family="tweedie", link="tweedie", alpha=1, lambda_search=T,
                                     # variable_importance=TRUE,
                                     nfolds=0, standardize = TRUE)
  print(model.h2o.tweedie)      #AIC is NaN

  
}

h2oTest.doTest("Testing AIC value for GLM families gamma and tweedie", test)
