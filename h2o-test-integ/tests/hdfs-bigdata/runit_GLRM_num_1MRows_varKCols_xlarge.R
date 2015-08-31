
#----------------------------------------------------------------------
# Purpose:  This test exercises building a GLRM model on numeric
#           data with 1M rows and 2K, 5K, and 10K cols.
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
rows <- c(1e6)
# cols <- c(2e3,5e3,10e3)
cols <- c(2e3)
k_dim <- 15
print(paste("Matrix decomposition rank k =", k_dim))

create_frm_time <- matrix(NA, nrow = length(rows), ncol = length(cols))
algo_run_time <- matrix(NA, nrow = length(rows), ncol = length(cols))
col_grid <- rep(NA,length(cols))
row_grid <- rep(NA,length(rows))
names <- c()

# Start modeling   
# Generalized Low Rank Models
for(i in 1:length(rows)) { # changing number of rows
  nrows <- rows[i]
  row_grid[i] <- nrows
  
  for(j in 1:length(cols)) { # changing number of columns
    ncols <- cols[j]
    col_grid[j] <- ncols
    names <- c(names, nrows * ncols)  # set the name to be the problem size
    
    print(paste("Creating numeric data frame with rows =", rows[i], "and cols =", cols[j]))
    sst <- system.time(myframe <- h2o.createFrame(conn, 'myframe', rows = nrows, cols = ncols, 
                                                  randomize = TRUE, real_range = 100, categorical_fraction = 0.0, 
                                                  integer_fraction = 0.0, binary_fraction = 0.0, 
                                                  missing_fraction = 0, has_response = FALSE))
    create_frm_time[i,j] <- as.numeric(sst[3])
    print(paste("Time it took to create frame:", create_frm_time[i,j]))
    
    print("Running GLRM on frame with quadratic loss and no regularization")
    aat <- system.time(myframe.glrm <- h2o.glrm(training_frame=myframe, k=k_dim, init="PlusPlus", loss="L2", regularization_x="None", regularization_y="None", max_iterations=100))
    algo_run_time[i,j] <- aat[3]
    print(myframe.glrm)
    print(paste("Time it took to build model:", algo_run_time[i,j]))
  }
}

myframe <- NULL
gc()
h2o.rm(conn,"myframe")

# format timing data from h2o
print("GLRM runtime summary")
data <- algo_run_time
dimnames(data) <- list(row_grid,col_grid)
print(data)

PASS_BANNER()

