


# Test k-means clustering on benign.csv
test.km.benign <- function() {
  Log.info("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile( locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)
  
  benign.data <- read.csv(locate("smalldata/logreg/benign.csv"), header = TRUE)
  benign.data <- na.omit(benign.data)
  for( i in 1:6 ) {
    Log.info(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    benign.km.h2o <- h2o.kmeans(training_frame = benign.hex, k = as.numeric(i))
    print(benign.km.h2o)
    benign.km <- kmeans(benign.data, centers = i)
  }

  
}

doTest("KMeans Test: Benign Data", test.km.benign)
