setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests k-means on a large dataset.
#----------------------------------------------------------------------




#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the H2O network by seeing if we can touch
# the namenode.
hadoop_namenode_is_accessible = h2oTest.hadoopNamenodeIsAccessible()

if (hadoop_namenode_is_accessible) {
    hdfs_name_node = HADOOP.NAMENODE
    hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
    hdfs_covtype_file = "/datasets/runit/covtype.data"
} else {
    stop("Not running on H2O internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

h2oTest.heading("BEGIN TEST")
check.kmeans <- function() {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  h2oTest.heading("Import iris_wheader.csv from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
  iris.hex <- h2o.importFile(url)
  n <- nrow(iris.hex)
  print(n)
  if (n != 150) {
      stop("nrows is wrong")
  }

  h2oTest.heading("Running KMeans on iris")
  iris.km <- h2o.kmeans(training_frame = iris.hex, k = 3, x = 1:4, max_iterations = 10)
  print(iris.km)



  h2oTest.heading("Importing covtype.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
  covtype.hex <- h2o.importFile(url)
  n <- nrow(covtype.hex)
  print(n)
  if (n != 581012) {
      stop("nrows is wrong")
  }

  h2oTest.heading("Running KMeans on covtype")
  covtype.km <- h2o.kmeans(training_frame = covtype.hex, k = 8, max_iterations = 10)
  print(covtype.km)


  
}

h2oTest.doTest("K-means", check.kmeans)
