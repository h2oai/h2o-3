#----------------------------------------------------------------------
# Purpose:  This test exercises amazon s3n access from R.
#----------------------------------------------------------------------

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_hdfs")

local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})
if (!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")

options(echo=TRUE)
TEST_ROOT_DIR <- ".."
source(sprintf("%s/%s", TEST_ROOT_DIR, "findNSourceUtils.R"))


heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS S3N")
s3n_iris_file <- "0xdata-public/examples/h2o/R/datasets/iris_wheader.csv"
url <- sprintf("s3n://%s", s3n_iris_file)

iris.hex <- h2o.importHDFS(conn, url)
head(iris.hex)
tail(iris.hex)
n <- nrow(iris.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}
if (class(iris.hex) != "H2OParsedData") {
    stop("iris.hex is the wrong type")
}


#----------------------------------------------------------------------
# Directory cases.
#----------------------------------------------------------------------

heading("Testing directory importHDFS S3N")
s3n_iris_dir <- "0xdata-public/examples/h2o/R/datasets"
url2 <- sprintf("s3n://%s", s3n_iris_dir)

irisdir.hex <- h2o.importHDFS(conn, url2)
head(irisdir.hex)
tail(irisdir.hex)
n <- nrow(irisdir.hex)
print(n)
if (n != 150) {
    stop("nrows is wrong")
}
if (class(irisdir.hex) != "H2OParsedData") {
    stop("irisdir.hex is the wrong type")
}


PASS_BANNER()
