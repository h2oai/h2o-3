#----------------------------------------------------------------------
# Purpose:  This test exercises building GLM/GBM/DL  model 
#             
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

heading("BEGIN TEST")
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

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
  
PASS_BANNER()
