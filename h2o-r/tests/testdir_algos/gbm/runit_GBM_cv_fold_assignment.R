setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GBM.nfolds.fold.assignment <- function() {

    N <- 5
    df <- as.h2o(iris)
    m <- h2o.gbm(1:3,4,df,nfolds=N,
                 keep_cross_validation_models=TRUE,
                 keep_cross_validation_fold_assignment=TRUE,
                 keep_cross_validation_predictions=TRUE)
    fold <- h2o.cross_validation_fold_assignment(m)
    predictions <- h2o.cross_validation_holdout_predictions(m)
    cv_predictions <- h2o.cross_validation_predictions(m)
    cv_models <- h2o.cross_validation_models(m)

    for (i in 1:N) {
      A=as.numeric(m@model$cross_validation_metrics_summary["mse",paste0("cv_",i,"_valid")])
      B=mean((df[fold==(i-1),4] - h2o.predict(cv_models[[i]], df[fold==(i-1),]))^2)
      expect_true(abs(A-B)<1e-6)
    }

    ## test that user-given fold column is respected
    df$fold <- as.h2o(sample(1:5,nrow(df),TRUE))
    m <- h2o.gbm(1:3,4,df,fold_column="fold",keep_cross_validation_fold_assignment=TRUE,keep_cross_validation_predictions = TRUE)
    fold <- h2o.cross_validation_fold_assignment(m)
    expect_true(any(fold!=df$fold)==0)

    ## test that modulo fold column is actually modulo
    m <- h2o.gbm(1:3,4,df,nfolds=5,fold_assignment="Modulo",keep_cross_validation_fold_assignment=TRUE,keep_cross_validation_predictions = TRUE)
    fold <- h2o.cross_validation_fold_assignment(m)
    for (i in 1:5) expect_true(any(fold[(1:30)*5-(5-i),]!=(i-1))==0)
}

doTest("GBM Cross-Validation Fold Assignment Test: Prostate", test.GBM.nfolds.fold.assignment)
