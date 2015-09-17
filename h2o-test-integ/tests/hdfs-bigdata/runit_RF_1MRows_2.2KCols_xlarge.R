
#----------------------------------------------------------------------
# Purpose:  This test exercises building 15MRows2KCols
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

PASS_BANNER()

