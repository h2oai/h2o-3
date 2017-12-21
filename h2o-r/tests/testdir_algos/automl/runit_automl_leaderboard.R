setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard.test <- function() {
    #binomial
    fr = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    fr["CAPSULE"] = as.factor(fr["CAPSULE"])
    f = h2o.automl(y=2,training_frame = fr, max_runtime_secs = 5, exclude_algos = list("GLM", "DeepLearning", "XRT", "DRF"))
    f@leaderboard
    expect_equal(names(f@leaderboard),c( "model_id","auc","logloss"))

    #regression
    fr2 = h2o.uploadFile(locate("smalldata/covtype/covtype.20k.data"))
    h = h2o.automl(y=55,training_frame = fr2, max_runtime_secs = 5, exclude_algos = list("GBM", "DeepWater"))
    h@leaderboard
    expect_equal(names(h@leaderboard),c("model_id", "mean_residual_deviance","rmse", "mae", "rmsle"))

    #multinomial
    fr3 = as.h2o(iris)
    g = h2o.automl(y=5,training_frame = fr3, max_runtime_secs = 5)
    g@leaderboard
    expect_equal(names(g@leaderboard),c( "model_id","mean_per_class_error"))


    #test of exclude_algos, should yield an empty leaderboard
    fr4 = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    fr4["CAPSULE"] = as.factor(fr4["CAPSULE"])
    f = h2o.automl(y=2,training_frame = fr4, max_runtime_secs = 5, exclude_algos = list("GLM", "XRT", "DRF", "GBM", "DeepLearning", "DeepWater", "StackedEnsemble"))
    f@leaderboard

    expect_equal(names(f@leaderboard),c( "model_id","auc","logloss"))
    # TODO: for empty leaderboards there's a dummy row for some reason.
    expect_equal(nrow(f@leaderboard), 1)
}

doTest("AutoML Leaderboard Test", automl.leaderboard.test)
