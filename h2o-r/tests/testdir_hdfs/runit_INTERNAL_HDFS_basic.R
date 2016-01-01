setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
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
    hdfs_iris_dir  = "/datasets/runit/iris_test_train"
} else {
    stop("Not running on H2O internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


h2oTest.heading("BEGIN TEST")
check.hdfs_basic <- function() {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  h2oTest.heading("Testing single file importHDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
  iris.hex <- h2o.importFile(url)
  head(iris.hex)
  tail(iris.hex)
  n <- nrow(iris.hex)
  print(n)
  if (n != 150) {
      stop("nrows is wrong")
  }
  if (class(iris.hex) != "H2OFrame") {
      stop("iris.hex is the wrong type")
  }
  print ("Import worked")

  #----------------------------------------------------------------------
  # Directory file cases.
  #----------------------------------------------------------------------

  h2oTest.heading("Testing directory importHDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_dir)
  iris.dir.hex <- h2o.importFile(url)
  head(iris.dir.hex)
  tail(iris.dir.hex)
  n <- nrow(iris.dir.hex)
  print(n)
  if (n != 150) {
      stop("nrows is wrong")
  }
  if (class(iris.dir.hex) != "H2OFrame") {
      stop("iris.dir.hex is the wrong type")
  }
  print ("Import worked")

  
}

h2oTest.doTest("HDFS operations", check.hdfs_basic)
