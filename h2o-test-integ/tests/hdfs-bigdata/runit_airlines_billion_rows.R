#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R')

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

library(RCurl)
library(testthat)
library(h2o)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

hdfs_data_file = "/datasets/airlinesbillion.csv"

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
data.hex <- h2o.importFile(conn, url)

n <- nrow(data.hex)
print(n)
if (n != 1166952590) {
    stop("nrows is wrong")
}

## Chose which col as response
## Response = Distance 
myY = "C19"
myX = setdiff(names(data.hex), myY)
## Build GLM Model and compare AUC with h2o1
data_admm.glm <- h2o.glm(x = myX, y = myY, training_frame = data.hex, family = "gaussian", solver = "IRLSM")
data_lbfgs.glm <- h2o.glm(x = myX, y = myY, training_frame = data.hex, family = "gaussian", solver = "L_BFGS")

## Chose which col as response
## Response = IsDepDelayed
myY = "C31"
myX = setdiff(names(data.hex), myY)
data.gbm <- h2o.gbm(x = myX, y = myY, training_frame = data.hex, distribution = "AUTO")

PASS_BANNER()
