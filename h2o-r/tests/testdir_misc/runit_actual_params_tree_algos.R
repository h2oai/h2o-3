setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

check.actual.parameters <- function(model) {
    print(model@algorithm)
    print(paste("ntrees from model_summary", model@model$model_summary$number_of_trees, ". Actual values of ntrees", model@params$actual$ntrees))
    expect_equal(model@model$model_summary$number_of_trees, model@params$actual$ntrees, info="ntrees from model_summary should be equal to actual ntrees")
    expect_equal(100,model@params$input$ntrees, info="Input params should be equal to 100")
    expect_true(100>model@params$actual$ntrees, info="Actual params should be less than 100 because of early stopping")
}

test.actual_params_tree_algos <- function() {
    prostate<-h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate$CAPSULE<-as.factor(prostate$CAPSULE)
    response<-"CAPSULE"

    prostate_split<-h2o.splitFrame(data=prostate,ratios=0.8, seed=1234)
    prostate_train<-prostate_split[[1]]
    prostate_test<-prostate_split[[2]]

    model<-h2o.randomForest(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.randomForest(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    nfolds=2, # enable cross validation
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.gbm(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.gbm(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    nfolds=2, # enable cross validation
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.isolationForest(training_frame=prostate_train,
    validation_frame=prostate_test,
    validation_response_column=response,
    max_depth=8,
    stopping_metric="AUCPR",
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=1,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.xgboost(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)

    model<-h2o.xgboost(training_frame=prostate_train,
    validation_frame=prostate_test,
    y=response,
    max_depth=8,
    stopping_metric="AUC",
    nfolds=2, # enable cross validation
    stopping_rounds=3,
    stopping_tolerance=0.01,
    seed=42,
    ntrees=100,
    score_each_iteration=TRUE)
    check.actual.parameters(model)
}

doTest("Test actual params", test.actual_params_tree_algos)
