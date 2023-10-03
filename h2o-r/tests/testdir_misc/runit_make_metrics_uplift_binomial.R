setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.make_metrics_uplift_binomial <- function() {
    response <- "outcome"
    treatment <- "treatment"
    train <- h2o.importFile(locate("smalldata/uplift/upliftml_train.csv"))
    valid <- h2o.importFile(locate("smalldata/uplift/upliftml_test.csv"))
    train$treatment <- as.factor(train$treatment)
    train$outcome <- as.factor(train$outcome)
    valid$treatment <- as.factor(valid$treatment)
    valid$outcome <- as.factor(valid$outcome)

    predictors <- sprintf("feature_%s",seq(0:11))

    nbins <- 20
    model <- h2o.upliftRandomForest(training_frame=train,
                                    validation_frame=valid,
                                    x=predictors,
                                    y=response,
                                    treatment_column=treatment,
                                    seed=42,
                                    auuc_nbins=nbins,
                                    score_each_iteration=TRUE,
                                    ntrees=3)
    print(model)

    pred <- h2o.assign(h2o.predict(model,valid)[,1], "pred")
    actual <- h2o.assign(valid[,response], "act")
    treat <- h2o.assign(valid[,treatment], "treatment")

    thresholds <- model@model$default_auuc_thresholds

    m0 <- h2o.performance(model, valid=TRUE)
    thresholds0 <- m0@metrics$thresholds$thresholds

    m1 <- h2o.make_metrics(pred, actual, treatment=treat, auuc_nbins=nbins)
    thresholds1 <- m1@metrics$thresholds$thresholds

    m2 <- h2o.performance(model, valid)
    thresholds2 <- m2@metrics$thresholds$thresholds
    
    m3 <- h2o.make_metrics(pred, actual, treatment=treat, custom_auuc_thresholds=thresholds)
    thresholds3 <- m3@metrics$thresholds$thresholds

    tol <- 1e-10
    ltol <- 1e-1 # There are few differences in prediction R vs. Java scoring, so the results are not the same but similar

    # thresholds should be the same
    expect_equal(thresholds, thresholds0, tolerance=tol)
    expect_equal(thresholds0, thresholds1, tolerance=tol)
    expect_equal(thresholds0, thresholds2, tolerance=tol)
    expect_equal(thresholds0, thresholds3, tolerance=ltol)

    auuc0 <- h2o.auuc(m0)
    auuc1 <- h2o.auuc(m1)
    auuc2 <- h2o.auuc(m2)
    auuc3 <- h2o.auuc(m3)

    expect_equal(auuc0, auuc1, tolerance=tol)
    expect_equal(auuc0, auuc2, tolerance=tol)
    expect_equal(auuc0, auuc3, tolerance=ltol)

    auuc_table0 <- h2o.auuc_table(m0)
    auuc_table1 <- h2o.auuc_table(m1)
    auuc_table2 <- h2o.auuc_table(m2)
    auuc_table3 <- h2o.auuc_table(m3)

    expect_true(is.data.frame(auuc_table0))
    expect_true(is.data.frame(auuc_table1))
    expect_true(is.data.frame(auuc_table2))
    expect_true(is.data.frame(auuc_table3))

    expect_equal(auuc_table0, auuc_table1, tolerance=tol)
    expect_equal(auuc_table0, auuc_table2, tolerance=tol)
    expect_equal(auuc_table0, auuc_table3, tolerance=ltol)

    thr_table0 <- h2o.thresholds_and_metric_scores(m0)
    thr_table1 <- h2o.thresholds_and_metric_scores(m1)
    thr_table2 <- h2o.thresholds_and_metric_scores(m2)
    thr_table3 <- h2o.thresholds_and_metric_scores(m3)

    expect_equal(thr_table0, thr_table1, tolerance=tol)
    expect_equal(thr_table0, thr_table2, tolerance=tol)
    expect_equal(thr_table0, thr_table3, tolerance=ltol)

    qini0 <- h2o.qini(m0)
    qini1 <- h2o.qini(m1)
    qini2 <- h2o.qini(m2)
    qini3 <- h2o.qini(m3)

    expect_equal(qini0, qini1, tolerance=tol)
    expect_equal(qini0, qini2, tolerance=tol)
    expect_equal(qini0, qini3, tolerance=ltol)

    aecu_table0 <- h2o.aecu_table(m0)
    aecu_table1 <- h2o.aecu_table(m1)
    aecu_table2 <- h2o.aecu_table(m2)
    aecu_table3 <- h2o.aecu_table(m3)

    expect_true(is.data.frame(aecu_table0))
    expect_true(is.data.frame(aecu_table1))
    expect_true(is.data.frame(aecu_table2))
    expect_true(is.data.frame(aecu_table3))

    expect_equal(aecu_table0, aecu_table1, tolerance=tol)
    expect_equal(aecu_table0, aecu_table2, tolerance=tol)
    expect_equal(aecu_table0, aecu_table3, tolerance=ltol)

    ate0 <- h2o.ate(m0)
    ate1 <- h2o.ate(m1)
    ate2 <- h2o.ate(m2)
    ate3 <- h2o.ate(m3)

    expect_equal(ate0, ate1, tolerance=tol)
    expect_equal(ate0, ate2, tolerance=tol)
    expect_equal(ate0, ate3, tolerance=ltol)

    att0 <- h2o.att(m0)
    att1 <- h2o.att(m1)
    att2 <- h2o.att(m2)
    att3 <- h2o.att(m3)

    expect_equal(att0, att1, tolerance=tol)
    expect_equal(att0, att2, tolerance=tol)
    expect_equal(att0, att3, tolerance=ltol)

    atc0 <- h2o.atc(m0)
    atc1 <- h2o.atc(m1)
    atc2 <- h2o.atc(m2)
    atc3 <- h2o.atc(m3)

    expect_equal(atc0, atc1, tolerance=tol)
    expect_equal(atc0, atc2, tolerance=tol)
    expect_equal(atc0, atc3, tolerance=ltol)
}

doSuite("Check making uplift binomial model metrics.", makeSuite(
    test.make_metrics_uplift_binomial
))
