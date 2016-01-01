setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.australia.golden <- function() {
  h2oTest.logInfo("Importing AustraliaCoast.csv data...") 
  australiaR <- read.csv(h2oTest.locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australiaH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/AustraliaCoast.csv"), destination_frame = "australiaH2O")
  
  k_test <- sort(sample(1:8,3))
  for(k in k_test) {
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'NONE'", sep = ""))
    fitR <- prcomp(australiaR, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'NONE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'DEMEAN'", sep = ""))
    fitR <- prcomp(australiaR, center = TRUE, scale. = FALSE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'DEMEAN', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'DESCALE'", sep = ""))
    fitR <- prcomp(australiaR, center = FALSE, scale. = apply(australiaR, 2, sd, na.rm = TRUE))
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'DESCALE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'STANDARDIZE'", sep = ""))
    fitR <- prcomp(australiaR, center = TRUE, scale. = TRUE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'STANDARDIZE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  }
  
}

h2oTest.doTest("PCA Golden Test: AustraliaCoast with Variable K", test.australia.golden)
