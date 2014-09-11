setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.PCA.australia <- function(conn) {
  Log.info("Importing AustraliaCoast.csv data...\n")
  australia.data = read.csv(locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australia.hex = h2o.uploadFile(conn, locate( "smalldata/pca_test/AustraliaCoast.csv",))
  australia.sum = summary(australia.hex)
  print(australia.sum)
  
  Log.info("H2O PCA on non-standardized Australia coastline data:\n")
  australia.pca.h2o = h2o.prcomp(australia.hex, standardize = FALSE)
  print(australia.pca.h2o)
  australia.pca = prcomp(australia.data, center = FALSE, scale. = FALSE, retx = TRUE)
  checkPCAModel(australia.pca.h2o, australia.pca)
  
  Log.info("H2O PCA on standardized Australia coastline data:\n")
  australia.pca.h2o.std = h2o.prcomp(australia.hex, standardize = TRUE)
  print(australia.pca.h2o.std)
  australia.pca.std = prcomp(australia.data, center = TRUE, scale. = TRUE, retx = TRUE)
  checkPCAModel(australia.pca.h2o.std, australia.pca.std)

  testEnd()
}

doTest("PCA: Australia Data", test.PCA.australia)

