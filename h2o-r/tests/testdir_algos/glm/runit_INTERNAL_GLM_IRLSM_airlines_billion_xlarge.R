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

## Response = Distance 

myY = "C19"
myX = setdiff(names(data.hex), myY)
## Build GLM Model and compare AUC with h2o1

glm_irlsm_time <- system.time(data_irlsm.glm <- h2o.glm(x = myX, y = myY, training_frame = data.train, validation_frame=data.valid, family = "gaussian", solver = "IRLSM"))
data_irlsm.glm
print("Time it took to build GLM IRLSM")
print(glm_irlsm_time)

}

h2oTest.doTest("Test",rtest)
