setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


automl.event_log.test <- function() {
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 3
    seed <- 1234

    aml = h2o.automl(project_name="r_test_event_log",
                     y=y,
                     training_frame=train,
                     max_models=max_models,
                     seed=seed)

    print(aml@event_log)
    expect_equal(names(aml@event_log), c('timestamp', 'level', 'stage', 'message', 'name', 'value'))
    expect_gt(nrow(aml@event_log), 10)

    print(aml@training_info)
    expect_gt(as.integer(aml@training_info['stop_epoch']), as.integer(aml@training_info['start_epoch']))
    stop_dt <- as.POSIXlt(as.integer(aml@training_info['stop_epoch']), origin="1970-01-01")
    now <- as.POSIXlt(Sys.time())
    expect_lte(abs(stop_dt - now),  60) # test that stop_epoch is time encoded as unix epoch
    expect_lte(abs(as.integer(aml@training_info['duration_secs']) - (as.integer(aml@training_info['stop_epoch']) - as.integer(aml@training_info['start_epoch']))), 1)
}

automl.train_verbosity.test <- function() {
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 2
    seed <- 1234

    print("verbosity disabled")
    aml = h2o.automl(project_name="r_test_train_verbosity",
                     y=y,
                     training_frame=train,
                     max_models=max_models,
                     seed=seed)

    print("verbosity level = info")
    aml = h2o.automl(project_name="r_test_train_verbosity_info",
                     y=y,
                     training_frame=train,
                     max_models=max_models,
                     seed=seed,
                     verbosity='info')

    print("verbosity level = warn")
    aml = h2o.automl(project_name="r_test_train_verbosity_warn",
                     y=y,
                     training_frame=train,
                     max_models=max_models,
                     seed=seed,
                     stopping_tolerance=0.01,
                     verbosity='warn')
}


doSuite("AutoML EventLog tests", makeSuite(
    automl.event_log.test,
    automl.train_verbosity.test
))
