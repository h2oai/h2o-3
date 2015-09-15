setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.australia.golden <- function() {
  # Import data: 
  Log.info("Importing AustraliaCoast.csv data...") 
  australiaR <- read.csv(locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australiaH2O <- h2o.uploadFile(locate("smalldata/pca_test/AustraliaCoast.csv"), destination_frame = "australiaH2O")
  
  Log.info("Compare with PCA when center = FALSE, scale. = FALSE")
  fitR <- prcomp(australiaR, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(australiaH2O, k = 8, transform = 'NONE', max_iterations = 2000)
  isFlipped1 <- checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
  Log.info("Compare Projections into PC space")
  predR <- predict(fitR, australiaR)
  predH2O <- predict(fitH2O, australiaH2O)
  Log.info("R Projection:"); print(head(predR))
  Log.info("H2O Projection:"); print(head(predH2O))
  isFlipped2 <- checkSignedCols(as.matrix(predH2O), predR, tolerance = 5e-4)
  expect_equal(isFlipped1, isFlipped2)
  
  testEnd()
}

doTest("PCA Golden Test: AustraliaCoast with Scoring", test.australia.golden)
