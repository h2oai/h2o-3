setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.make_metrics_uplift_binomial <- function() {
    response <- "outcome"
    treatment <- "treatment"
    train <- h2o.importFile(locate("smalldata/uplift/upliftml_train.csv"))
    train$treatment <- as.factor(train$treatment)
    train$outcome <- as.factor(train$outcome)

    predictors <- sprintf("feature_%s",seq(0:11))
    
    
    model <- h2o.upliftRandomForest(training_frame=train,
                                    x=predictors,
                                    y=response,
                                    ntrees=5,
                                    max_depth=5,
                                    treatment_column=treatment,
                                    min_rows=10,
                                    nbins=100,
                                    seed=1234)
    print(model)

    pred <- h2o.assign(h2o.predict(model,train)[,1],"pred")
    actual <- h2o.assign(train[,response],"act")
    treat <- h2o.assign(train[,treatment],"treatment")
    
    m0 <- h2o.make_metrics(pred, actual, treatment=treatment)
    print(m0)
    m1 <- h2o.performance(model, train)
    print(m1)
    
    auuc0 <- h2o.auuc(m0)
    auuc1 <- h2o.auuc(m1)
 
    auuc_table0 <- h2o.auuc_table(m0)
    auuc_table1 <- h2o.auuc_table(m1)
    
    expect_true(is.data.frame(auuc_table0))
    expect_true(is.data.frame(auuc_table1))
    
    expect_equal(auuc0, auuc1)
    expect_equal(auuc_table0, auuc_table1)
    
    thr_table0 <- h2o.thresholds_and_metric_scores(m0)
    thr_table1 <- h2o.thresholds_and_metric_scores(m1)
    
    expect_equal(thr_table0, thr_table1)
   
    qini0 <- h2o.qini(m0)
    qini1 <- h2o.qini(m1)
    
    expect_equal(qini0, qini1)
 
    aecu_table0 <- h2o.aecu_table(m0)
    aecu_table1 <- h2o.aecu_table(m1)
    
    expect_true(is.data.frame(aecu_table0))
    expect_true(is.data.frame(aecu_table1))
 
    expect_equal(aecu_table0, aecu_table1)

    ate0 <- h2o.ate(m0)
    ate1 <- h2o.ate(m1)

    expect_equal(ate0, ate1)

    att0 <- h2o.att(m0)
    att1 <- h2o.att(m1)

    expect_equal(att0, att1)

    atc0 <- h2o.atc(m0)
    atc1 <- h2o.atc(m1)

    expect_equal(atc0, atc1)
}

doSuite("Check making uplift binomial model metrics.", makeSuite(
    test.make_metrics_uplift_binomial
))
