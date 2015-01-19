#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "mr-0xd6"
    hdfs_iris_file = "/datasets/iris_wheader.csv"
    hdfs_covtype_file = "/datasets/covtype.data"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Import iris_wheader.csv from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.hex <- h2o.importFile(conn, url)
n <- nrow(iris.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}

heading("Running KMeans on iris")
iris.km = h2o.kmeans(training_frame = iris.hex, k = 3, x = 1:4, max_iterations = 10)
iris.km



heading("Importing covtype.data from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
covtype.hex <- h2o.importFile(conn, url)
n <- nrow(covtype.hex)
print(n)
if (n != 581012) {
    stop("nrows is wrong")
}

heading("Running KMeans on covtype")
covtype.km = h2o.kmeans(training_frame = covtype.hex, k = 8, max_iterations = 10)
covtype.km


PASS_BANNER()
