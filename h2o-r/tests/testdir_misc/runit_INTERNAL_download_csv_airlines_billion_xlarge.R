setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

    hdfs_name_node = HADOOP.NAMENODE
    hdfs_airlines_file = "/datasets/airlinesbillion.csv"
    url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_airlines_file)

    print("Importing airlinesbillion...")
    airlines_billion <- h2o.importFile(url)
    airlines_billion[,31] <- as.factor(airlines_billion[,31])

    print("Building small GBM model to predict with...")
    gbm <- h2o.gbm(x=1:30, y=31, training_frame=airlines_billion, ntrees=1, distribution="bernoulli", max_depth=1)

    print("Predicting...")
    predictions1 <- h2o.predict(gbm, airlines_billion)

    print("Downloading predictions as csv...")
    library(R.utils)
    myFile <- paste(h2oTest.sandbox(), "delete_this_file.csv", sep = .Platform$file.sep)
    h2o.downloadCSV(predictions1, myFile)

    #predictions2 <- h2o.uploadFile(myFile)
    #
    #r1 <- nrow(predictions1)
    #print("Number of rows of predictions frame 1:")
    #print(r1)
    #
    #c1 <- ncol(predictions1)
    #print("Number of cols of predictions frame 1:")
    #print(c1)
    #
    #r2 <- nrow(predictions2)
    #print("Number of rows of predictions frame 2:")
    #print(r2)
    #
    #c2 <- ncol(predictions2)
    #print("Number of cols of predictions frame 2:")
    #print(c2)
    #
    #expect_equal(r1, r2, info="Expected the same number of rows")
    #expect_equal(c1, c2, info="Expected the same number of cols")

}

h2oTest.doTest("Test",rtest)
