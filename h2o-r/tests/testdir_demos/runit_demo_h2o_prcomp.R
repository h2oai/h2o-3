##
# Test out the h2o.prcomp R demo
# It imports a dataset, parses it, and prints a summary
# Then, it runs h2o.prcomp on the dataset
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.h2o.prcomp <- function() {
  ausPath <- system.file("extdata", "australia.csv", package="h2o")
  Log.info(paste("Uploading", ausPath))
  australia.hex <- h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")
  
  Log.info("Print out summary of australia.csv")
  print(summary(australia.hex))
  
  Log.info("Run PCA with k = 8, gamma = 0, transform = 'DEMEAN'")
  australia.pca = h2o.prcomp(australia.hex, k = 8, transform = "DEMEAN")
  print(australia.pca)
  
  Log.info("Run PCA with k = 4, transform = 'STANDARDIZE'")
  australia.pca2 = h2o.prcomp(australia.hex, k = 4, transform = "STANDARDIZE")
  print(australia.pca2)
  
  testEnd()
}

doTest("Test out the h2o.prcomp R demo", test.h2o.prcomp)
