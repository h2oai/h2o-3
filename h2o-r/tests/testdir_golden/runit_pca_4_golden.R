


test.australia.golden <- function() {
  Log.info("Importing AustraliaCoast.csv data...") 
  australiaR <- read.csv(locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australiaH2O <- h2o.uploadFile(locate("smalldata/pca_test/AustraliaCoast.csv"), destination_frame = "australiaH2O")
  
  k_test <- sort(sample(1:8,3))
  for(k in k_test) {
    Log.info(paste("Compare with PCA when k = ", k, ", transform = 'NONE'", sep = ""))
    fitR <- prcomp(australiaR, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'NONE', max_iterations = 2000)
    checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    Log.info(paste("Compare with PCA when k = ", k, ", transform = 'DEMEAN'", sep = ""))
    fitR <- prcomp(australiaR, center = TRUE, scale. = FALSE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'DEMEAN', max_iterations = 2000)
    checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    Log.info(paste("Compare with PCA when k = ", k, ", transform = 'DESCALE'", sep = ""))
    fitR <- prcomp(australiaR, center = FALSE, scale. = apply(australiaR, 2, sd, na.rm = TRUE))
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'DESCALE', max_iterations = 2000)
    checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    Log.info(paste("Compare with PCA when k = ", k, ", transform = 'STANDARDIZE'", sep = ""))
    fitR <- prcomp(australiaR, center = TRUE, scale. = TRUE)
    fitH2O <- h2o.prcomp(australiaH2O, k = k, transform = 'STANDARDIZE', max_iterations = 2000)
    checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  }
  
}

doTest("PCA Golden Test: AustraliaCoast with Variable K", test.australia.golden)
