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
check.hdfs_model_export <- function(conn) {

  #----------------------------------------------------------------------
  # Model export
  #----------------------------------------------------------------------
  iris.hex <- hex <- as.h2o(iris)
  dl_model <- h2o.deeplearning(x=1:4, y=5, training_frame=iris.hex)
  #hdfs_name_node <- "mr-0x6"
  path <- sprintf("hdfs://%s/tmp/dl_model", hdfs_name_node)
  exportedModelPath <- h2o.saveModel(dl_model, path = h2oTest.sandbox())
  print ("Model exported")

  #----------------------------------------------------------------------
  # Import model back
  #----------------------------------------------------------------------
  imported_model <- h2o.loadModel(path = exportedModelPath)

  print ("Model imported")

}

h2oTest.doTest("HDFS operations", check.hdfs_model_export)
