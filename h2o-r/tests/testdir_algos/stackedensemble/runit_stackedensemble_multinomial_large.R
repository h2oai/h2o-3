setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.multinomial.test <- function() {

    # This test checks the following (for multinomial regression):
    #
    # 1) That h2o.stackedEnsemble executes w/o errors
    #    on a 6-model "manually constucted" ensemble.
    # 2) That h2o.predict works on a stack
    # 3) That h2o.performance works on a stack
    # 4) That the test performance is
    #    better on a ensemble vs the base learners.
    # 5) That the validation_frame arg on
    #    h2o.stackedEnsemble works correctly


    test_file <- locate("bigdata/laptop/mnist/test.csv.gz")
    df <- h2o.importFile(test_file)
    y <- "C785"
    x <- setdiff(names(df), y)
    df[,y] <- as.factor(df[,y])
    train <- df[1:5000,]
    test <- df[5001:10000,]
    # Number of CV folds (to generate level-one data for stacking)
    nfolds <- 2


    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "multinomial",
                      ntrees = 10,
                      nfolds = nfolds,
                      fold_assignment = "Modulo",
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)
    # Eval perf
    perf_gbm_train <- h2o.performance(my_gbm)
    perf_gbm_test <- h2o.performance(my_gbm, newdata = test)
    print("GBM training performance: ")
    print(perf_gbm_train)
    print("GBM test performance: ")
    print(perf_gbm_test)


    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 10,
                              nfolds = nfolds,
                              fold_assignment = "Modulo",
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)
    # Eval perf
    perf_rf_train <- h2o.performance(my_rf)
    perf_rf_test <- h2o.performance(my_rf, newdata = test)
    print("RF training performance: ")
    print(perf_rf_train)
    print("RF test performance: ")
    print(perf_rf_test)


    # Train & Cross-validate an XGBoost GBM
    my_xgb <- h2o.xgboost(x = x,
                          y = y,
                          training_frame = train,
                          ntrees = 10,
                          nfolds = nfolds,
                          fold_assignment = "Modulo",
                          keep_cross_validation_predictions = TRUE,
                          seed = 1)
    # Eval perf
    perf_xgb_train <- h2o.performance(my_xgb)
    perf_xgb_test <- h2o.performance(my_xgb, newdata = test)
    print("XGB training performance: ")
    print(perf_xgb_train)
    print("XGB test performance: ")
    print(perf_xgb_test)
    
    
    # Train & Cross-validate a Naive Bayes model
    my_nb <- h2o.naiveBayes(x = x,
                            y = y,
                            training_frame = train,
                            nfolds = nfolds,
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)
    # Eval perf
    perf_nb_train <- h2o.performance(my_nb)
    perf_nb_test <- h2o.performance(my_nb, newdata = test)
    print("NB training performance: ")
    print(perf_nb_train)
    print("NB test performance: ")
    print(perf_nb_test)
    
    
    # Train & Cross-validate a Deep Learning model
    my_dnn <- h2o.deeplearning(x = x,
                               y = y,
                               training_frame = train,
                               nfolds = nfolds,
                               hidden = c(10,10),
                               fold_assignment = "Modulo",
                               keep_cross_validation_predictions = TRUE,
                               seed = 1)
    # Eval perf
    perf_dnn_train <- h2o.performance(my_dnn)
    perf_dnn_test <- h2o.performance(my_dnn, newdata = test)
    print("DNN training performance: ")
    print(perf_dnn_train)
    print("DNN test performance: ")
    print(perf_dnn_test)
    
    
    # Train & Cross-validate a GLM model
    my_glm <- h2o.glm(x = x,
                      y = y,
                      family = "multinomial",
                      training_frame = train,
                      nfolds = nfolds,
                      fold_assignment = "Modulo",
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)
    # Eval perf
    perf_glm_train <- h2o.performance(my_glm)
    perf_glm_test <- h2o.performance(my_glm, newdata = test)
    print("GLM training performance: ")
    print(perf_glm_train)
    print("GLM test performance: ")
    print(perf_glm_test) 


    print("Train StackedEnsemble Model")
    # Train a stacked ensemble using the GBM and GLM above
    stack <- h2o.stackedEnsemble(x = x,
                                 y = y,
                                 training_frame = train,
                                 validation_frame = test,  #also test that validation_frame is working
                                 base_models = list(my_gbm, my_rf, my_xgb, my_nb, my_dnn, my_glm))
    expect_true( inherits(stack, "H2OMultinomialModel") )
    
    # Check that prediction works
    pred <- h2o.predict(stack, newdata = test)
    print(pred)
    expect_equal(nrow(pred), nrow(test))
    expect_equal(ncol(pred), 11)

    # Evaluate ensemble performance
    perf_stack_train <- h2o.performance(stack)
    expect_true( inherits(perf_stack_train, "H2OMultinomialMetrics") )
    perf_stack_valid <- h2o.performance(stack, valid = TRUE)  #uses same test set
    expect_true( inherits(perf_stack_valid, "H2OMultinomialMetrics") )
    perf_stack_test <- h2o.performance(stack, newdata = test)
    expect_true( inherits(perf_stack_test, "H2OMultinomialMetrics") )
    
    # Check that stack perf is better (smaller) than the best (smaller) base learner perf:
    # Test mean_per_class_error for each base learner
    baselearner_best_mean_per_class_error_test <- min(h2o.mean_per_class_error(perf_gbm_test), 
                                                      h2o.mean_per_class_error(perf_rf_test), 
                                                      h2o.mean_per_class_error(perf_xgb_test),
                                                      h2o.mean_per_class_error(perf_nb_test),
                                                      h2o.mean_per_class_error(perf_dnn_test),
                                                      h2o.mean_per_class_error(perf_glm_test))
    stack_mean_per_class_error_test <- h2o.mean_per_class_error(perf_stack_test)
    print(sprintf("Best Base-learner Test mean_per_class_error:  %s", baselearner_best_mean_per_class_error_test))
    print(sprintf("Stacked Ensemble Test mean_per_class_error:  %s", stack_mean_per_class_error_test))
    expect_equal(TRUE, stack_mean_per_class_error_test <= baselearner_best_mean_per_class_error_test)

    # Check that passing `test` as a validation_frame
    # produces the same metrics as h2o.performance(stack, test)
    # Since the metrics object is not exactly the same, we can just test that mean_per_class_error is the same
    perf_stack_validation_frame <- h2o.performance(stack, valid = TRUE)
    expect_identical(stack_mean_per_class_error_test, h2o.mean_per_class_error(perf_stack_validation_frame))
}

doTest("Stacked Ensemble Multinomial Test", stackedensemble.multinomial.test)
