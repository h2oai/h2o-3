setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.GBM.iris.multinomial.auc <- function() {
    #prostate <- h2o.importFile(path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
    prostate <- h2o.importFile("/home/mori/Documents/h2o/code/h2o-3/smalldata/prostate/prostate.csv")
    print(prostate)

    # Split dataset giving the training dataset 75% of the data
    prostate_split <- h2o.splitFrame(data = prostate, ratios = 0.75)
    
    response_col <- "GLEASON"

    # Create a training set from the 1st dataset in the split
    train.hex <- prostate_split[[1]]
    train.hex[, response_col] <- as.factor(train.hex[, response_col])

    # Create a testing set from the 2nd dataset in the split
    test.hex <- prostate_split[[2]]
    test.hex[, response_col] <- as.factor(test.hex[, response_col])

    predictors <- c("RACE", "AGE", "PSA", "DPROS", "CAPSULE", "VOL", "DCAPS")
    
    # Build GBM model
    iris.gbm <- h2o.gbm(y=response_col, x=predictors, distribution="multinomial", training_frame=train.hex, ntrees=1, max_depth=2, min_rows=20)
    
    # Score test data with different default auc_type (previous was "NONE", so no AUC calculation)
    auc_type <- "WEIGHTED_OVO"
    perf <- h2o.performance(iris.gbm, test.hex, auc_type=auc_type)
    
    # Check default AUC is set correctly
    auc_table <- h2o.multinomial_auc_table(perf)
    default_auc <- h2o.auc(perf)
    weighted_ovo_auc <- auc_table[32, 4] # weighted ovo AUC is the last number in the table
    
    expect_equal(default_auc, weighted_ovo_auc)
    print(paste(weighted_ovo_auc, "=",  default_auc))
    print(perf)
    print(auc_table)
    
    #Test auc_type is set and newdata is NULL
    perf2 <- h2o.performance(iris.gbm, train=TRUE, auc_type=auc_type)
    auc <- h2o.auc(perf2)
    print(auc)
    expect_true(auc == "NaN")

    # Build GBM model with auc_type
    iris.gbm <- h2o.gbm(y=response_col, x=predictors, distribution="multinomial", training_frame=train.hex, ntrees=1, max_depth=2, min_rows=20, auc_type=auc_type)
    mm <- iris.gbm@model$training_metrics
    print("AUC auc_type set")
    auc_table <- h2o.multinomial_auc_table(mm)
    default_auc <- h2o.auc(mm)
    weighted_ovo_auc <- auc_table[32, 4] # weighted ovo AUC is the last number in the table

    expect_equal(default_auc, weighted_ovo_auc)
    print(paste(weighted_ovo_auc, "=",  default_auc))
    print(perf)
    print(auc_table)
    

    # Build GBM model with cv
    iris.gbm <- h2o.gbm(y=response_col, x=predictors, distribution="multinomial", training_frame=train.hex, validation_frame=test.hex, ntrees=5, max_depth=2, min_rows=20, nfold=3)

    # Check aucpr is not in performance table
    print(iris.gbm@model$cross_validation_metrics_summary)
    expect_false("aucpr" %in% row.names(iris.gbm@model$cross_validation_metrics_summary))
    expect_true("pr_auc" %in% row.names(iris.gbm@model$cross_validation_metrics_summary))
}

doTest("GBM test checkpoint on iris", test.GBM.iris.multinomial.auc)
