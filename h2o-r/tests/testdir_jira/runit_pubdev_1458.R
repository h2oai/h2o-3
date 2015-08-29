setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1458 <- function() {
  Log.info("Importing pub_1458.csv data...")
  train.dat <- read.csv(locate("smalldata/jira/pub_1458.csv"), header = TRUE)
  train.hex <- h2o.importFile(locate("smalldata/jira/pub_1458.csv"))
  print(summary(train.hex))
  
  Log.info("H2O R with standardization, drop rows with any NAs")
  train.cmp <- scale(train.dat, center = TRUE, scale = TRUE)
  train.cmp <- train.cmp[complete.cases(train.cmp),]
  fitR <- prcomp(train.cmp, center = FALSE, scale. = FALSE)
  
  nvec <- 10
  Log.info(paste("H2O PCA with k =", nvec, ", transform = 'STANDARDIZE'", sep = ""))
  fitH2O <- h2o.prcomp(train.hex, k = nvec, transform = "STANDARDIZE", max_iterations = 5000)
  checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  testEnd()
}

doTest("PUBDEV-1458: PCA handling of Missing Values", test.pubdev.1458)
