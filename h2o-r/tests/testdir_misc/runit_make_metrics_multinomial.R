setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.make_metrics_multinomial <- function(weights_col = NULL) {
    train <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    train$CAPSULE <- as.factor(train$CAPSULE)
    train$RACE <- as.factor(train$RACE)
    weights <- NULL
    if (!is.null(weights_col)) {
        weights <- h2o.runif(train, seed = 42)
        train[[weights_col]] <- weights
    }

    response = "RACE"
    predictors <- setdiff(names(train),c("ID",response))
    model <- h2o.gbm(x=predictors,y=response,distribution = "multinomial",training_frame=train,
                     ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20,weights=weights_col, auc_type="MACRO_OVR")

    pred <- h2o.assign(h2o.predict(model,train)[,-1],"pred")
    actual <- h2o.assign(as.factor(train[,response]),"act")
    domain <- h2o.levels(train[[response]])

    m0 <- h2o.make_metrics(pred,actual,weights=weights, auc_type="MACRO_OVR")
    print(m0)
    m1 <- h2o.make_metrics(pred,actual,domain=domain,weights=weights, auc_type="MACRO_OVR")
    print(m1)
    m2 <- h2o.performance(model)
    print(m2)

    expect_equal(h2o.mse(m1), h2o.mse(m0))
    expect_equal(h2o.rmse(m1), h2o.rmse(m0))
    expect_equal(h2o.logloss(m1), h2o.logloss(m0))
    expect_equal(h2o.mean_per_class_error(m1), h2o.mean_per_class_error(m0))
    expect_equal(h2o.auc(m1), h2o.auc(m0))
    expect_equal(h2o.aucpr(m1), h2o.aucpr(m0))

    expect_equal(h2o.mse(m1), h2o.mse(m2))
    expect_equal(h2o.rmse(m1), h2o.rmse(m2))
    expect_equal(h2o.logloss(m1), h2o.logloss(m2))
    expect_equal(h2o.mean_per_class_error(m1), h2o.mean_per_class_error(m2))
    expect_equal(h2o.auc(m1), h2o.auc(m2))
    expect_equal(h2o.aucpr(m1), h2o.aucpr(m2))
}

test.make_metrics_multinomial_weights <- function() test.make_metrics_multinomial("weights")

doSuite("Check making Multinomial model metrics.", makeSuite(
#    test.make_metrics_multinomial,
    test.make_metrics_multinomial_weights
))
