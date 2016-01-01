setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on benign.csv
test.km.benign <- function() {
  h2oTest.logInfo("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)
  
  benign.data <- read.csv(h2oTest.locate("smalldata/logreg/benign.csv"), header = TRUE)
  benign.data <- na.omit(benign.data)
  for( i in 1:6 ) {
    h2oTest.logInfo(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    benign.km.h2o <- h2o.kmeans(training_frame = benign.hex, k = as.numeric(i), nfolds=3)
    print(benign.km.h2o)
  }

  
}

h2oTest.doTest("KMeans Test: Benign Data with 3-fold CV", test.km.benign)
