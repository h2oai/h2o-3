setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.make_metrics_regression <- function(weights_col = NULL) {
    train <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    train$CAPSULE <- as.factor(train$CAPSULE)
    train$RACE <- as.factor(train$RACE)
    weights <- NULL
    if (!is.null(weights_col)) {
        weights <- h2o.runif(train, seed = 42)
        train[[weights_col]] <- weights
    }

    response <- "AGE"
    predictors <- setdiff(names(train),c("ID",response))
    
    for (distribution in c("gaussian","laplace","poisson","gamma")) {
      if (!is.null(weights_col) && distribution=="laplace") {
          # Skipping on `laplace`
          # GBM training fails due to a bug: https://github.com/h2oai/h2o-3/issues/8158
          next
      }
      model <- h2o.gbm(x=predictors,y=response,distribution = distribution,training_frame=train,
                      ntrees=2,max_depth=3,min_rows=1,learn_rate=0.1,nbins=20,weights=weights_col)
      pred <- h2o.assign(h2o.predict(model,train),"pred")
      actual <- h2o.assign(train[,response],"act")

      m0 <- h2o.make_metrics(pred,actual,weights=weights)
      print(m0)
      m1 <- h2o.make_metrics(pred,actual,distribution=distribution,weights=weights)
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

}

test.make_metrics_regression_weights <- function() test.make_metrics_regression("weights")

doSuite("Check making regression model metrics.", makeSuite(
    test.make_metrics_regression,
    test.make_metrics_regression_weights
))
