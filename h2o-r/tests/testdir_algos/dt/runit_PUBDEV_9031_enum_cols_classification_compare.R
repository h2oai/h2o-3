setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

library(rpart)
# this test aims to compare H2O decision tree performance with that of R package
test.compare.classification.with.r.enum <- function() {
  data <-
    h2o.importFile(
        locate("smalldata/sdt/sdt_5EnumCols_10KRows_binomial.csv")
    )
  data["response"] <- as.factor(data["response"])
  dataR <- as.data.frame(data)
  logloss_h2o <- c()
  allDepth <- c(2:11)
  for (depthLevel in allDepth) {
    h2o_dt <-
      h2o.decision_tree(
        y = "response",
        x = c(1:5),
        training_frame = data,
        max_depth = depthLevel
      )
    h2o_pred <- h2o.predict(h2o_dt, data)$predict
    logloss_h2o <-
      c(logloss_h2o,
        h2o_dt@model$training_metrics@metrics$logloss[1])
    train_accuracy <- sum(h2o_pred == data$response) / h2o.nrow(data)
    #train_accuracy <- (h2o_dt@model$training_metrics@metrics$cm$table[1,1]+h2o_dt@model$training_metrics@metrics$cm$table[2,2])/h2o.nrow(data)
    print("tree depth is ")
    print(depthLevel)
    print("H2O training accuracy")
    print(train_accuracy)
    r_dt <-
      rpart(
        response ~ .,
        data = dataR,
        method = "class",
        parms = list(split = "information"),
        control = rpart.control(maxdepth = depthLevel - 1)
      )
    r_predict <- predict(r_dt, dataR, type = 
                           "class")
    r_train_accuracy <- sum(r_predict == dataR$response) / h2o.nrow(data)
    print("R training accuracy")
    print(r_train_accuracy)
    expect_true(train_accuracy >= r_train_accuracy ||
                    abs(train_accuracy - r_train_accuracy) < 0.1)
  }
  #check and make sure logloss is decreasing
  count <- 2
  print("logloss")
  print(logloss_h2o)
  for (logl in logloss_h2o) {
    expect_true(logl >= logloss_h2o[count])
    count <- count + 1
    if (count > length(logloss_h2o))
      break
  }
}


doTest("Decision tree: enum predictors only for binary classification. Compare performance with R.", test.compare.classification.with.r.enum)
