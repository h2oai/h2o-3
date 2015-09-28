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
check.hdfs_model_export <- function(conn) {

  #----------------------------------------------------------------------
  # Model export
  #----------------------------------------------------------------------
  iris.hex <- hex <- as.h2o(iris)
  dl_model <- h2o.deeplearning(x=1:4, y=5, training_frame=iris.hex)
  #hdfs_name_node <- "mr-0x6"
  path <- sprintf("hdfs://%s/tmp/dl_model", hdfs_name_node)
  exportedModelPath <- h2o.saveModel(dl_model, path = path)
  print ("Model exported")

  #----------------------------------------------------------------------
  # Import model back
  #----------------------------------------------------------------------
  imported_model <- h2o.loadModel(path = exportedModelPath)

  print ("Model imported")

  
}

doTest("HDFS operations", check.hdfs_model_export)
