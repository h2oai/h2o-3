setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test GLRM on arrests.csv
test.glrm.arrests <- function(conn) {
  Log.info("Importing arrests.csv data...\n")
  arrests.hex <- h2o.uploadFile(conn, locate("smalldata/pca_test/USArrests.csv"))
  arrests.sum <- summary(arrests.hex)
  print(arrests.sum)
  arrests.data <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  
  for(i in 1:4) {
    Log.info(paste("H2O GLRM with ", i, " dimensions:\n", sep = ""))
    Log.info(paste( "Using these columns: ", colnames(arrests.hex)))
    arrests.glrm.h2o <- h2o.glrm(training_frame = arrests.hex, k = as.numeric(i))
    print(arrests.glrm.h2o)
    # prostate.glrm <- kmeans(prostate.data[,3], centers = i)
  }
  
  testEnd()
}

doTest("GLRM Test: USArrests Data", test.glrm.arrests)