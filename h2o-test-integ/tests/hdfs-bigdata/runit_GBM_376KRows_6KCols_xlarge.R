#----------------------------------------------------------------------
# Purpose:  This test exercises building GLM/GBM/DL  model 
#           for 376K rows and 6.9K columns 
#----------------------------------------------------------------------
test <-
function() {
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

h2o.ls()
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

#GBM model
gbm_time <- system.time(model.gbm <- h2o.gbm(x = 3:(ncol(train)), y = 2, training_frame = train, validation_frame=valid, ntrees=10, max_depth=5)) 
print("Time it took to build GBM")
print(gbm_time)
model.gbm

pred = predict(model.gbm, valid)
perf <- h2o.performance(model.gbm, valid)

}

doTest("Test", test)
