#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_hdfs")

local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})
if (!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")

options(echo=TRUE)
TEST_ROOT_DIR <- ".."
source(sprintf("%s/%s", TEST_ROOT_DIR, "findNSourceUtils.R"))


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the cdh3 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://192.168.1.176:80", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "192.168.1.176"
    hdfs_iris_file = "/datasets/runit/iris_wheader.csv"
    hdfs_covtype_file = "/datasets/runit/covtype.data"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

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
iris.km = h2o.kmeans(data = iris.hex, centers = 3, cols = c(1, 2, 3, 4), iter.max=10)
iris.km



heading("Running covtype importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
covtype.hex <- h2o.importHDFS(conn, url)
n <- nrow(covtype.hex)
print(n)
if (n != 581012) {
    stop("nrows is wrong")
}

heading("Running covtype KMeans")
covtype.km = h2o.kmeans(data = covtype.hex, centers = 8, cols = colnames(covtype.hex), iter.max=10)
covtype.km


PASS_BANNER()
