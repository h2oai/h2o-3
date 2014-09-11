#----------------------------------------------------------------------
# Purpose:  This test exercises the DRF2 model downloaded as java code
#           for the churn data set while randomly setting the parameters.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------
    
options(echo=TRUE)    
TEST_ROOT_DIR <- ".."
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source(paste(TEST_ROOT_DIR, "findNSourceUtils.R"  , sep="/"))
    
# Check if we are running inside the 0xdata network by seeing if we can touch
# the cdh3 namenode. (we're not using it though..using automount to the nas)
# stay consistent with the check we use for hdfs tests
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://192.168.1.176:80", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "192.168.1.176"
} else {
    stop("Not running on 0xdata internal network. Assume no access to /mnt/0xcustomer-datasets.")
}

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# q(save="no")

heading("Choose random parameters")

ntree <- sample(100, 1)
print(paste("ntrees", ntree))
    
depth <- sample(20, 1)
print(paste("depth", depth))
    
nodesize <- sample(10, 1)
print(paste("nodesize", nodesize))

data_dir = "/mnt/0xcustomer-datasets/c10"
train <- paste(data_dir, "churn_train.csv", sep="/")
print(paste("train", train))
    
test <- paste(data_dir, "churn_test.csv", sep="/")
print(paste("test", test))
    
y <- "churn"
print(paste("y", y))

if (! file.exists(train)) {
    cat("\n")
    cat("\n")
    stop(sprintf("Data file not available (%s).", train))
}

# Remove Customer_ID because it's the right thing to do when building the model.
# Remove last_swap because it doesn't parse properly with date format.
# Remove occu1 because it doesn't parse properly with mixed char/num format.
df <- read.csv(train, nrows=1);
x <- setdiff(colnames(df), c(y, "Customer_ID", "last_swap", "occu1"))
print("x")
print(x)    


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_SRF.R')
