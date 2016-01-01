setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.logloss <- function() {
  h2oTest.logInfo("Testing binomial logloss")

  train = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="train")
  test = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="test")

  ## Compute LogLoss explicitly from predictions on the test set
  LogLoss <- function(act, pred) {
    for (i in 0:max(act)) {
      act[,2+i] = act[,1]==i
    }
    ll <- act[,-1]*log(pred)
    ll <- -sum(ll)/nrow(act)
    ll
  }

  ### BINOMIAL

  predictors = 3:9
  response = 2
  train[,response] <- as.factor(train[,response])
  model = h2o.gbm(x=predictors,y=response,distribution = "bernoulli",training_frame=train,
                  ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)

  ## Get LogLoss from the model on training set
  ll1 <- h2o.performance(model, train)@metrics$logloss

  ## Get LogLoss from model metrics after predicting on test set (same as training set)
  ll2 <- h2o.performance(model, test)@metrics$logloss


  test3 = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="test3")
  actual <- as.numeric(test3[,response])
  pred <- predict(model,test3)

  ll3 <- LogLoss(actual, pred[,-1])
  print(ll1)
  print(ll2)
  print(ll3)

  expect_true(abs(ll1-ll2)<1e-6)
  expect_true(abs(ll1-ll3)<1e-6)



  ### MULTINOMIAL

  predictors = c(2:3,5:9)
  response = 4
  train[,response] <- as.factor(train[,response])
  model = h2o.gbm(x=predictors,y=response,distribution = "multinomial",training_frame=train,
                  ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)

  ## Get LogLoss from the model on training set
  ll1 <- h2o.performance(model, train)@metrics$logloss

  ## Get LogLoss from model metrics after predicting on test set (same as training set)
  ll2 <- h2o.performance(model, test)@metrics$logloss


  test3 = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="test3")
  actual <- as.numeric(test3[,response])
  pred <- predict(model,test3)

  ll3 <- LogLoss(actual, pred[,-1])
  print(ll1)
  print(ll2)
  print(ll3)

  expect_true(abs(ll1-ll2)<1e-6)
  expect_true(abs(ll1-ll3)<1e-6)

  
}

h2oTest.doTest("Test logloss computation", test.logloss)
