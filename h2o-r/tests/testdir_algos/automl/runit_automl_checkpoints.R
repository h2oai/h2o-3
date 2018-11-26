setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.checkpoints.test <- function() {

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
    checkpoints_dir <- tempfile()
    print(checkpoints_dir)

    aml1 <- h2o.automl(y = y,
                        training_frame = train,
                        project_name="r_aml1", 
                        stopping_rounds=3, 
                        stopping_tolerance=0.001, 
                        stopping_metric="AUC", 
                        max_models=max_models, 
                        export_checkpoints_dir=checkpoints_dir,
                        seed=1234)

    saved_models <- list.files(checkpoints_dir)
    leader_models <- c()
    for (m in saved_models) {
      if (!grepl("_cv", m)) {
        leader_models <- c(leader_models, m)
      }
    }
    num_files <- length(leader_models)
    unlink(checkpoints_dir, recursive = TRUE)

    print(leader_models)
    print(aml1@leaderboard)
    
    expect_true(num_files > 0)
    expect_equal(num_files, nrow(aml1@leaderboard))
}

doTest("AutoML checkpoints export Test", automl.checkpoints.test)