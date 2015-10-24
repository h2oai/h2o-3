#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------
test <-
function() {

hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

hdfs_iris_file = "/datasets/runit/iris_wheader.csv"

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_iris_file)
iris.hex <- h2o.importFile(url, header = T)
print(summary(iris.hex))

myX = 1:4
myY = 5

# GBM Model
iris.gbm <- h2o.gbm(myX, myY, training_frame = iris.hex, distribution = 'multinomial')
print(iris.gbm)

myZ = 1

# GLM Model
iris.glm <- h2o.glm(myX, myZ, training_frame = iris.hex, family = "gaussian")
print(iris.glm)


# DL Model
iris.dl  <- h2o.deeplearning(myX, myY, training_frame = iris.hex, epochs=1, hidden=c(50,50), loss = 'CrossEntropy')
print(iris.dl)

}

doTest("Test", test)