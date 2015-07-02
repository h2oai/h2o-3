setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test PCA on prostate.csv
test.pca.prostate <- function(conn) {
  Log.info("Importing prostate.csv data...\n")
  prostate.hex <- h2o.uploadFile(conn, locate("smalldata/logreg/prostate.csv"))
  
  Log.info("Converting CAPSULE, RACE, DPROS and DCAPS columns to factors")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  prostate.hex$DPROS <- as.factor(prostate.hex$DPROS)
  prostate.hex$DCAPS <- as.factor(prostate.hex$DCAPS)
  prostate.sum <- summary(prostate.hex)
  print(prostate.sum)
  
  Log.info("PCA on columns 3 to 9 with k = 3, transform = 'STANDARDIZE', pca_method = 'Power'")
  fitPCA <- h2o.prcomp(training_frame = prostate.hex, x = 3:9, k = 3, transform = "STANDARDIZE", pca_method = "Power")
  pred1 <- predict(fitPCA, prostate.hex)
  pred2 <- h2o.getFrame(fitPCA@model$loading_key$name)
  
  Log.info("Compare dimensions of projection and loading matrix")
  Log.info("Projection matrix:\n"); print(head(pred1))
  Log.info("Loading matrix:\n"); print(head(pred2))
  expect_equal(dim(pred1), dim(pred2))
  
  testEnd()
}

doTest("PCA Test: Prostate Data", test.pca.prostate)