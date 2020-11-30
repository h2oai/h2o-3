setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# testing the newly added varimp for GLM
glmVarimpCheck <- function() {
  # check for binomial
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/benign.csv"), destination_frame="benignFV.hex")
  maxX <- 11
  Y <- 4
  X   <- 3:maxX
  X   <- X[ X != Y ] 
  
  Log.info("Checking varimp for GLM Binomial")
  buildModelVarimpCheck("binomial",bhexFV,X,Y)
  
  # check for multinomial
  Log.info("Checking varimp for GLM Multinomial")
  buildModelVarimpCheck("multinomial", as.h2o(iris), x_indices=c(1,2,3,4), y_index=5)

  # check regression
  h2o.data = h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")    
  myY = "GLEASON"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Checking varimp for GLM Gaussian")
  buildModelVarimpCheck("gaussian", h2o.data, x_indices=myX, y_index=myY)
}

buildModelVarimpCheck <- function(family, training_frame, x_indices, y_index) {
  model <- h2o.glm(y=y_index, x=x_indices, training_frame=training_frame, family=family)
  varimp <- h2o.varimp(model)[2][[1]]
  standardizedCoeff = model@model$standardized_coefficient_magnitudes[2][[1]]
  for (index in 1:length(varimp)) {
    expect_true(abs(varimp[index]-standardizedCoeff[index])<1e-6, "varimp calculation is incorrect.")
  }
  h2o.varimp_plot(model)
}

doTest("GLM: Check generical variable importance values", glmVarimpCheck)
