setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.uplift.vs.h2oUplift <- function() {
  ntrees <- 10
  mtries <- 6
  seed <- 42
  # split_method == uplift_metric, the naming is specific for each implementation
  split_methods <- c( "KL", "Chisq", "ED")
  uplift_metrics <- c("KL", "ChiSquared", "Euclidean")
  set.seed(seed)

  # Test data preparation for each implementation
  train <- sim_pte(n = 2000, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
  train$treat <- ifelse(train$treat == 1, 1, 0)
  test <- sim_pte(n = 1000, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
  test$treat <- ifelse(test$treat == 1, 1, 0)

  trainh2o <- train
  trainh2o$treat <- as.factor(train$treat)
  trainh2o$y <- as.factor(train$y)
  trainh2o <- as.h2o(trainh2o)

  testh2o <- test
  testh2o$treat <- as.factor(test$treat)
  testh2o$y <- as.factor(test$y)
  testh2o <- as.h2o(testh2o)

  for (i in 1:length(split_methods)) {
    print(paste("Uplift fit model with metric", split_methods[i]))
    modelUplift <- upliftRF(y ~ X1 + X2 + X3 + X4 + X5 + X6 + trt(treat),
                            data = train,
                            split_method = split_methods[i],
                            mtry = mtries,
                            ntree = ntrees,
                            minsplit = 10,
                            min_bucket_ct0 = 10,
                            min_bucket_ct1 = 10,
                            verbose = TRUE,
                            seed = seed)

    print("Uplift model summary")
    print(summary(modelUplift))

    print("Uplift predict on test data")
    predUplift <- predict(modelUplift, test)

    upliftPerf <- performance(predUplift[, 1], predUplift[, 2], test$y, test$treat, direction = 1)
    upliftQini <- qini(upliftPerf)

    print("Train h2o uplift model")
    modelh2o <- h2o.upliftRandomForest(
      x = c("X1", "X2", "X3", "X4", "X5", "X6"),
      y = "y",
      training_frame = trainh2o,
      treatment_column = "treat",
      uplift_metric = uplift_metrics[i],
      auuc_type = "qini",
      distribution = "bernoulli",
      ntrees = ntrees,
      mtries = mtries,
      max_depth = 10,
      min_rows = 10,
      nbins = 100,
      seed = seed
    )

    print(modelh2o)

    # predict upliftRF on new data for treatment group
    print("H2O uplift predict on test data")
    predh2o <- predict(modelh2o, testh2o)

    res <- as.data.frame(predh2o)
    h2oPerf <- performance(res$p_y1_with_treatment, res$p_y1_without_treatment, test$y, test$treat, direction = 1)
    h2oQini <- qini(h2oPerf)

    print(paste("H2O:", h2oQini, "upliftRF:", upliftQini$Qini)) 
    diff = abs(h2oQini$Qini - upliftQini$Qini)
    print(paste("Diff:", diff))
    expect_true(diff < 10e-1)
  }
}

doTest("Uplift Random Forest Test: Test H2O RF uplift against uplift.upliftRF", test.uplift.vs.h2oUplift)
