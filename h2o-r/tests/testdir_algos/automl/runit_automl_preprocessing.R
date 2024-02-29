setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.preprocessing.suite <- function() {

  import_dataset <- function() {
    y <- "survived"
    fr <- h2o.importFile(locate("smalldata/titanic/titanic_expanded.csv"))
    splits <- h2o.splitFrame(fr, destination_frames=c("r_amlte_train", "r_amlte_test"), seed = 1)
    train <- splits[[1]]
    test <- splits[[2]]
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
                      preprocessing = list("target_encoding"),
                      seed = 1
    )
    print(h2o.get_leaderboard(aml))
    model_ids <- as.vector(aml@leaderboard$model_id)
    expect_equal(sum(grepl("Pipeline_", model_ids)) > 0, TRUE)

    keys <- h2o.ls()$key
    expect_true(any(grepl("default_TE_1_model", keys)))
  }


  makeSuite(
    test_targetencoding_enabled
  )
}


doSuite("AutoML Preprocessing Suite", automl.preprocessing.suite())
