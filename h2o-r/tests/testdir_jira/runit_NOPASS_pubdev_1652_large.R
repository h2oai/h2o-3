setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1652 <- function() {
  h2oTest.logInfo("Importing allyears2k.zip data...")
  air.hex <- h2o.importFile(h2oTest.locate("smalldata/airlines/allyears2k.zip"))
  print(summary(air.hex))
  
  my_seed <- 1436311869164163000
  ignore_cols <- c(5, 7, 12, 14, 15, 16, 20, 21, 23, 25, 26, 27, 28, 29)
  x_cols <- setdiff(1:ncol(air.hex), ignore_cols)
  
  h2oTest.logInfo(paste("Running H2O PCA with seed =", my_seed, "on columns x =", paste(x_cols, collapse = ", ")))
  fitH2O <- h2o.prcomp(training_frame = air.hex, x = x_cols, k = 1, transform = "STANDARDIZE", pca_method = "GramSVD", max_iterations = 1000, use_all_factor_levels = FALSE, seed = seed)
  print(fitH2O@model)
  
  
}

h2oTest.doTest("PUBDEV-1652: PCA Core Dump", test.pubdev.1652)
