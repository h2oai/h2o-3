setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.compare.classification.with.r <- function() {
    data <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/sdt/sdt_5numCols_10kRows.csv")
    data["response"] <- as.factor(data["response"])
    splits = h2o.splitFrame(data, ratios=c(0.8), seed=12345)
    train = splits[[1]]
    valid = splits[[2]]

    allDepth <- c(10, 20)
    for (depthLevel in allDepth) {
        h2o_sdt <- h2o.sdt(y="response", x = c(1:2), training_frame=train, max_depth=depthLevel)
        h2o_pred_valid <- h2o.predict(h2o_sdt, valid)
        h2o_pred <- h2o.predict(h2o_sdt, train)
        acc_valid <- sum(h2o_pred_valid==valid$response)/h2o.nrow(valid)
        acc <- sum(h2o_pred==train$response)/h2o.nrow(train)
        expect_true(abs(acc-acc_valid) < 0.02)
    }
}

doTest("Decision tree: numerical predictors only for binary classification.  Compare performance with R.", test.compare.classification.with.r)
