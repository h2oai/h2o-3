setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests GLRM on a large dataset.
#----------------------------------------------------------------------

hdfs_name_node <- HADOOP.NAMENODE
hdfs_cross_file <- "/datasets/runit/BigCross.data"

#----------------------------------------------------------------------

heading("BEGIN TEST")
check.hdfs_glrm <- function() {
  heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))

  heading("Running GLRM on BigCross.data")
  cross.glrm <- h2o.glrm(training_frame = cross.hex, k = 3, max_iterations = 20)
  print(cross.glrm)
  h2o.rm(cross.glrm@model$representation_name)   # Remove X matrix to free memory
}

doTest("GLRM test", check.hdfs_glrm)
