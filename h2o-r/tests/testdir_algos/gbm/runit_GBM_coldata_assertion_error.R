# setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.gbm.coldata.assertion_error <- function () {
    data <- h2o.importFile(locate("smalldata/jira/pubdev_3847.csv"), destination_frame = "data")
    myY <- "class"
    myX <- setdiff(names(data), myY)

    ntrees <- 100
    max_depth <- 6
    min_rows <- 5
    learn_rate <- 0.1
    sample_rate <- 0.8
    col_sample_rate_per_tree <- 0.6
    nfolds <- 2
    min_split_improvement <- 1e-04

    for (i in 1 : 100) {
        print(paste0("###### Attempt #", i, ": ######"))
        model <- h2o.gbm(x = myX, y = myY, training_frame = data, model_id = "amodel", ntrees = ntrees,
        max_depth = max_depth , min_rows = min_rows, learn_rate = learn_rate,
        sample_rate = sample_rate , col_sample_rate_per_tree = col_sample_rate_per_tree ,
        nfolds = nfolds, min_split_improvement = min_split_improvement)
    }
}

# doTest("Testing GBM Coldata, there should not be an AssertionError", test.gbm.coldata.assertion_error)
test.gbm.coldata.assertion_error()

