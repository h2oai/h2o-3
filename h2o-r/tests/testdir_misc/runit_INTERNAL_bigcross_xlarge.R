setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/runit/BigCross.data"

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
data.hex <- h2o.importFile(url)
print(summary(data.hex))

myY = "C1"
myX = setdiff(names(data.hex), myY)

# GLM
data.glm <- h2o.glm(myX, myY, training_frame = data.hex, family = "gaussian", solver = "L_BFGS")
data.glm

# GBM Model
data.gbm <- h2o.gbm(myX, myY, training_frame = data.hex, distribution = 'AUTO')
print(data.gbm)

# DL Model
data.dl  <- h2o.deeplearning(myX, myY, training_frame = data.hex, epochs=1, hidden=c(50,50), loss = 'Automatic')
print(data.dl)

}

h2oTest.doTest("Test",rtest)
