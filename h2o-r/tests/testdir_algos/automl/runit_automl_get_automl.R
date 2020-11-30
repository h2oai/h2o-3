setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.get.automl.test <- function() {

    # Load data and split into train, valid and test sets
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 3

    aml1 <- h2o.automl(y = y,
                       training_frame = train,
                       project_name="r_aml_get_automl",
                       max_models=max_models,
                       seed=1234)

    #Use h2o.get_automl to get previous automl instance
    get_aml1 <- h2o.get_automl(aml1@project_name)
    
    print("Leader model ID/project_name for original automl object")
    print(aml1@leader@model_id)
    print(aml1@project_name)
    print("Leader model ID/project_name after fetching original automl object")
    print(get_aml1@leader@model_id)
    print(get_aml1@project_name)
    
    
    expect_equal(aml1@project_name, get_aml1@project_name)
    expect_equal(aml1@leader@model_id, get_aml1@leader@model_id)
    expect_equal(aml1@leaderboard, get_aml1@leaderboard)
    expect_equal(h2o.dim(aml1@event_log), h2o.dim(get_aml1@event_log))
    expect_equal(h2o.get_leaderboard(aml1), h2o.get_leaderboard(get_aml1))
    expect_equal(h2o.get_leaderboard(aml1, "ALL"), h2o.get_leaderboard(get_aml1, "ALL"))
}

doTest("AutoML h2o.get_automl Test", automl.get.automl.test)
