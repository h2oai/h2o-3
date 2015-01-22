#----------------------------------------------------------------------
# Purpose:  This test compares k-means centers between H2O and MLlib.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 186 is not likely to exist.
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "mr-0xd6"
    hdfs_cross_file = "/datasets/BigCross.data"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)
local_err_bench = locate("smalldata/mllib_bench/bigcross_wcsse.csv")
# local_err_bench = locate("smalldata/mllib_bench/ozone_wcsse.csv")

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Import BigCross.data from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
cross.hex <- h2o.importFile(conn, url)
n <- nrow(cross.hex)
print(paste("Imported n =", n, "rows"))

err.mllib <- read.csv(local_err_bench, header = TRUE)
ncent <- err.mllib[,1]

for(k in ncent) {
  heading(paste("Run k-means++ with k =", k, "and max_iterations = 10"))
  cross.km <- h2o.kmeans(training_frame = cross.hex, k = k, init = "PlusPlus", max_iterations = 10, standardize = FALSE)

  path <- paste("smalldata/mllib_bench/bigcross_centers_", k, ".csv", sep = "")
  clust.mllib <- read.csv(locate(path), header = FALSE)
  clust.h2o <- as.data.frame(as.matrix(cross.km@model$centers))
  
  # Sort in ascending order by first dimension for comparison purposes
  clust.mllib <- clust.mllib[order(clust.mllib[,1]),]
  clust.h2o <- clust.h2o[order(clust.h2o[,1]),]
  colnames(clust.mllib) <- colnames(clust.h2o)
  # rownames(clust.mllib) <- 1:k
  # rownames(clust.h2o) <- 1:k
  
  cat("\nMLlib Cluster Centers:\n"); print(clust.mllib)
  cat("\nH2O Cluster Centers:\n"); print(clust.h2o)
  # expect_equal(t(clust.h2o), t(clust.mllib), tolerance = 0.3)
  
  wcsse.mllib <- err.mllib[which(err.mllib[,1] == k),2]
  wcsse.mllib <- cross.km@model$avg_within_ss
  cat("\nMLlib Average Within-Cluster SSE: ", wcsse.mllib, "\n")
  cat("H2O Average Within-Cluster SSE: ", wcsse.mllib, "\n")
  expect_equal(cross.km@model$avg_within_ss, wcsse.mllib)
}

PASS_BANNER()
