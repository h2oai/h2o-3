setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1458 <- function() {
  h2oTest.logInfo("Importing pub_1458.csv data...")
  train.dat <- read.csv(h2oTest.locate("smalldata/jira/pub_1458.csv"), header = TRUE)
  train.hex <- h2o.importFile(h2oTest.locate("smalldata/jira/pub_1458.csv"))
  print(summary(train.hex))
  
  h2oTest.logInfo("H2O R with standardization, drop rows with any NAs")
  train.cmp <- scale(train.dat, center = TRUE, scale = TRUE)
  train.cmp <- train.cmp[complete.cases(train.cmp),]
  fitR <- prcomp(train.cmp, center = FALSE, scale. = FALSE)
  
  nvec <- 10
  h2oTest.logInfo(paste("H2O PCA with k =", nvec, ", transform = 'STANDARDIZE'", sep = ""))
  fitH2O <- h2o.prcomp(train.hex, k = nvec, transform = "STANDARDIZE", max_iterations = 5000)
  h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
}

h2oTest.doTest("PUBDEV-1458: PCA handling of Missing Values", test.pubdev.1458)
