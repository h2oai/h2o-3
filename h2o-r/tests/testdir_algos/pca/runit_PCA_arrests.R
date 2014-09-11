setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.PCA.arrests <- function(conn) {
  Log.info("Importing USArrests.csv data...\n")
  arrests.hex = h2o.uploadFile(conn, locate("smalldata/pca_test/USArrests.csv"), "arrests.hex")
  arrests.sum = summary(arrests.hex)
  print(arrests.sum)
  
  Log.info("H2O PCA on non-standardized USArrests:\n")
  arrests.pca.h2o = h2o.prcomp(arrests.hex, standardize = FALSE)
  print(arrests.pca.h2o)
  arrests.pca = prcomp(USArrests, center = FALSE, scale. = FALSE, retx = TRUE)
  checkPCAModel(arrests.pca.h2o, arrests.pca)
  
  Log.info("H2O PCA on standardized USArrests:\n")
  arrests.pca.h2o.std = h2o.prcomp(arrests.hex, standardize = TRUE)
  print(arrests.pca.h2o.std)
  arrests.pca.std = prcomp(USArrests, center = TRUE, scale. = TRUE, retx = TRUE)
  checkPCAModel(arrests.pca.h2o.std, arrests.pca.std)
  
  testEnd()
}

doTest("PCA: US Arrests Data", test.PCA.arrests)

