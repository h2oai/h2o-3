setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests convergence of k-means on a large dataset.
#----------------------------------------------------------------------

hdfs_name_node <- HADOOP.NAMENODE
hdfs_cross_file <- "/datasets/runit/BigCross.data"

heading("BEGIN TEST")
check.kmeans_converge <- function() {

  heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))
  ncent <- 3
  miters <- 10

  heading(paste("Run k-means with k =", ncent, "and max_iterations =", miters))
  cross1.km <- h2o.kmeans(training_frame = cross.hex, k = ncent, max_iterations = miters, seed=42)
  print(cross1.km)
  centers <- as.h2o(getCenters(cross1.km))
  print(centers)

  heading("Run k-means with user_points = final cluster centers and max_iterations = 1")
  cross2.km <- h2o.kmeans(training_frame = cross.hex, user_points = centers, max_iterations = 1, seed=42)
  print(cross2.km)
  centers2 <- as.h2o(getCenters(cross2.km))
  print(centers2)
    
  heading("Check k-means converged or maximum iterations reached")
  avg_change <- sum((centers - centers2)^2)/ncent
  expect_true(avg_change < 1e-6 || getIterations(cross1.km) > miters)
}

doTest("K-means convergence", check.kmeans_converge)
