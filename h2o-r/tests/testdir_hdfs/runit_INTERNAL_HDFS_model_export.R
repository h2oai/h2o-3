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
hadoop_namenode_is_accessible = hadoop.namenode.is.accessible()

if (hadoop_namenode_is_accessible) {
    hdfs_name_node <- HADOOP.NAMENODE
    hdfs_workspace <- Sys.getenv("HDFS_WORKSPACE")
} else {
    stop("Not running on H2O internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


heading("BEGIN TEST")
check.hdfs_model_export <- function(conn) {

  #----------------------------------------------------------------------
  # Model export
  #----------------------------------------------------------------------
  iris.hex <- as.h2o(iris)
  dl_model <- h2o.deeplearning(x=1:4, y=5, training_frame=iris.hex)
  path <- sprintf("hdfs://%s/%s/%s/", hdfs_name_node, hdfs_workspace, dl_model@model_id)
  exportedModelPath <- h2o.saveModel(dl_model, path = path)
  print("Model exported")

  #----------------------------------------------------------------------
  # Import model back
  #----------------------------------------------------------------------
  imported_model <- h2o.loadModel(path = exportedModelPath)

  print("Model imported")
  expect_false(is.null(imported_model))
}

doTest("HDFS operations", check.hdfs_model_export)
