#----------------------------------------------------------------------
# Purpose:  This tests GLRM on a large dataset.
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
  hdfs_cross_file = "/datasets/BigCross.data"
} else {
  stop("Not running on 0xdata internal network. No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Import BigCross.data from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
cross.hex <- h2o.importFile(conn, url)
n <- nrow(cross.hex)
print(paste("Imported n =", n, "rows"))

heading("Running GLRM on BigCross.data")
cross.glrm = h2o.glrm(training_frame = cross.hex, k = 3, max_iterations = 100)
print(cross.glrm)

PASS_BANNER()

