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
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=5)

if (running_inside_hexdata) {
    # hdp2.1 cluster
    hdfs_name_node = "mr-0xd6"    
    hdfs_iris_file = "/datasets/iris_wheader.csv"
    hdfs_iris_dir  = "/datasets/iris_test_train"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.hex <- h2o.importFile(conn, url)
head(iris.hex)
tail(iris.hex)
n <- nrow(iris.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}
if (class(iris.hex) != "H2OFrame") {
    stop("iris.hex is the wrong type")
}
print ("Import worked")

#----------------------------------------------------------------------
# Directory file cases.
#----------------------------------------------------------------------

heading("Testing directory importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_dir)
iris.dir.hex <- h2o.importFile(conn, url)
head(iris.dir.hex)
tail(iris.dir.hex)
n <- nrow(iris.dir.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}
if (class(iris.dir.hex) != "H2OFrame") {
    stop("iris.dir.hex is the wrong type")
}
print ("Import worked")

PASS_BANNER()
