
#----------------------------------------------------------------------
# Purpose:  This test exercises building a GLRM model on numeric
#           data with 15M rows and 2.2K cols.
#----------------------------------------------------------------------
test <-
function() {
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

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
parse_time <- system.time(data.hex <- h2o.importFile(url))
print(paste("Time it took to parse:", parse_time))

response <- 1     # 1:1000 imbalance
predictors <- c(3:ncol(data.hex))

print("Running GLRM on frame with quadratic loss and no regularization")
aat <- system.time(myframe.glrm <- h2o.glrm(training_frame=data.hex, cols=predictors, k=k_dim, init="PlusPlus", loss="Quadratic", regularization_x="None", regularization_y="None", max_iterations=100))
print(myframe.glrm)
algo_run_time <- as.numeric(aat[3])
print(paste("Time it took to build model:", algo_run_time))

}

doTest("Test", test)

