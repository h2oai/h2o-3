setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.pca.quasar <- function() {
  h2oTest.logInfo("Importing SDSS_quasar.txt.zip data...") 
  quasar.hex <- h2o.importFile(h2oTest.locate("smalldata/pca_test/SDSS_quasar.txt.zip"), header = TRUE)
  quasar.hex <- quasar.hex[,-1]
  print(summary(quasar.hex))
  
  h2oTest.logInfo("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'GramSVD'")
  fitGramSVD <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "GramSVD", use_all_factor_levels = TRUE)
  h2oTest.logInfo("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'Power'")
  fitPower <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "Power", use_all_factor_levels = TRUE)
  h2oTest.logInfo("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'GLRM'")
  fitGLRM <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "GLRM", use_all_factor_levels = TRUE, seed = 1436)
  
  # Note: GLRM depends immensely on initial X, Y matrices in this case, so changing seed will affect results
  h2oTest.logInfo(paste("Standard deviation with GramSVD:", paste(h2o.sdev(fitGramSVD), collapse = " ")))
  h2oTest.logInfo(paste("Standard deviation with Power  :", paste(h2o.sdev(fitPower), collapse = " ")))
  h2oTest.logInfo(paste("Standard deviation with GLRM   :", paste(h2o.sdev(fitGLRM), collapse = " ")))
  h2oTest.logInfo(paste("GLRM final objective value:", fitGLRM@model$objective))
  
}

h2oTest.doTest("PCA Test: SDSS Quasar with different methods", test.pca.quasar)
