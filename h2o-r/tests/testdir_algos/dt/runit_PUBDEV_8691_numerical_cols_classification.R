setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.compare.classification.with.r <- function() {
    data <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/sdt/sdt_5numCols_10kRows.csv")
    data["response"] <- as.factor(data["response"])
    splits = h2o.splitFrame(data, ratios=c(0.8), seed=12345)
    train = splits[[1]]
    valid = splits[[2]]
    train_accuracy <- c()
    val_accuracy <- c()

    allDepth <- c(2, 5, 7, 10, 15, 17, 20)
    for (depthLevel in allDepth) {
        h2o_dt <- h2o.decisionTree(y="response", x = c(1:2), training_frame=train, max_depth=depthLevel)
        h2o_pred_valid <- h2o.predict(h2o_dt, valid)$predict
        h2o_pred <- h2o.predict(h2o_dt, train)$predict
        print(h2o.predict(h2o_dt, train))
        val_accuracy <- append(val_accuracy, sum(h2o_pred_valid==valid$response)/h2o.nrow(valid))
        train_accuracy <- append(train_accuracy, sum(h2o_pred==train$response)/h2o.nrow(train))
    }
    print("depths:")
    print(allDepth)
    print("train accuracies:")
    print(train_accuracy)
    print("validation accuracies:")
    print(val_accuracy)
    # check if training accuracy increases with depth
    expect_true(all(sort(train_accuracy, decreasing = FALSE) == train_accuracy))
}

doTest("Decision tree: numerical predictors only for binary classification.  Compare performance with R.", test.compare.classification.with.r)
