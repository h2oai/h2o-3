setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.pca.quasar <- function() {
  Log.info("Importing SDSS_quasar.txt.zip data...") 
  quasar.hex <- h2o.importFile(locate("smalldata/pca_test/SDSS_quasar.txt.zip"), header = TRUE)
  quasar.hex <- quasar.hex[,-1]
  print(summary(quasar.hex))
  
  Log.info("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'GramSVD'")
  fitGramSVD <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "GramSVD", use_all_factor_levels = TRUE)
  Log.info("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'Power'")
  fitPower <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "Power", use_all_factor_levels = TRUE)
  Log.info("Run PCA with k = 5, transform = 'STANDARDIZE', pca_method = 'GLRM'")
  fitGLRM <- h2o.prcomp(quasar.hex, k = 5, transform = "STANDARDIZE", max_iterations = 2000, pca_method = "GLRM", use_all_factor_levels = TRUE, seed = 1436)
  
  # Note: GLRM depends immensely on initial X, Y matrices in this case, so changing seed will affect results
  Log.info(paste("Standard deviation with GramSVD:", paste(h2o.sdev(fitGramSVD), collapse = " ")))
  Log.info(paste("Standard deviation with Power  :", paste(h2o.sdev(fitPower), collapse = " ")))
  Log.info(paste("Standard deviation with GLRM   :", paste(h2o.sdev(fitGLRM), collapse = " ")))
  Log.info(paste("GLRM final objective value:", fitGLRM@model$objective))
  
}

doTest("PCA Test: SDSS Quasar with different methods", test.pca.quasar)
