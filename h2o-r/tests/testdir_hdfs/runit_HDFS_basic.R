#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the H2O network by seeing if we can touch
# the namenode.
running_inside_h2o = is.running.internal.to.h2o()

if (running_inside_h2o) {
    hdfs_name_node = H2O_INTERNAL_HDFS_NAME_NODE
    hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
    hdfs_iris_dir  = "/datasets/runit/iris_test_train"
} else {
    stop("Not running on H2O internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


heading("BEGIN TEST")
check.hdfs_basic <- function(conn) {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  heading("Testing single file importHDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
  iris.hex <- h2o.importFile(conn, url)
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

  heading("Testing directory importHDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_dir)
  iris.dir.hex <- h2o.importFile(conn, url)
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

  testEnd()
}

doTest("HDFS operations", check.hdfs_basic)
