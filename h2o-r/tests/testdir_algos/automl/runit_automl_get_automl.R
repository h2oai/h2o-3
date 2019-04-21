setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.get.automl.test <- function() {

    # Load data and split into train, valid and test sets
    train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
    destination_frame = "higgs_train_5k")
    test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
    destination_frame = "higgs_test_5k")
    ss <- h2o.splitFrame(test, seed = 1)
    valid <- ss[[1]]
    test <- ss[[1]]

    y <- "response"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])
    max_models <- 3

    aml1 <- h2o.automl(y = y,
                        training_frame = train,
                        project_name="r_aml1", 
                        stopping_rounds=3, 
                        stopping_tolerance=0.001, 
                        stopping_metric="AUC", 
                        max_models=max_models, 
                        seed=1234)

    #Use h2o.getAutoML to get previous automl instance
    get_aml1 <- h2o.getAutoML(aml1@project_name)
    
    print("Leader model ID/project_name for original automl object")
    print(aml1@leader@model_id)
    print(aml1@project_name)
    print("Leader model ID/project_name after fetching original automl object")
    print(get_aml1@leader@model_id)
    print(get_aml1@project_name)
    
    
    expect_equal(aml1@project_name, get_aml1@project_name)
    expect_equal(aml1@leader@model_id, get_aml1@leader@model_id)
    expect_equal(aml1@leaderboard, get_aml1@leaderboard)

}

doTest("AutoML h2o.getAutoML Test", automl.get.automl.test)
