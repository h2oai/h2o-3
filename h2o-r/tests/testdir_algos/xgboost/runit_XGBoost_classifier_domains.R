setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.xgboost.mult.levels <- function() {
  expect_true(h2o.xgboost.available())

  print("build iris with string levels:")
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")

  # iris XGBoost
  iris.xgboost <- h2o.xgboost(model_id = "xgboost_iris",
                          x = 1:4,
                          y = 5,
                          training_frame = iris.train)

  print("training_metrics for iris: ")
  print(iris.xgboost@model$training_metrics)
  print("training cm for iris: ")
  print(h2o.confusionMatrix(iris.xgboost@model$training_metrics))

  table = iris.xgboost@model$training_metrics@metrics$cm$table
  domain = rownames(table)[1:length(rownames(table)) - 1]
  print("domain for iris XGBoost: ")
  print(domain)
  print("number of levels: ")
  print(length(domain))
  expect_equal(length(domain), 3)
  expect_equal(domain[1], "Iris-setosa")


  print("build iris with numeric levels:")
  iris_numeric_levels.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader_numeric_levels.csv"))
  iris_numeric_levels.hex[,5] <- as.factor(iris_numeric_levels.hex[,5])
  i.sid <- h2o.runif(iris_numeric_levels.hex)
  iris_numeric_levels.train <- h2o.assign(iris_numeric_levels.hex[i.sid > .2, ], "iris_numeric_levels.train")
  iris_numeric_levels.xgboost <- h2o.xgboost(model_id = "xgboost_iris_numeric_levels",
                          x = 1:4,
                          y = 5,
                          training_frame = iris_numeric_levels.train)

  print("training_metrics for iris: ")
  print(iris_numeric_levels.xgboost@model$training_metrics)
  print("training cm for iris: ")
  print(h2o.confusionMatrix(iris_numeric_levels.xgboost@model$training_metrics))

  table = iris_numeric_levels.xgboost@model$training_metrics@metrics$cm$table
  domain = rownames(table)[1:length(rownames(table)) - 1]
  print("domain for iris_numeric_levels XGBoost: ")
  print(domain)
  print("number of levels: ")
  print(length(domain))
  expect_equal(length(domain), 3)
  expect_equal(domain[2], "1")


  print("build prostate with numeric levels:")
  prostate.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  prostate.hex[,"CAPSULE"] <- as.factor(prostate.hex[,"CAPSULE"])

  y <- "CAPSULE"
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  prostate.xgboost <- h2o.xgboost(model_id = "xgboost_prostate",
                              x = x, y = y, 
                              training_frame = prostate.hex, 
                              ntrees = 50, 
                              distribution = "multinomial")
  
  print("training_metrics for prostate: ")
  print(prostate.xgboost@model$training_metrics)
  print("training cm for prostate: ")
  print(h2o.confusionMatrix(prostate.xgboost@model$training_metrics))
  
  domain = prostate.xgboost@model$training_metrics@metrics$domain
  print("domain for prostate XGBoost: ")
  print(domain)
  print("number of levels: ")
  print(length(domain))
  expect_equal(length(domain), 2)
  expect_equal(domain[2], "1")


  print("build prostate with string levels (dead/alive):")
  prostate_string_levels.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate_string_levels.csv"))
  prostate_string_levels.hex[,"CAPSULE"] <- as.factor(prostate_string_levels.hex[,"CAPSULE"])

  y <- "CAPSULE"
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  prostate_string_levels.xgboost <- h2o.xgboost(model_id = "xgboost_prostate",
                              x = x, y = y, 
                              training_frame = prostate_string_levels.hex, 
                              ntrees = 50, 
                              distribution = "multinomial")
  
  print("training_metrics for prostate: ")
  print(prostate_string_levels.xgboost@model$training_metrics)
  print("training cm for prostate: ")
  print(h2o.confusionMatrix(prostate_string_levels.xgboost@model$training_metrics))

  domain = prostate_string_levels.xgboost@model$training_metrics@metrics$domain
  print("domain for prostate_string_levels XGBoost: ")
  print(domain)
  print("number of levels: ")
  print(length(domain))
  expect_equal(length(domain), 2)
  expect_equal(domain[2], "dead")

}

doTest("Testing categorical levels for XGBoost classifier models", test.xgboost.mult.levels)
