setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.poison.golden <- function() {
  h2oTest.logInfo("Importing poison.csv data...") 
  poisonR <- read.csv(h2oTest.locate("smalldata/pca_test/poison.csv"), header = TRUE)
  poisonH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/poison.csv"), destination_frame = "poisonH2O")
  
  k_test <- sort(sample(1:8,3))
  for(k in k_test) {
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'NONE'", sep = ""))
    poisonR.exp <- h2oTest.alignData(poisonR, center = FALSE, scale = FALSE, use_all_factor_levels = FALSE)
    fitR <- prcomp(poisonR.exp, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(poisonH2O, k = k, transform = 'NONE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'DEMEAN'", sep = ""))
    poisonR.exp <- h2oTest.alignData(poisonR, center = TRUE, scale = FALSE, use_all_factor_levels = FALSE)
    fitR <- prcomp(poisonR.exp, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(poisonH2O, k = k, transform = 'DEMEAN', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'DESCALE'", sep = ""))
    poisonR.num <- poisonR[, sapply(poisonR, is.numeric)]
    poisonR.exp <- h2oTest.alignData(poisonR, center = FALSE, scale = apply(poisonR.num, 2, sd, na.rm = TRUE), use_all_factor_levels = FALSE)
    fitR <- prcomp(poisonR.exp, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(poisonH2O, k = k, transform = 'DESCALE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
    
    h2oTest.logInfo(paste("Compare with PCA when k = ", k, ", transform = 'STANDARDIZE'", sep = ""))
    poisonR.exp <- h2oTest.alignData(poisonR, center = TRUE, scale = TRUE, use_all_factor_levels = FALSE)
    fitR <- prcomp(poisonR.exp, center = FALSE, scale. = FALSE)
    fitH2O <- h2o.prcomp(poisonH2O, k = k, transform = 'STANDARDIZE', max_iterations = 2000)
    h2oTest.checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  }
  
}

h2oTest.doTest("PCA Golden Test: Poison with Variable K", test.poison.golden)

