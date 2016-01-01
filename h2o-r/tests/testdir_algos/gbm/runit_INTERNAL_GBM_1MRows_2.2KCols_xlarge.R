setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/1Mx2.2k.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(url))
print("Time it took to parse")
print(parse_time)

response=1 #1:1000 imbalance
predictors=c(3:ncol(data.hex))

# Start modeling   
# Gradient Boosted Trees
gbm_time <- system.time(mdl.gbm <- h2o.gbm(x=predictors, y=response, training_frame=data.hex, distribution = "bernoulli"))
mdl.gbm
print("Time it took to build GBM")
print(gbm_time)

}

h2oTest.doTest("Test",rtest)

