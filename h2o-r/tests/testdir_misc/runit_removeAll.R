##
# Generate lots of keys then remove them
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn) {
  arrests.hex = h2o.uploadFile(conn, locate("smalldata/pca_test/USArrests.csv"), "arrests.hex")

  Log.info("Slicing column 1 of arrests 50 times")
  for(i in 1:50) {
    arrests.hex[,1]
    if( i %% 50 == 0 ) Log.info(paste("Finished ", paste(i, " slices of arrests.hex", sep = ""), sep = ""))
  }

  Log.info("Performing 100 PCA's on the arrests data")
  for(i in 1:100) {
    arrests.pca.h2o = h2o.prcomp(arrests.hex, k = 2)
    if( i %% 50 == 0 ) Log.info(paste("Finished ", paste(i, " PCAs of arrests.hex", sep = ""), sep = ""))
  }
  Log.info("Making a call to remove all")
  h2o.removeAll(conn)

  Log.info("h2o.ls() should return an empty list")
  expect_equal(length(h2o.ls()$key), 0)

  testEnd()
}

doTest("Many Keys Test: Removing", test)

