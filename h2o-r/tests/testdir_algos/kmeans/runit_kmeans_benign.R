setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

# Test k-means clustering on benign.csv
test.km.benign <- function(conn) {
  Log.info("Importing benign.csv data...\n")
  # benign.hex = h2o.importURL(conn, "https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/benign.csv")
  # benign.hex = h2o.importFile(conn, normalizePath("../../../smalldata/logreg/benign.csv"))
  benign.hex <- h2o.uploadFile(conn, locate("../../../smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)
  
  # benign.data = read.csv(text = getURL("https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/benign.csv"), header = TRUE)
  benign.data <- read.csv(locate("../../../smalldata/logreg/benign.csv"), header = TRUE)
  benign.data <- na.omit(benign.data)
  
  for(i in 2:6) {
    Log.info(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    benign.km.h2o <- h2o.kmeans(data = benign.hex, centers = as.numeric(i), cols = colnames(benign.hex))
    print(benign.km.h2o)
    benign.km <- kmeans(benign.data, centers = i)
  }
  
  testEnd()
}

doTest("KMeans Test: Benign Data", test.km.benign)

