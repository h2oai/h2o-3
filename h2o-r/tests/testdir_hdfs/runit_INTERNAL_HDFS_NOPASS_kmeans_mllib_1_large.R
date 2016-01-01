setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test compares k-means centers between H2O and MLlib.
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
    stop("Not running on H2O internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

h2oTest.heading("BEGIN TEST")
check.kmeans_mllib <- function() {
  local_err_bench = h2oTest.locate("smalldata/mllib_bench/bigcross_wcsse.csv")
  # local_err_bench = h2oTest.locate("smalldata/mllib_bench/ozone_wcsse.csv")

  #----------------------------------------------------------------------
  # Single file cases.
  #----------------------------------------------------------------------

  h2oTest.heading("Import BigCross.data from HDFS")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
  cross.hex <- h2o.importFile(url)
  n <- nrow(cross.hex)
  print(paste("Imported n =", n, "rows"))

  err.mllib <- read.csv(local_err_bench, header = TRUE)
  ncent <- err.mllib[,1]

  for(k in ncent) {
    h2oTest.heading(paste("Run k-means++ with k =", k, "and max_iterations = 10"))
    cross.km <- h2o.kmeans(training_frame = cross.hex, k = k, init = "PlusPlus", max_iterations = 10, standardize = FALSE)

    path <- paste("smalldata/mllib_bench/bigcross_centers_", k, ".csv", sep = "")
    clust.mllib <- read.csv(h2oTest.locate(path), header = FALSE)
    clust.h2o <- getCenters(cross.km)
  
    # Sort in ascending order by first dimension for comparison purposes
    ulen <- apply(clust.mllib, 2, function(x) { length(unique(x)) })
    cidx <- which(ulen == nrow(clust.mllib))
    cidx <- ifelse(length(cidx) == 0, 1, cidx[1])
  
    h2oTest.logInfo(paste("Sorting clusters in ascending order by column", cidx))
    clust.mllib <- clust.mllib[order(clust.mllib[,cidx]),]
    clust.h2o <- clust.h2o[order(clust.h2o[,cidx]),]
    colnames(clust.mllib) <- colnames(clust.h2o)
    # rownames(clust.mllib) <- 1:k
    # rownames(clust.h2o) <- 1:k
  
    cat("\nMLlib Cluster Centers:\n"); print(clust.mllib)
    cat("\nH2O Cluster Centers:\n"); print(clust.h2o)
    # expect_equal(t(clust.h2o), t(clust.mllib), tolerance = 0.3)
  
    wcsse.mllib <- err.mllib[which(err.mllib[,1] == k),2]
    wcsse.h2o <- getTotWithinSS(cross.km) / n
    cat("\nMLlib Average Within-Cluster SSE: ", wcsse.mllib, "\n")
    cat("H2O Average Within-Cluster SSE: ", wcsse.h2o, "\n")
    expect_equal(wcsse.h2o, wcsse.mllib)
  }

  
}

h2oTest.doTest("K-means comparison", check.kmeans_mllib)
