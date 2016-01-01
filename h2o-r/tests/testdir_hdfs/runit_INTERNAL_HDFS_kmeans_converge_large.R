setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests convergence of k-means on a large dataset.
#----------------------------------------------------------------------




#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the H2O network by seeing if we can touch
# the namenode.
hadoop_namenode_is_accessible = h2oTest.hadoopNamenodeIsAccessible()

if (hadoop_namenode_is_accessible) {
    hdfs_name_node = HADOOP.NAMENODE
    hdfs_cross_file = "/datasets/runit/BigCross.data"
} else {
    stop("Not running on H2O internal network. No access to HDFS.")
}

#----------------------------------------------------------------------

h2oTest.heading("BEGIN TEST")
check.kmeans_converge <- function() {

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  h2oTest.heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))
  ncent <- 3
  miters <- 10

  h2oTest.heading(paste("Run k-means with k =", ncent, "and max_iterations =", miters))
  cross1.km <- h2o.kmeans(training_frame = cross.hex, k = ncent, max_iterations = miters)
  print(cross1.km)

  h2oTest.heading("Run k-means with init = final cluster centers and max_iterations = 1")
  init_centers <- as.h2o(getCenters(cross1.km))
  cross2.km <- h2o.kmeans(training_frame = cross.hex, init = init_centers, max_iterations = 1)
  print(cross2.km)

  h2oTest.heading("Check k-means converged or maximum iterations reached")
  avg_change <- sum((getCenters(cross1.km) - getCenters(cross2.km))^2)/ncent
  expect_true(avg_change < 1e-6 || getIterations(cross1.km) > miters)

  
}

h2oTest.doTest("K-means convergence", check.kmeans_converge)
