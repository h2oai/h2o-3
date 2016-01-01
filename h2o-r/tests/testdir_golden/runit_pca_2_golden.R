setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pcascore.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing USArrests.csv data...") 
  arrestsR <- read.csv(h2oTest.locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  h2oTest.logInfo("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'DEMEAN', max_iterations = 2000)
  isFlipped1 <- h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
  h2oTest.logInfo("Compare Projections into PC space")
  predR <- predict(fitR, arrestsR)
  predH2O <- predict(fitH2O, arrestsH2O)
  h2oTest.logInfo("R Projection:"); print(head(predR))
  h2oTest.logInfo("H2O Projection:"); print(head(predH2O))
  isFlipped2 <- h2oTest.checkSignedCols(as.matrix(predH2O), predR, tolerance = 5e-5)
  expect_equal(isFlipped1, isFlipped2)
  
  
}

h2oTest.doTest("PCA Golden Test: USArrests with Scoring", test.pcascore.golden)
