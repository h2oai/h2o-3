setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests PCA on a large dataset.
#----------------------------------------------------------------------

hdfs_name_node <- HADOOP.NAMENODE
hdfs_cross_file <- "/datasets/runit/BigCross.data"

heading("BEGIN TEST")
check.pca_large <- function() {
  heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))

  heading("Running PCA on BigCross.data")
  cross.pca = h2o.prcomp(training_frame = cross.hex, k = 25, max_iterations = 1000)
  print(cross.pca@model$eigenvectors)
  print(cross.pca@model$importance)
}

doTest("PCA test", check.pca_large)
