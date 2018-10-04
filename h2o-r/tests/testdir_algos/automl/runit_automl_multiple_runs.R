setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

list_keys_containing <- function(str) {
  keys <- h2o.ls()$key
  if (length(keys)) as.character(keys[sapply(keys, function(k) grepl(str, k))]) else list()
}

automl.multiple_runs.test <- function() {

    # This test runs multiple automl projects,
    # keeping a reference to each of them
    # and tries to manipulate results from both previous and last runs, especially leaderboard
    # ensuring that old references have not been overridden during last run.
    # This checks an issue raised by PUBDEV-5848.

    df <- as.h2o(iris)
    splits <- h2o.splitFrame(df, seed = 1234)
    train <- splits[[1]]
    test <- splits[[2]]

    ## Run First AutoML
    y <- "Species"
    x <- setdiff(colnames(train), y)
    automl_1 <- h2o.automl(
        x = x, y = y,
        seed = 1234,
        training_frame = train,
        validation_frame = test,
        max_models = 3,
        project_name = "run_1.hex"
    )
    leaderboard_keys_1 <- list_keys_containing("leaderboard") 
    expect_equal(length(leaderboard_keys_1), 1)
      
    ## Run Second AutoML
    automl_2 <- h2o.automl(
        x = x, y = y,
        seed = 4321,
        training_frame = train,
        validation_frame = test,
        max_models = 3,
        project_name = "run_2.hex"
    )
    leaderboard_keys_2 <- list_keys_containing("leaderboard") 
    expect_equal(length(leaderboard_keys_2), 2)
    expect_true(all(leaderboard_keys_1 %in% leaderboard_keys_2))
    
    #following statement trigger Rapids request: ensuring that operations are still working
    expect_true(sum(automl_1@leaderboard$mean_per_class_error) > 0)
    expect_true(sum(automl_2@leaderboard$mean_per_class_error) > 0)
    expect_true(sum(automl_1@leaderboard$mean_per_class_error) != sum(automl_2@leaderboard$mean_per_class_error))

    #verifying Rapids didn't mess up anything
    leaderboard_keys_3 <- list_keys_containing("leaderboard") 
    expect_equal(leaderboard_keys_3, leaderboard_keys_2)
}

doTest("AutoML Multiple Runs Test", automl.multiple_runs.test)
