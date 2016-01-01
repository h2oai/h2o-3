setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
data.hex <- h2o.importFile("/mnt/0xcustomer-datasets/c28/mr_output.tsv.sorted.gz")

dim(data.hex)

s = h2o.runif(data.hex)
train = data.hex[s <= 0.8,]
valid = data.hex[s > 0.8,]

#model.glm <- h2o.glm(x = 3:(ncol(train)), y = 2, training_frame = train, validation_frame=valid, family = "binomial", solver = "L_BFGS")

#pred = predict(model.glm, valid)
#perf <- h2o.performance(model.glm, valid)

model.gbm <- h2o.gbm(x = 3:(ncol(train)), y = 2, training_frame = train, validation_frame=valid, ntrees=10, max_depth=5) 
pred = predict(model.gbm, valid)
perf <- h2o.performance(model.gbm, valid)

}

h2oTest.doTest("Test",rtest)
