
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
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

hdfs_data_file = "/datasets/bigdata/7MRows_4400KCols.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(conn, url))
print("Time it took to parse")
print(parse_time)

# Start modeling   
# GLM gaussian
response="C1" #1:1000 imbalance
predictors=c(3:ncol(data.hex))

glm_time <- system.time(mdl.glm <- h2o.glm(x=predictors, y=response, training_frame=data.hex, family="gaussian", solver = "L_BFGS"))
mdl.glm
print("Time it took to build GLM")
print(glm_time)

# GLM binomial
response="C2" #1:1000 imbalance
predictors=c(4:ncol(data.hex))

glm2_time <- system.time(mdl2.glm <- h2o.glm(x=predictors, y=response, training_frame=data.hex, family="binomial", solver = "L_BFGS"))
mdl2.glm
print("Time it took to build GLM")
print(glm2_time)


PASS_BANNER()

