setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.leaf_node <- function() {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  Log.info(paste0("runif seed: ",seed))
  s <- h2o.runif(cars, seed=seed)
  train <- cars[s > .2,]
  valid <- cars[s <= .2,]

  # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
  # 2:multinomial
  problem <- sample(0:2,1)

  # pick the predictors and response column
  predictors <- c("displacement","power","weight","acceleration","year")
  if ( problem == 1 ) {
    response_col <- "economy_20mpg"
    distribution <- "bernoulli"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else if ( problem == 2 ) {
    response_col <- "cylinders"
    distribution <- "multinomial"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else {
    distribution <- "gaussian"
    response_col <- "economy"
  }

  # XGBoost can't handle NAs in the response column
  print("before impute: ")
  print(train)
  h2o.impute(train, column=response_col, method="mode")
  print("after impute: ")
  print(train)
  h2o.impute(valid, column=response_col, method="mode")

  print(paste0("Response column: ",response_col))

  ntrees1=sample(5:20,1)
  max_depth1=sample(2:5,1)
  min_rows1=sample(10:15,1)
  print(paste0("ntrees model 1: ",ntrees1))
  print(paste0("max_depth model 1: ",max_depth1))
  print(paste0("min_rows model 1: ",min_rows1))
  model1 <- h2o.xgboost(x=predictors,y=response_col,training_frame=train,ntrees=ntrees1,max_depth=max_depth1,
                    min_rows=min_rows1,validation_frame=valid,distribution=distribution)
  preds1 <- h2o.predict(model1, train)
  print(preds1)
  preds2 <- h2o.predict_leaf_node_assignment(model1, train)
  print(preds2)
}

doTest("Test XGBoost predict_leaf_node_assignment", test.leaf_node)
