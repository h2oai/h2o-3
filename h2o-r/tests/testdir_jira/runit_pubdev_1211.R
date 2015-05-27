setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pca.quasar <- function(conn) {
  Log.info("Importing SDSS_quasar.txt data...\n")
  quasar.dat <- read.table(locate("smalldata/pca_test/SDSS_quasar.txt"), header = TRUE)
  quasar.hex <- h2o.uploadFile(conn, locate("smalldata/pca_test/SDSS_quasar.txt"), header = TRUE)
  quasar.dat[,1] <- NULL; quasar.hex <- quasar.hex[,-1]
  quasar.sum <- summary(quasar.hex)
  print(quasar.sum)
  
  Log.info("R PCA with k = 22 on columns scaled by standard deviation")
  # fitR <- prcomp(quasar.dat, center = FALSE, scale. = TRUE)
  fitR <- prcomp(quasar.dat, center = FALSE, scale. = apply(quasar.dat, 2, sd, na.rm = TRUE))
  print(fitR)
  
  Log.info("H2O PCA with k = 22, transform = 'DESCALE'")
  fitH2O <- h2o.prcomp(training_frame = quasar.hex, k = 22, transform = "DESCALE")
  print(fitH2O)
  
  checkPCAModel(fitH2O, fitR, tolerance = 2e-5)
  testEnd()
}

doTest("PCA Test: Quasar Data", test.pca.quasar)

