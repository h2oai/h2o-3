setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on benign.csv
test.km.benign <- function() {
  h2oTest.logInfo("Importing benign.csv data...\n")
  # benign.hex = h2o.importURL( "https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/benign.csv")
  # benign.hex = h2o.importFile( normalizePath("../../../smalldata/logreg/benign.csv"))
  benign.hex <- h2o.importFile( h2oTest.locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)
  
  # benign.data = read.csv(text = getURL("https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/benign.csv"), header = TRUE)
  benign.data <- read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header = TRUE)
  benign.data <- na.omit(benign.data)
  
  for(i in 2:6) {
    h2oTest.logInfo(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    benign.km.h2o <- h2o.kmeans(training_frame = benign.hex, k = as.numeric(i))
    print(benign.km.h2o)
    m <- h2o.getModel(benign.km.h2o@model_id)
    print(m)
    benign.km <- kmeans(benign.data, centers = i)
  }
  
  
}

h2oTest.doTest("KMeans Test: Benign Data", test.km.benign)

