#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the cdh4 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://mr-0x6:80", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "mr-0x6"
    hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
    hdfs_covtype_file = "/datasets/runit/covtype.data"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Running iris importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.hex <- h2o.importHDFS(conn, url)
n <- nrow(iris.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}

heading("Running iris KMeans")
iris.km = h2o.kmeans(training_frame = iris.hex, K = 3, x = 1:4, max_iters = 10)
iris.km



heading("Running covtype importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
covtype.hex <- h2o.importFile(conn, url)
n <- nrow(covtype.hex)
print(n)
if (n != 581012) {
    stop("nrows is wrong")
}

heading("Running covtype KMeans")
covtype.km = h2o.kmeans(training_frame = covtype.hex, K = 8, max_iters = 10)
covtype.km


PASS_BANNER()
