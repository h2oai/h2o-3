setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

makeMetrics <- function() {
    train = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="train")
    train$CAPSULE <- as.factor(train$CAPSULE)
    train$RACE <- as.factor(train$RACE)

    ## REGRESSION
    response = "AGE"
    predictors = setdiff(names(train),c("ID",response))

    for (distribution in c("gaussian","laplace","poisson","gamma")) {
      model = h2o.gbm(x=predictors,y=response,distribution = distribution,training_frame=train,
                      ntrees=2,max_depth=3,min_rows=1,learn_rate=0.1,nbins=20)
      pred <- h2o.assign(h2o.predict(model,train),"pred")
      actual <- h2o.assign(train[,response],"act")

      m0 <- h2o.make_metrics(pred,actual)
      print(m0)
      m1 <- h2o.make_metrics(pred,actual,distribution=distribution)
      print(m1)
      m2 <- h2o.performance(model)
      print(m2)

      expect_true(abs(h2o.rmse(m0)-h2o.rmse(m1))<1e-5)
      expect_true(abs(h2o.mae(m0)-h2o.mae(m1))<1e-5)
      expect_true(abs(h2o.mse(m0)-h2o.mse(m1))<1e-5)
      if (distribution=="gaussian") {
        expect_true(abs(h2o.mean_residual_deviance(m0)-h2o.mean_residual_deviance(m1))<1e-5)
      } else {
        expect_true(abs(h2o.mean_residual_deviance(m0)-h2o.mean_residual_deviance(m1))>1e-5)
      }
      expect_true(abs(h2o.rmsle(m0)-h2o.rmsle(m1))<1e-5)

      expect_true(abs(h2o.rmse(m1)-h2o.rmse(m2))<1e-5)
      expect_true(abs(h2o.mae(m1)-h2o.mae(m2))<1e-5)
      expect_true(abs(h2o.mse(m1)-h2o.mse(m2))<1e-5)
      expect_true(abs(h2o.mean_residual_deviance(m1)-h2o.mean_residual_deviance(m2))<1e-5)
      expect_true(abs(h2o.rmsle(m1)-h2o.rmsle(m2))<1e-5)
    }


    ## BINOMIAL
    response = "CAPSULE"
    predictors = setdiff(names(train),c("ID",response))
    model = h2o.gbm(x=predictors,y=response,distribution = "bernoulli",training_frame=train,
            ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)
    pred <- h2o.assign(h2o.predict(model,train)[,3],"pred")
    actual <- h2o.assign(as.factor(train[,response]),"act")
    domain <- c("0","1")

    m0 <- h2o.make_metrics(pred,actual)
    print(m0)
    m1 <- h2o.make_metrics(pred,actual,domain=domain)
    print(m1)
    m2 <- h2o.performance(model)
    print(m2)

    expect_true(abs(h2o.auc(m0)-h2o.auc(m1))<1e-5)
    expect_true(abs(h2o.mse(m0)-h2o.mse(m1))<1e-5)
    expect_true(abs(h2o.rmse(m0)-h2o.rmse(m1))<1e-5)
    expect_true(abs(h2o.logloss(m0)-h2o.logloss(m1))<1e-5)
    expect_true(abs(h2o.mean_per_class_error(m0)-h2o.mean_per_class_error(m1))<1e-5)

    expect_true(abs(h2o.auc(m1)-h2o.auc(m2))<1e-5)
    expect_true(abs(h2o.mse(m1)-h2o.mse(m2))<1e-5)
    expect_true(abs(h2o.rmse(m1)-h2o.rmse(m2))<1e-5)
    expect_true(abs(h2o.logloss(m1)-h2o.logloss(m2))<1e-5)
    expect_true(abs(h2o.mean_per_class_error(m1)-h2o.mean_per_class_error(m2))<1e-5)


    ## MULTINOMIAL
    response = "RACE"
    predictors = setdiff(names(train),c("ID",response))
    model = h2o.gbm(x=predictors,y=response,distribution = "multinomial",training_frame=train,
            ntrees=2,max_depth=3,min_rows=1,learn_rate=0.01,nbins=20)
    pred <- h2o.assign(h2o.predict(model,train)[,-1],"pred")
    actual <- h2o.assign(as.factor(train[,response]),"act")
    domain <- h2o.levels(train[[response]])

    m0 <- h2o.make_metrics(pred,actual)
    print(m0)
    m1 <- h2o.make_metrics(pred,actual,domain=domain)
    print(m1)
    m2 <- h2o.performance(model)
    print(m2)

    expect_true(abs(h2o.mse(m1)-h2o.mse(m0))<1e-5)
    expect_true(abs(h2o.rmse(m1)-h2o.rmse(m0))<1e-5)
    expect_true(abs(h2o.logloss(m1)-h2o.logloss(m0))<1e-5)
    expect_true(abs(h2o.mean_per_class_error(m1)-h2o.mean_per_class_error(m0))<1e-5)

    expect_true(abs(h2o.mse(m1)-h2o.mse(m2))<1e-5)
    expect_true(abs(h2o.rmse(m1)-h2o.rmse(m2))<1e-5)
    expect_true(abs(h2o.logloss(m1)-h2o.logloss(m2))<1e-5)
    expect_true(abs(h2o.mean_per_class_error(m1)-h2o.mean_per_class_error(m2))<1e-5)
}
doTest("Check making model metrics.", makeMetrics)
