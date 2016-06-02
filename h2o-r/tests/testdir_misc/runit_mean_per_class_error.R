setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.mean_per_class_error <- function() {
  Log.info("Testing binomial mean_per_class_error")

  train = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="train")
  test = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="test")

  ### BINOMIAL
  predictors = 3:9
  response = 2
  train[,response] <- as.factor(train[,response])
  model = h2o.gbm(x=predictors,y=response,distribution = "bernoulli",training_frame=train,
                  ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)

  ## Get mean per class error from the model on training set
  mpce1 <- h2o.performance(model, train)@metrics$mean_per_class_error
  expect_true(mpce1==h2o.mean_per_class_error(model, train=TRUE))
  expect_true(mpce1==h2o.mean_per_class_error(h2o.performance(model, train=TRUE)))
  expect_true(mpce1==h2o.mean_per_class_error(h2o.performance(model, newdata=train)))

  ## Get mean per class error from model metrics after predicting on test set (same as training set)
  mpce2 <- h2o.performance(model, test)@metrics$mean_per_class_error

  MeanPerClassError <- function(cm) {
    error <- cm$Error
    mean(error[1:length(error)-1])
  }

  test3 = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="test3")
  mpce3 <- MeanPerClassError(h2o.confusionMatrix(h2o.performance(model, newdata=test3)))

  print(mpce1)
  print(mpce2)
  print(mpce3)

  expect_true(abs(mpce1-mpce2)<1e-6)
  expect_true(abs(mpce1-mpce3)<1e-6)



  ### MULTINOMIAL

  predictors = c(2:3,5:9)
  response = 4
  train[,response] <- as.factor(train[,response])
  model = h2o.gbm(x=predictors,y=response,distribution = "multinomial",training_frame=train,
                  ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)

  ## Get mean per class error from the model on training set
  mpce1 <- h2o.mean_per_class_error(h2o.performance(model, train))

  ## Get mean per class error from model metrics after predicting on test set (same as training set)
  mpce2 <- h2o.mean_per_class_error(h2o.performance(model, test))


  test3 = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="test3")
  actual <- as.numeric(test3[,response])
  pred <- predict(model,test3)

  mpce3 <- MeanPerClassError(h2o.confusionMatrix(h2o.performance(model, newdata=test3)))
  print(mpce1)
  print(mpce2)
  print(mpce3)

  expect_true(abs(mpce1-mpce2)<1e-6)
  expect_true(abs(mpce1-mpce3)<1e-6)

  
}

doTest("Test mean_per_class_error computation", test.mean_per_class_error)
