setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.preprocessing.suite <- function() {

  import_dataset <- function() {
    y <- "CAPSULE"
    train <- h2o.importFile(locate("smalldata/testng/prostate_train.csv"), destination_frame = "prostate_full_train")
    train[,y] <- as.factor(train[,y])
    test <- h2o.importFile(locate("smalldata/testng/prostate_test.csv"), destination_frame = "prostate_full_test")
    test[,y] <- as.factor(test[,y])
    x <- setdiff(names(train), y)
    return(list(x=x, y=y, train=train, test=test))
  }

  test_targetencoding_enabled <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                      training_frame = ds$train,
                      leaderboard_frame = ds$test,
                      project_name="r_automl_targetencoding",
                      max_models = 6,
                      preprocessing = list("targetencoding"),
                      seed = 1
    )
    print(h2o.get_leaderboard(aml))  
    keys <- h2o.ls()$key
    expect_true(any(grepl("TargetEncoding_AutoML", keys))) 
  }


  makeSuite(
    test_targetencoding_enabled
  )
}


doSuite("AutoML Preprocessing Suite", automl.preprocessing.suite())
