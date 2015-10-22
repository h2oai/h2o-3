
#----------------------------------------------------------------------
# Purpose:  This test exercises building a GLRM model on categorical
#           data with 1M rows and 100 cols of 50 unique levels each.
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

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Data frame size
rows <- 1e6
cols <- 100
levels <- 50
k_dim <- 15
print(paste("Matrix decomposition rank k =", k_dim))

print(paste("Creating categorical data frame with rows =", rows, "and cols =", cols, "with", levels, "unique levels each"))
sst <- system.time(myframe <- h2o.createFrame('myframe', rows = rows, cols = cols, 
                                              randomize = TRUE, categorical_fraction = 1.0, factors = levels,
                                              integer_fraction = 0.0, binary_fraction = 0.0, 
                                              missing_fraction = 0, has_response = FALSE))

print(paste("Time it took to create frame:", as.numeric(sst[3])))

print("Running GLRM on frame with one-versus-all multivariate loss and no regularization")
aat <- system.time(myframe.glrm <- h2o.glrm(training_frame=myframe, k=k_dim, init="PlusPlus", multi_loss="Categorical", regularization_x="None", regularization_y="None", max_iterations=100))
print(myframe.glrm)
print(paste("Time it took to build model:", aat[3]))

myframe <- NULL
gc()
h2o.rm("myframe")

PASS_BANNER()

