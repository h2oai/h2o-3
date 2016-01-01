setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/bigdata/5MRows6KCols.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(url))
print("Time it took to parse")
print(parse_time)

dim(data.hex)

s = h2o.runif(data.hex)
train = data.hex[s <= 0.8,]
valid = data.hex[s > 0.8,]

#GLM Model
glm_time <- system.time(model.glm <- h2o.glm(x = 2:(ncol(train)), y = 1, training_frame = train, validation_frame=valid, solver = "L_BFGS"))
print("Time it took to build GLM")
print(glm_time)
model.glm

pred = predict(model.glm, valid)
perf <- h2o.performance(model.glm, valid)

}

h2oTest.doTest("Test",rtest)
