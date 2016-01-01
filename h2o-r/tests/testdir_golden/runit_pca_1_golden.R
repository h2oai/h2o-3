setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pcavanilla.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing USArrests.csv data...") 
  arrestsR <- read.csv(h2oTest.locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  h2oTest.logInfo("Compare with PCA when center = TRUE, scale. = TRUE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = TRUE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'STANDARDIZE', max_iterations = 2000)
  h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5) 
  
  h2oTest.logInfo("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'DEMEAN', max_iterations = 2000)
  h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  
}

h2oTest.doTest("PCA Golden Test: USArrests with Transformed Data", test.pcavanilla.golden)
