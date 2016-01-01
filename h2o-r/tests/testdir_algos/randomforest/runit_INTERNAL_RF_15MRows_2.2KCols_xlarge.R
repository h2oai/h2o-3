setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/15Mx2.2k.csv"
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
#Random Forest
rf_time <- system.time(mdl.rf <- h2o.randomForest(x=predictors, y=response, training_frame=data.hex, ntrees=10, max_depth=5))
mdl.rf
print("Time it took to build RF")
print(rf_time)

}

h2oTest.doTest("Test",rtest)

