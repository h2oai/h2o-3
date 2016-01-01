setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/airlinesbillion.csv"
#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

h2oTest.heading("Testing single file importHDFS")
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
gbm_10tree_time <- system.time(data1.gbm <- h2o.gbm(x = myX, y = myY, training_frame = data.train, validation_frame=data.valid, ntrees = 10, max_depth = 5, distribution = "AUTO"))
data1.gbm
print("Time it took to build 10 tree GBM")
print(gbm_10tree_time)

gbm_50tree_time <- system.time(data2.gbm <- h2o.gbm(x = myX, y = myY, training_frame = data.train, validation_frame=data.valid, ntrees = 50, max_depth = 5, distribution = "AUTO"))
data2.gbm
print("Time it took to build 50 tree GBM")
print(gbm_50tree_time)

}

h2oTest.doTest("Test",rtest)
