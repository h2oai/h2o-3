setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)


test.uplift <- function() {
  ntrees <- 10
  mtries <- 6
  seed <- 42
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
  
  expected_values_auuc_qini <- c(66.10928, 85.58292, 60.71382)
  expected_values_auuc_gain <- c(128.6424, 162.0204, 121.7822) 
  expected_values_auuc_lift <- c(0.2125203, 0.2605544, 0.2046642)
  
  expected_values_aecu_qini <- c(82.12111, 101.5937, 76.73942)
  expected_values_aecu_gain <- c(160.6661, 194.0419, 153.8334)
  expected_values_aecu_lift <- c(0.2285321, 0.2765652, 0.2206898)
    
  expected_values_auuc_norm_qini <- c(2.065915, 2.674466, 1.897307)
  expected_values_auuc_norm_gain <- c(2.010038, 2.531569, 1.902846)
  expected_values_auuc_norm_lift <- c(0.2125203, 0.2605544, 0.2046642)
    
  for (i in 1:length(uplift_metrics)) {
    print(paste("Train h2o uplift model", uplift_metrics[i]))
    model <- h2o.upliftRandomForest(
        x = c("X1", "X2", "X3", "X4", "X5", "X6"),
        y = "y",
        training_frame = trainh2o,
        validation_frame = testh2o,
        treatment_column = "treat",
        uplift_metric = uplift_metrics[i],
        auuc_type = "qini",
        distribution = "bernoulli",
        ntrees = ntrees,
        mtries = mtries,
        max_depth = 10,
        min_rows = 10,
        nbins = 100,
        seed = seed) 
        
    # test model metrics
    print("Test model metrics")
    auuc <- h2o.auuc(model, train=TRUE, valid=TRUE)
    print(auuc) 
    qini <- h2o.qini(model, train=TRUE, valid=TRUE)
    print(qini)
    aecu <- h2o.aecu(model, train=TRUE, valid=TRUE)
    print(aecu)
    auuc_normalized <- h2o.auuc_normalized(model, train=TRUE, valid=TRUE)
    print(auuc_normalized)  
       
    # test performance 
    print("Test performance metrics")
    perf <- h2o.performance(model)
    auuc <- h2o.auuc(perf)  
    print(auuc)
    auuc_qini <- h2o.auuc(perf, metric="qini")
    print(auuc_qini)
    auuc_gain <- h2o.auuc(perf, metric="gain")
    print(auuc_gain)
    auuc_lift <- h2o.auuc(perf, metric="lift")
    print(auuc_lift)
      
    auuc_table <- h2o.auuc_table(perf)
    print(auuc_table)
      
    qini <- h2o.qini(perf)
    print(qini)
      
    aecu_qini <- h2o.aecu(perf, metric="qini")
    print(aecu_qini)
    aecu_gain <- h2o.aecu(perf, metric="gain")
    print(aecu_gain)
    aecu_lift <- h2o.aecu(perf, metric="lift")
    print(aecu_lift)
      
    aecu_table <- h2o.aecu_table(perf)
    print(aecu_table)
    print(h2o.thresholds_and_metric_scores(perf))

    auuc_norm <- h2o.auuc_normalized(perf)
    print(auuc_norm)
    auuc_qini_norm <- h2o.auuc_normalized(perf, metric="qini")
    print(auuc_qini_norm)
    auuc_gain_norm <- h2o.auuc_normalized(perf, metric="gain")
    print(auuc_gain_norm)
    auuc_lift_norm <- h2o.auuc_normalized(perf, metric="lift")
    print(auuc_lift_norm)  
    
    expect_equal(auuc, auuc_qini, tolerance=1e-6)
    expect_equal(auuc, expected_values_auuc_qini[i], tolerance=1e-6)
    expect_equal(auuc_gain, expected_values_auuc_gain[i], tolerance=1e-6)
    expect_equal(auuc_lift, expected_values_auuc_lift[i], tolerance=1e-6)   
    expect_equal(qini, aecu_qini, tolerance=1e-6) 
    expect_equal(aecu_qini, expected_values_aecu_qini[i], tolerance=1e-6) 
    expect_equal(aecu_gain, expected_values_aecu_gain[i], tolerance=1e-6) 
    expect_equal(aecu_lift, expected_values_aecu_lift[i], tolerance=1e-6)

    expect_equal(auuc_norm, auuc_qini_norm, tolerance=1e-6)
    expect_equal(auuc_norm, expected_values_auuc_norm_qini[i], tolerance=1e-6)
    expect_equal(auuc_gain_norm, expected_values_auuc_norm_gain[i], tolerance=1e-6)
    expect_equal(auuc_lift_norm, expected_values_auuc_norm_lift[i], tolerance=1e-6)

    plot(perf)
    plot(perf, normalize=TRUE)  
  }
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
