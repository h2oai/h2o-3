#----------------------------------------------------------------------
# Purpose:  This test exercises building GLM/GBM/DL  model 
#           for 376K rows and 6.9K columns 
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
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

hdfs_data_file = "/datasets/bigdata/5MRows6KCols.csv
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(conn, url))
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

PASS_BANNER()
