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
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

hdfs_data_file = "/datasets/airlinesbillion.csv"

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(url))
print("Time it took to parse")
print(parse_time)

data1.hex <- data.hex

n <- nrow(data.hex)
print(n)
if (n != 1166952590) {
    stop("nrows is wrong")
}

#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(data.hex)    # Useful when number of rows too large for R to handle
data.train <- data.hex[s <= 0.8,]
data.valid <- data.hex[s > 0.8,]

## Chose which col as response
## Response = IsDepDelayed
myY = "C31"
myX = setdiff(names(data1.hex), myY)

dl_time <- system.time(data1.dl <- h2o.deeplearning(x=myX, y=myY, training_frame=data.train, validation_frame=data.valid, replicate_training_data=FALSE, epochs=.1, hidden=c(5,5)))
data1.dl
print("Time it took to build DL")
print(dl_time)

PASS_BANNER()
