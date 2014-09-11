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
    hdfs_iris_dir  = "/datasets/runit/iris_test_train"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.FV.hex <- h2o.importFile(conn, url)
head(iris.FV.hex)
tail(iris.FV.hex)
n <- nrow(iris.FV.hex)
print(n)
if (n != 150) {
    stop("FV nrows is wrong")
}
if (class(iris.FV.hex) != "H2OParsedData") {
    stop("iris.FV.hex is the wrong type")
}
print ("FV import worked")

#----------------------------------------------------------------------
# Directory file cases.
#----------------------------------------------------------------------

heading("Testing directory importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_dir)
iris.FV.dir.hex <- h2o.importFolder(conn, url, pattern="*.csv")
head(iris.FV.dir.hex)
tail(iris.FV.dir.hex)
n <- nrow(iris.FV.dir.hex)
print(n)
if (n != 150) {
    stop("FV nrows is wrong")
}
if (class(iris.FV.dir.hex) != "H2OParsedData") {
    stop("iris.FV.dir.hex is the wrong type")
}
print ("FV import worked")

PASS_BANNER()
