setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Comparison of H2O to R for the TWEEDIE family on prostate dataset
# In particular, we want to compare the coefficients and their standard error calculations.
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
  print("Compare H2O, R GLM model coefficients and standard error for var_power=1, link_power=0")
  compareH2ORGLM(1, 0, x, y, hdf, df)
  print("Compare H2O, R GLM model coefficients and standard error for var_power=0, link_power=1")
  compareH2ORGLM(0, 1, x, y, hdf, df)
  hdf$CAPSULE <- hdf$CAPSULE+1 # add 1 to avoid NaN
  df <- as.data.frame(hdf)
  print("Compare H2O, R GLM model coefficients and standard error for var_power=2, link_power=0")
  compareH2ORGLM(2, 0, x, y, hdf, df)
}

compareH2ORGLM<-function(vpower, lpower, x, y, hdf, df, tolerance=5e-6) {
  print("Define formula for R")
  formula <- (df[,"CAPSULE"]~.)
  rmodel <- glm(formula = formula, data = df[,x],
                                 family=tweedie(var.power=vpower,link.power=lpower),na.action = na.omit)
  h2omodel <- h2o.glm(x = x, y = y, training_frame = hdf, family = "tweedie", link = "tweedie", 
                      tweedie_variance_power = vpower, tweedie_link_power = lpower, alpha = 0.5, lambda = 0, 
                      nfolds = 0, compute_p_values=TRUE)
  print("H2O GLM model....")
  print(h2omodel)
  print("R GLM model....")
  print(summary(rmodel))
  
  print("Comparing H2O and R GLM model coefficients....")
  h2oCoeff <- h2omodel@model$coefficients
  rCoeff <- coef(rmodel)
  for (ind in c(1:length(x))) {
    expect_true(abs(h2oCoeff[x[ind]]-rCoeff[x[ind]]) < tolerance, info = paste0(
      "R coefficient: ",
      rCoeff[x[ind]],
      " but h2o Coefficient: ",
      h2oCoeff[x[ind]],
      sep = " "
    ))
  }
  expect_true(abs(h2oCoeff[x[ind]]-rCoeff[x[ind]]) < tolerance, info = paste0(
    "R coefficient: ",
    rCoeff["(Intercept)"],
    " but h2o Coefficient: ",
    h2oCoeff["(Intercept)"],
    sep = " "
  ))
}

doTest("Comparison of H2O to R TWEEDIE family coefficients and standard errors", test_linkFunctions)