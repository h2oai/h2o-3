setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Comparison of H2O to R with varying link functions for the TWEEDIE family on prostate dataset
# Link functions: tweedie (canonical link)
##





test_linkFunctions <- function() {

  # Use prostate_complete to test tweedie in R glm vs h2o.glm
  # Note that the outcome in this dataset has a bernoulli distribution
  require(statmod)

  print("Read in prostate data.")
  hdf <- h2o.uploadFile("../../../../smalldata/prostate/prostate_complete.csv.zip", destination_frame = "hdf")
  df <- as.data.frame(hdf)

  print("Testing for family: TWEEDIE")
  print("Set variables for h2o.")
  y <- "CAPSULE"
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  print("Define formula for R")
  formula <- (df[,"CAPSULE"]~.)

  print("Create models with canonical link: TWEEDIE")
  model.h2o.tweedie.tweedie <- h2o.glm(x = x, y = y,
                                       training_frame = hdf,
                                       family = "tweedie",
                                       #link = "family_default",
                                       link = "tweedie",
                                       alpha = 0.5,
                                       lambda = 0,
                                       nfolds = 0)
  model.R.tweedie.tweedie <- glm(formula = formula,
                                 #data = df[,4:10],
                                 data = df[,x],
                                 family = "tweedie",
                                 na.action = na.omit)

  print("Compare model deviances for link function tweedie")
  deviance.h2o.tweedie <- model.h2o.tweedie.tweedie@model$training_metrics@metrics$residual_deviance / model.h2o.tweedie.tweedie@model$training_metrics@metrics$null_deviance
  deviance.R.tweedie <- deviance(model.R.tweedie.tweedie) / model.h2o.tweedie.tweedie@model$training_metrics@metrics$null_deviance
  difference <- deviance.R.tweedie - deviance.h2o.tweedie
  print(cat("Deviance in H2O: ", deviance.h2o.tweedie))
  print(cat("Deviance in R: ", deviance.R.tweedie))
  if (difference > 0.01) {
    checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
  }

  
}

h2oTest.doTest("Comparison of H2O to R with varying link functions for the TWEEDIE family", test_linkFunctions)








