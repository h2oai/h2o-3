setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests k-means on a large dataset.
#----------------------------------------------------------------------

hdfs_name_node <- HADOOP.NAMENODE
hdfs_iris_file <- "/datasets/runit/iris_wheader.csv"
hdfs_covtype_file <- "/datasets/runit/covtype.data"

heading("BEGIN TEST")
check.kmeans <- function() {
  heading("Import iris_wheader.csv from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
  iris.hex <- h2o.importFile(url)
  n <- nrow(iris.hex)
  print(n)
  if (n != 150) {
      stop("nrows is wrong")
  }

  heading("Running KMeans on iris")
  iris.km <- h2o.kmeans(training_frame = iris.hex, k = 3, x = 1:4, max_iterations = 10)
  print(iris.km)



  heading("Importing covtype.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
  covtype.hex <- h2o.importFile(url)
  n <- nrow(covtype.hex)
  print(n)
  if (n != 581012) {
      stop("nrows is wrong")
  }

  heading("Running KMeans on covtype")
  covtype.km <- h2o.kmeans(training_frame = covtype.hex, k = 8, max_iterations = 10)
  print(covtype.km)
}

doTest("K-means", check.kmeans)
