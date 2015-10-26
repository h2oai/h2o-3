#----------------------------------------------------------------------
# Purpose:  This tests GLRM on a large dataset.
#----------------------------------------------------------------------




#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the H2O network by seeing if we can touch
# the namenode.
hadoop_namenode_is_accessible = hadoop.namenode.is.accessible()

if (hadoop_namenode_is_accessible) {
  hdfs_name_node = hadoop.namenode()
  hdfs_cross_file = "/datasets/runit/BigCross.data"
} else {
  stop("Not running on H2O internal network. No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
check.hdfs_glrm <- function() {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))

  heading("Running GLRM on BigCross.data")
  cross.glrm = h2o.glrm(training_frame = cross.hex, k = 3, max_iterations = 20)
  print(cross.glrm)
  h2o.rm(cross.glrm@model$representation_name)   # Remove X matrix to free memory


  
}

doTest("GLRM test", check.hdfs_glrm)
