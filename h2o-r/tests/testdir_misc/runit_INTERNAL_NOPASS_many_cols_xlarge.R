


rtest <- function() {

hdfs_name_node = hadoop.namenode()
hdfs_data_file = "/datasets/1Mx2.2k.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
data.hex <- h2o.importFile(url)
    
response=1 #1:1000 imbalance
predictors=c(3:ncol(data.hex))
   
# Start modeling   
   
# Gradient Boosted Trees
mdl.gbm <- h2o.gbm(x=predictors, y=response, training_frame=data.hex, distribution = "bernoulli")
mdl.gbm
  
}

doTest("Test",rtest)
