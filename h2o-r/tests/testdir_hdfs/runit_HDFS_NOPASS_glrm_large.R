#----------------------------------------------------------------------
# Purpose:  This tests GLRM on a large dataset.
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
  hdfs_cross_file = "/datasets/runit/BigCross.data"
} else {
  stop("Not running on H2O internal network. No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
check.hdfs_glrm <- function(conn) {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(conn, url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))

  heading("Running GLRM on BigCross.data")
  cross.glrm = h2o.glrm(training_frame = cross.hex, k = 3, max_iterations = 20)
  print(cross.glrm)
  h2o.rm(cross.glrm@model$loading_key$name)   # Remove loading matrix to free memory


  testEnd()
}

doTest("GLRM test", check.hdfs_glrm)
