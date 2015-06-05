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
library(h2o)

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

hdfs_iris_file = "/datasets/runit/iris_wheader.csv"

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.hex <- h2o.importFile(conn, url)
print(summary(iris.hex))

myX = 1:4
myY = 5

# GBM Model
iris.gbm <- h2o.gbm(myX, myY, training_frame = iris.hex, distribution = 'multinomial')
print(iris.gbm)

# GLM Model
iris.glm <- h2o.glm(myX, myY, training_frame = iris.hex, family = "gaussian")
print(iris.glm)


# DL Model
iris.dl  <- h2o.deeplearning(myX, myY, training_frame = iris.hex, epochs=1, hidden=c(50,50), loss = 'CrossEntropy')
print(iris.dl)

# DRF Model
# iris.drf <- h2o.randomforest(myX, myY, training_frame = iris.hex)

PASS_BANNER()
