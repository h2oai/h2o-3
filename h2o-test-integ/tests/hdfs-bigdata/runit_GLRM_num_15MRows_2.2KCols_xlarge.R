
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

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Data frame size
rows <- 15e6
cols <- 2200
k_dim <- 15
print(paste("Matrix decomposition rank k =", k_dim))

print(paste("Creating numeric data frame with rows =", rows, "and cols =", cols))
sst <- system.time(myframe <- h2o.createFrame(conn, 'myframe', rows = rows, cols = cols, 
                                              randomize = TRUE, real_range = 100, categorical_fraction = 0.0, 
                                              integer_fraction = 0.0, binary_fraction = 0.0, 
                                              missing_fraction = 0, has_response = FALSE))

create_frm_time <- as.numeric(sst[3])
print(paste("Time it took to create frame:", create_frm_time))

print("Running GLRM on frame with quadratic loss and no regularization")
aat <- system.time(myframe.glrm <- h2o.glrm(training_frame=myframe, k=k_dim, init="PlusPlus", loss="L2", regularization_x="None", regularization_y="None", max_iterations=100))
print(myframe.glrm)
algo_run_time <- as.numeric(aat[3])
print(paste("Time it took to build model:", algo_run_time))

myframe <- NULL
gc()
h2o.rm(conn,"myframe")

PASS_BANNER()

