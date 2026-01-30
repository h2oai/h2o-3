setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# this test compares the RID generates from R toolbox with our implementation.  I noticed that our customer wants us
# to use special formula from their vendors.  I implemented this one and it turns out the RID per row are off by some
# constant ratio for the whole row.  The R implementation basically copies the Gaussian implementation by actually
# finding the true difference between the coefficients with and without row i and then divided the result by 
# the deviance times the square root of the gram matrix inverse diagonal.
test_RID_binomial_compareR <- function() {
  fat <- h2o.importFile(locate("smalldata/glm_test/bodyfat.csv"))
  bodyfat <- as.data.frame(fat)
  rGlmBinomial <- glm(bmi ~ neck+density+hip, data=bodyfat, family=binomial())
  dfbetasGlmB <- dfbetas(rGlmBinomial)
  hGlmBinomial <- h2o.glm(x=c("neck", "density", "hip"), y="bmi", lambda=0, family="binomial", standardize=FALSE, influence="dfbetas", training_frame=fat)
  dfbetashGlmB <- h2o.get_regression_influence_diagnostics(hGlmBinomial)
  ratioL <- 1.83
  ratioH <- 2.1
  namesH2O <- c("DFBETA_Intercept", "DFBETA_neck", "DFBETA_density", "DFBETA_hip")
  ridH2O <- as.data.frame(dfbetashGlmB)
  for (ind in seq(1, h2o.nrow(fat))) {
    for (colInd in seq(1, ncol(dfbetasGlmB))) { 
      ratio <- dfbetasGlmB[ind, colInd]/ridH2O[ind, namesH2O[colInd]]
      ratioL <- ratio*0.9
      ratioH <- ratio*1.1
      print(ratio)
      expect_true(ratio > ratioL && ratio < ratioH)
    }
  }
}

doTest("compare GLM regression influence diagnostics in GLM with R for binomial", test_RID_binomial_compareR)
