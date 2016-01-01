setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
parse_time <- system.time(data.hex <- h2o.importFile("/mnt/0xcustomer-datasets/c28/mr_output.tsv.sorted.gz"))
print("Time it took to parse")
print(parse_time)

dim(data.hex)

s = h2o.runif(data.hex)
train = data.hex[s <= 0.8,]
valid = data.hex[s > 0.8,]

#GLM Model
glm_time <- system.time(model.glm <- h2o.glm(x = 3:(ncol(train)), y = 6, training_frame = train, validation_frame=valid, family = "binomial", solver = "L_BFGS"))
print("Time it took to build GLM")
print(glm_time)
model.glm

pred = predict(model.glm, valid)
perf <- h2o.performance(model.glm, valid)

}

h2oTest.doTest("Test",rtest)
