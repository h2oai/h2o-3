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

  print(h2o.mean_per_class_accuracy(h2o.performance(model,train), thresholds=c(0.3,0.5)))
  print(h2o.mean_per_class_accuracy(h2o.performance(model,train)))



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


  gbm4 <- h2o.gbm(x=predictors,y=response,distribution = "multinomial",training_frame=train,
                  ntrees=10000,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20, score_tree_interval=1,
                  stopping_rounds = 2, stopping_metric = "mean_per_class_error")

  print(gbm4@model$scoring_history)

  expect_true(gbm4@model$model_summary$number_of_trees < 10000)


  hyper_params = list(
      ## restrict the search to the range of max_depth established above
      max_depth = seq(1,10,1),

      ## search a large space of row sampling rates per tree
      sample_rate = seq(0.2,1,0.01),

      ## search a large space of column sampling rates per split
      col_sample_rate = seq(0.2,1,0.01),

      ## search a large space of column sampling rates per tree
      col_sample_rate_per_tree = seq(0.2,1,0.01),

      ## search a large space of how column sampling per split should change as a function of the depth of the split
      col_sample_rate_change_per_level = seq(0.9,1.1,0.01),

      ## search a large space of the number of min rows in a terminal node
      min_rows = 2^seq(0,log2(nrow(train))-2,1),

      ## search a large space of the number of bins for split-finding for continuous and integer columns
      nbins = 2^seq(4,10,1),

      ## search a large space of the number of bins for split-finding for categorical columns
      nbins_cats = 2^seq(4,12,1),

      ## search a few minimum required relative error improvement thresholds for a split to happen
      min_split_improvement = c(0,1e-8,1e-6,1e-4),

      ## try all histogram types (QuantilesGlobal and RoundRobin are good for numeric columns with outliers)
      histogram_type = c("UniformAdaptive","QuantilesGlobal","RoundRobin")
    )

    search_criteria = list(
      ## Random grid search
      strategy = "RandomDiscrete",

      ## limit the runtime to 10 minutes
      max_runtime_secs = 600,

      ## build no more than 10 models
      max_models = 10,

      ## random number generator seed to make sampling of parameter combinations reproducible
      seed = 1234,

      ## early stopping once the leaderboard of the top 5 models is converged to 0.1% relative difference in mean_per_class_error
      stopping_rounds = 5,
      stopping_metric = "mean_per_class_error",
      stopping_tolerance = 1e-3
    )

    grid <- h2o.grid("gbm", grid_id="mygrid", 
                         x=predictors, y=response,
                         distribution="multinomial",
                         seed=1234,
                         training_frame = train,
                         nfolds=3, # for early stopping
                         ntrees=1000,
                         hyper_params = hyper_params,
                         #each model stops early based on logloss
                         stopping_rounds = 10, stopping_metric = "logloss", stopping_tolerance=1e-3,
                         search_criteria = search_criteria)

    print(grid)
    print(h2o.getGrid("mygrid",sort_by="mean_per_class_error",decreasing=FALSE))
  
}

doTest("Test mean_per_class_error computation", test.mean_per_class_error)
