#----------------------------------------------------------------------
# Purpose:  This test runs k-means on the full airlines dataset.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 186 is not likely to exist.
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=5)

if (running_inside_hexdata) {
  # hdp2.1 cluster
  hdfs_name_node = "mr-0xd6"
  hdfs_cross_file = "/datasets/airlines_all.csv"
} else {
  stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Import airlines_all.csv from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
airlines.hex <- h2o.importFile(conn, url)
n <- nrow(airlines.hex)
print(paste("Imported n =", n, "rows"))

heading(paste("Run k-means++ with k = 7 and max_iterations = 10"))
myX <- c(1:8, 10, 12:16, 19:21, 25:29)
airlines.km <- h2o.kmeans(training_frame = airlines.hex, x = myX, k = 7, init = "Furthest", max_iterations = 10, standardize = TRUE)
airlines.km

PASS_BANNER()
