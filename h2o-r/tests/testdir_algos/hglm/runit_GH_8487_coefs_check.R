setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Build HGLM Model and check against models from model outputs.
##

test.HGLMData1 <- function() {
  tol = 1e-4
  h2odata <- h2o.importFile(locate("smalldata/hglm_test/gaussian_0GC_allRC_2enum2numeric_2p0noise_p5T_wIntercept.gz"))
  browser()
  yresp <- "response"
  random_columns <- c("C2", "C3", "C10", "C20")
  group_column <- "C1"
  
  
 }

doTest("Check HGLM model building and coefficient retrievea.", test.HGLMData1)


