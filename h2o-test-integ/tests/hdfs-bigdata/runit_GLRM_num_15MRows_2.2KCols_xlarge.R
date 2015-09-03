
#----------------------------------------------------------------------
# Purpose:  This test exercises building a GLRM model on numeric
#           data with 15M rows and 2.2K cols.
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

hdfs_data_file = "/datasets/15Mx2.2k.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Data frame size
rows <- 15e6
cols <- 2200
k_dim <- 15
print(paste("Matrix decomposition rank k =", k_dim))

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(conn, url))
print(paste("Time it took to parse:", parse_time))

response <- 1     # 1:1000 imbalance
predictors <- c(3:ncol(data.hex))

print("Running GLRM on frame with quadratic loss and no regularization")
aat <- system.time(myframe.glrm <- h2o.glrm(training_frame=data.hex, x=predictors, k=k_dim, init="PlusPlus", loss="L2", regularization_x="None", regularization_y="None", max_iterations=100))
print(myframe.glrm)
algo_run_time <- as.numeric(aat[3])
print(paste("Time it took to build model:", algo_run_time))

PASS_BANNER()

