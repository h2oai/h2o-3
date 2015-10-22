
#----------------------------------------------------------------------
# Purpose:  This test exercises building 15MRows2KCols
#             
#----------------------------------------------------------------------
test <-
function() {
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

hdfs_data_file = "/datasets/bigdata/7MRows_4400KCols.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(url))
print("Time it took to parse")
print(parse_time)

# Start modeling   
# GBM 
response="C1" #1:1000 imbalance
predictors=c(4:ncol(data.hex))

# Start modeling   
# Gradient Boosted Trees
gbm_time <- system.time(mdl.gbm <- h2o.gbm(x=predictors, y=response, training_frame=data.hex, distribution = "AUTO"))
mdl.gbm
print("Time it took to build GBM")
print(gbm_time)

}

doTest("Test", test)

