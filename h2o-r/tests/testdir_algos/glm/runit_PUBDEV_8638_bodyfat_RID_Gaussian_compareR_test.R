setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# purpose of this test is to compare our RID implementation with R.  The comparisons agree to tol of 0.001.
test_RID_gaussian <- function() {
  fat <- h2o.importFile(locate("smalldata/glm_test/bodyfat.csv"))
  bodyfat <- as.data.frame(fat)
  bodyfat1 <- bodyfat[-c(1),]
  fat1 <- as.h2o(bodyfat1)
  lm3 <- glm(pctfat.brozek ~ age+fatfreeweight+neck+density, data=bodyfat)
  dfbetasRlm3 <- dfbetas(lm3)
  glmFull <- h2o.glm(x=c("age", "neck", "fatfreeweight", "density"), y="pctfat.brozek", lambda=0, family="gaussian", 
                     standardize=FALSE, compute_p_values=TRUE, remove_collinear_columns=TRUE, influence="dfbetas",
                     training_frame=fat)
  dfbetasglmFull <- h2o.get_regression_influence_diagnostics(glmFull)
  
  ridFrame <- h2o.cbind(dfbetasglmFull$DFBETA_Intercept, dfbetasglmFull$DFBETA_age, dfbetasglmFull$DFBETA_fatfreeweight, 
                        dfbetasglmFull$DFBETA_neck, dfbetasglmFull$DFBETA_density)
  ridFrame <- as.data.frame(ridFrame)
  colnames(ridFrame) <-  c("(Intercept)", "age", "fatfreeweight", "neck", "density")
  compareFrames(as.h2o(ridFrame), as.h2o(dfbetasRlm3), prob=1, tol=1e-6)
}

doTest("compare GLM regression influence diagnostics in GLM with R for Gaussian", test_RID_gaussian)
