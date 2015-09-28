setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test k-means clustering on prostate.csv
test.km.prostate <- function() {
  Log.info("Importing prostate.csv data...\n")
  # prostate.hex = h2o.importURL( "https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv", "prostate.hex")
  # prostate.hex = h2o.importFile( normalizePath("../../../smalldata/logreg/prostate.csv"))
  prostate.hex <- h2o.uploadFile( locate("smalldata/logreg/prostate.csv"))
  prostate.sum <- summary(prostate.hex)
  print(prostate.sum)
  
  # prostate.data = read.csv(text = getURL("https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data <- read.csv(locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data <- na.omit(prostate.data)
  
  for(i in 5:8) {
    Log.info(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    Log.info(paste( "Using these columns: ", colnames(prostate.hex)[-1]) )
    prostate.km.h2o <- h2o.kmeans(training_frame = prostate.hex, k = as.numeric(i), x = colnames(prostate.hex)[-1])
    print(prostate.km.h2o)
    prostate.km <- kmeans(prostate.data[,3], centers = i)
  }

  
}

doTest("KMeans Test: Prostate Data", test.km.prostate)
