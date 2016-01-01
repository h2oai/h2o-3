setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on prostate.csv
test.km.prostate <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  # prostate.hex = h2o.importURL( "https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv", "prostate.hex")
  # prostate.hex = h2o.importFile( normalizePath("../../../smalldata/logreg/prostate.csv"))
  prostate.hex <- h2o.uploadFile( h2oTest.locate("smalldata/logreg/prostate.csv"))
  prostate.sum <- summary(prostate.hex)
  print(prostate.sum)
  
  # prostate.data = read.csv(text = getURL("https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data <- read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data <- na.omit(prostate.data)
  
  for(i in 5:8) {
    h2oTest.logInfo(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    h2oTest.logInfo(paste( "Using these columns: ", colnames(prostate.hex)[-1]) )
    prostate.km.h2o <- h2o.kmeans(training_frame = prostate.hex, k = as.numeric(i), x = colnames(prostate.hex)[-1])
    print(prostate.km.h2o)
    prostate.km <- kmeans(prostate.data[,3], centers = i)
  }

  
}

h2oTest.doTest("KMeans Test: Prostate Data", test.km.prostate)
