


# Test PCA on USArrests.csv
test.pca.arrests <- function() {
  Log.info("Importing USArrests.csv data...\n")
  arrests.hex <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
  arrests.sum <- summary(arrests.hex)
  print(arrests.sum)

  for(i in 1:4) {
    Log.info(paste("H2O PCA with ", i, " dimensions:\n", sep = ""))
    Log.info(paste("Using these columns: ", colnames(arrests.hex)))
    arrests.pca.h2o <- h2o.prcomp(training_frame = arrests.hex, k = as.numeric(i))
    print(arrests.pca.h2o)
  }
  
  
}

doTest("PCA Test: USArrests Data", test.pca.arrests)
