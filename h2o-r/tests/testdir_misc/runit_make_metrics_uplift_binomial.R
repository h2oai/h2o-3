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

    m1 <- h2o.make_metrics(pred, actual, treatment=treat, custom_auuc_thresholds=thresholds)
    thresholds1 <- m1@metrics$thresholds$thresholds

    m2 <- h2o.performance(model, valid)
    thresholds2 <- m2@metrics$thresholds$thresholds

    tol <- 1e-10
    ltol <- 1e-1 # There are few differences in prediction R vs. Java scoring, so the results are not the same but similar

    # thresholds should be the same
    expect_equal(thresholds, thresholds0, tolerance=tol)
    expect_equal(thresholds0, thresholds1, tolerance=ltol)
    expect_equal(thresholds0, thresholds2, tolerance=tol)

    auuc0 <- h2o.auuc(m0)
    auuc1 <- h2o.auuc(m1)
    auuc2 <- h2o.auuc(m2)

    expect_equal(auuc0, auuc1, tolerance=ltol)
    expect_equal(auuc0, auuc2, tolerance=tol)

    auuc_table0 <- h2o.auuc_table(m0)
    auuc_table1 <- h2o.auuc_table(m1)
    auuc_table2 <- h2o.auuc_table(m2)

    expect_true(is.data.frame(auuc_table0))
    expect_true(is.data.frame(auuc_table1))
    expect_true(is.data.frame(auuc_table2))

    expect_equal(auuc_table0, auuc_table1, tolerance=ltol)
    expect_equal(auuc_table0, auuc_table2, tolerance=tol)

    thr_table0 <- h2o.thresholds_and_metric_scores(m0)
    thr_table1 <- h2o.thresholds_and_metric_scores(m1)
    thr_table2 <- h2o.thresholds_and_metric_scores(m2)

    expect_equal(thr_table0, thr_table1, tolerance=ltol)
    expect_equal(thr_table0, thr_table2, tolerance=tol)

    qini0 <- h2o.qini(m0)
    qini1 <- h2o.qini(m1)
    qini2 <- h2o.qini(m2)

    expect_equal(qini0, qini1, tolerance=ltol)
    expect_equal(qini0, qini2, tolerance=tol)

    aecu_table0 <- h2o.aecu_table(m0)
    aecu_table1 <- h2o.aecu_table(m1)
    aecu_table2 <- h2o.aecu_table(m2)

    expect_true(is.data.frame(aecu_table0))
    expect_true(is.data.frame(aecu_table1))
    expect_true(is.data.frame(aecu_table2))

    expect_equal(aecu_table0, aecu_table1, tolerance=ltol)
    expect_equal(aecu_table0, aecu_table2, tolerance=tol)

    ate0 <- h2o.ate(m0)
    ate1 <- h2o.ate(m1)
    ate2 <- h2o.ate(m2)

    expect_equal(ate0, ate1, tolerance=ltol)
    expect_equal(ate0, ate2, tolerance=tol)

    att0 <- h2o.att(m0)
    att1 <- h2o.att(m1)
    att2 <- h2o.att(m2)

    expect_equal(att0, att1, tolerance=ltol)
    expect_equal(att0, att2, tolerance=tol)

    atc0 <- h2o.atc(m0)
    atc1 <- h2o.atc(m1)
    atc2 <- h2o.atc(m2)

    expect_equal(atc0, atc1, tolerance=ltol)
    expect_equal(atc0, atc2, tolerance=tol)
}

doSuite("Check making uplift binomial model metrics.", makeSuite(
    test.make_metrics_uplift_binomial
))
