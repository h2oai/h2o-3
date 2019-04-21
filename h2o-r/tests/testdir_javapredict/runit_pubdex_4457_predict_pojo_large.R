setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#----------------------------------------------------------------------
# Test and make sure h2o predict is working properly as specified
# in PUBDEV-4457.  Runit test derived from Nidhi template.
#----------------------------------------------------------------------
test <-
  function() {
    h2o.removeAll()
    set.seed(12345)

    # these datasets are generated using Nidhi code which will fail without Tomas fix.
    train.hex <- h2o.importFile(locate("bigdata/laptop/jira/df_train.csv.zip"),
    destination_frame = "train.hex")
    test.hex <- h2o.importFile(locate("bigdata/laptop/jira/df_test.csv.zip"),
    destination_frame = "test.hex")

    predictors <- setdiff(colnames(train.hex), "response")
    params <- list()
    params$ntrees <- 10
    params$max_depth <- 10
    params$x <- predictors
    params$y <- "response"
    params$training_frame <-train.hex
    params$learn_rate <- 0.01
    params$sample_rate <- 1
    params$col_sample_rate <- 1
    params$col_sample_rate_per_tree <- 1
    params$seed <- 12345

    doJavapredictTest("gbm", locate("bigdata/laptop/jira/df_test.csv.zip"), test.hex, params)
    }


doTest("pubdev-4457: PredictCsv test", test)