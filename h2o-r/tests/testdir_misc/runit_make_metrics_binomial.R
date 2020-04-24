setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.make_metrics_binomial <- function(weights_col = NULL) {
    train <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    train$CAPSULE <- as.factor(train$CAPSULE)
    train$RACE <- as.factor(train$RACE)
    weights <- NULL
    if (!is.null(weights_col)) {
        weights <- h2o.runif(train, seed = 42)
        train[[weights_col]] <- weights
    }

    response <- "CAPSULE"
    predictors <- setdiff(names(train),c("ID",response))
    model <- h2o.gbm(x=predictors, y=response, distribution = "bernoulli", training_frame = train,
                    ntrees=2, max_depth=3, min_rows=1, learn_rate=0.01, nbins=20, weights=weights_col)
    print(model)

    pred <- h2o.assign(h2o.predict(model,train)[,3],"pred")
    actual <- h2o.assign(as.factor(train[,response]),"act")
    domain <- c("0","1")

    m0 <- h2o.make_metrics(pred,actual,weights=weights)
    print(m0)
    m1 <- h2o.make_metrics(pred,actual,domain=domain,weights=weights)
    print(m1)
    m2 <- h2o.performance(model)
    print(m2)
    
    acc0 <- h2o.accuracy(m0)
    expect_true(is.data.frame(acc0))
    expect_equal(dim(acc0), c(nrow(m0@metrics$thresholds_and_metric_scores), 2))
    expect_equal(names(acc0), c('threshold', 'accuracy'))

    acc0_t <- h2o.accuracy(m0, thresholds=c(0.2, 0.3, 0.4))
    expect_true(is.list(acc0_t))
    expect_equal(length(acc0_t), 3)

    acc0_max <- h2o.accuracy(m0, thresholds='max')
    expect_true(is.list(acc0_max))
    expect_equal(length(acc0_max), 1)

    all_m0 <- h2o.metric(m0)
    expect_true(is.data.frame(all_m0))
    expect_equal(all_m0, m0@metrics$thresholds_and_metric_scores)

    all_m0_t <- h2o.metric(m0, thresholds=c(0.2, 0.3, 0.4))
    expect_true(is.list(all_m0_t))
    expect_equal(length(all_m0_t), 3)
    expect_equal(dim(all_m0_t[[1]]), c(1, ncol(m0@metrics$thresholds_and_metric_scores) - 2))

    all_max <- h2o.metric(m0, thresholds='max')
    expect_true(is.data.frame(all_max))
    expect_equal(all_max, m0@metrics$max_criteria_and_metric_scores)

    err0 <- h2o.error(m0)
    expect_true(is.data.frame(err0))
    expect_equal(names(err0), c('threshold', 'error'))
    expect_equal(acc0['accuracy'] + err0['error'], data.frame(accuracy=rep(1.0, nrow(acc0))), tolerance = 1e-6)

    err0_t <- h2o.error(m0, thresholds=c(0.2, 0.3, 0.4))
    expect_true(is.list(err0_t))
    expect_equal(mapply(`+`, acc0_t, err0_t), rep(1.0, 3))

    err0_max <- h2o.error(m0, thresholds='max')
    expect_true(is.list(err0_max))
    expect_equal(acc0_max[[1]] + err0_max[[1]], 1.0)


    expect_equal(h2o.auc(m0), h2o.auc(m1))
    expect_equal(h2o.mse(m0), h2o.mse(m1))
    expect_equal(h2o.rmse(m0), h2o.rmse(m1))
    expect_equal(h2o.logloss(m0), h2o.logloss(m1))
    expect_equal(h2o.mean_per_class_error(m0), h2o.mean_per_class_error(m1))

    expect_equal(h2o.auc(m1), h2o.auc(m2))
    expect_equal(h2o.mse(m1), h2o.mse(m2))
    expect_equal(h2o.rmse(m1), h2o.rmse(m2))
    expect_equal(h2o.logloss(m1), h2o.logloss(m2))
    expect_equal(h2o.mean_per_class_error(m1), h2o.mean_per_class_error(m2))

    # Testing confusion matrix
    cm0 <- h2o.confusionMatrix(m0, metrics=as.list(.h2o.maximizing_metrics))
    expect_equal(length(cm0), length(.h2o.maximizing_metrics))
    headers <- lapply(cm0, function(cm) attr(cm, 'header'))
    expect_true(all(sapply(.h2o.maximizing_metrics, function(m) any(grepl(m, headers)))),
                info="got duplicate CM headers, although all metrics are different")

    cm0t <- h2o.confusionMatrix(m0, metrics=as.list(.h2o.maximizing_metrics), thresholds=list(.3, .6))
    expect_equal(length(cm0t), 2 + length(.h2o.maximizing_metrics))
    headers <- lapply(cm0t, function(cm) attr(cm, 'header'))
    expect_equal(sum(unlist(lapply(headers, function(h) !any(sapply(.h2o.maximizing_metrics, function(m) grepl(m, h)))))), 2,
                 info="missing or duplicate headers without metric (thresholds only CMs)")
    expect_true(all(sapply(.h2o.maximizing_metrics, function(m) any(grepl(m, headers)))),
                info="got duplicate CM headers, although all metrics are different")
}

test.make_metrics_binomial_weights <- function() test.make_metrics_binomial("weights")

doSuite("Check making binomial model metrics.", makeSuite(
    test.make_metrics_binomial,
    test.make_metrics_binomial_weights
))
