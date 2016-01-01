setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.australia.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing AustraliaCoast.csv data...") 
  australiaR <- read.csv(h2oTest.locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australiaH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/AustraliaCoast.csv"), destination_frame = "australiaH2O")
  
  h2oTest.logInfo("Compare with PCA when center = FALSE, scale. = FALSE")
  fitR <- prcomp(australiaR, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(australiaH2O, k = 8, transform = 'NONE', max_iterations = 2000)
  isFlipped1 <- h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
  h2oTest.logInfo("Compare Projections into PC space")
  predR <- predict(fitR, australiaR)
  predH2O <- predict(fitH2O, australiaH2O)
  h2oTest.logInfo("R Projection:"); print(head(predR))
  h2oTest.logInfo("H2O Projection:"); print(head(predH2O))
  isFlipped2 <- h2oTest.checkSignedCols(as.matrix(predH2O), predR, tolerance = 5e-4)
  expect_equal(isFlipped1, isFlipped2)
  
  
}

h2oTest.doTest("PCA Golden Test: AustraliaCoast with Scoring", test.australia.golden)
