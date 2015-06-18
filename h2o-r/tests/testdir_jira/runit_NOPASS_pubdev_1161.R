setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pca.mds <- function(conn) {
  Log.info("Importing pub_1161.csv data...\n")
  mds <- read.csv(locate("smalldata/jira/pub_1161.csv"), header = TRUE)
  mds <- as.matrix(mds)
  train.dat <- mds %*% t(mds)
  train.hex <- as.h2o(conn, train.dat)
  
  nvec <- 9
  Log.info(paste("R SVD with nv = ", nvec, sep = ""))
  fitR <- svd(train.dat)
  
  Log.info(paste("H2O PCA with nv = ", nvec, ", transform = 'NONE', max_iterations = 2000", sep = ""))
  fitH2O <- h2o.svd(training_frame = train.hex, nv = nvec, transform = "NONE", max_iterations = 2000)
  
  Log.info("R eigenvalues:"); print(fitR$d)
  Log.info("H2O eigenvalues:"); print(fitH2O@model$d)
  expect_equal(fitH2O@model$d, fitR$d[1:nvec], tolerance = 1e-6, scale = 1)
  
  # Log.info("R right singular values:"); print(fitR$v)
  # Log.info("H2O right singular values:"); print(fitH2O@model$v)
  # checkSignedCols(fitH2O@model$v, fitR$v, tolerance = 1e-6)
  
  Log.info("Check H2O decomposition UDV' = training data")
  u.h2o <- h2o.getFrame(fitH2O@model$u_key$name)
  u.h2o <- as.matrix(u.h2o)
  udv.h2o <- u.h2o %*% diag(fitH2O@model$d) %*% t(fitH2O@model$v)
  print(head(udv.h2o))
  expect_equal(udv.h2o, train.dat, tolerance = 1e-6, scale = 1)
  
  testEnd()
}

doTest("SVD Test: Precision Loss in Eigenvalues", test.pca.mds)
