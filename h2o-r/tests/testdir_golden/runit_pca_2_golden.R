setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pcascore.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing USArrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  Log.info("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'DEMEAN', max_iterations = 2000)
  isFlipped1 <- checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
  Log.info("Compare Projections into PC space")
  predR <- predict(fitR, arrestsR)
  predH2O <- predict(fitH2O, arrestsH2O)
  Log.info("R Projection:"); print(head(predR))
  Log.info("H2O Projection:"); print(head(predH2O))
  isFlipped2 <- checkSignedCols(as.matrix(predH2O), predR, tolerance = 5e-5)
  expect_equal(isFlipped1, isFlipped2)
  
  testEnd()
}

doTest("PCA Golden Test: USArrests with Scoring", test.pcascore.golden)
