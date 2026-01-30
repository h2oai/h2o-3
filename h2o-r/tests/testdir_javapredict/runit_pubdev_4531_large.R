setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#----------------------------------------------------------------------
# Used to test out customer dataset to make sure h2o model predict and pojo predict generate
# the same answers.  However, customer dataset is not to be made public and hence this test
# is a NOPASS.
#----------------------------------------------------------------------
test <-
  function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------
    
    # Story:
    # The objective of the test is to verify java code generation
    # for big models containing huge amount of trees.
    # This case verify multi-classifiers.
    test_file <- locate("smalldata/logreg/prostate_train_null_column_name.csv")
    test_frame <- h2o.importFile(test_file)
    params = prepTest()
    doJavapredictTest("gbm",test_file,test_frame,params)  # make sure original code run

    # check a separator that is not a special character
    test_file <- locate("smalldata/logreg/prostate_train_null_column_name.csv")
    test_frame <- h2o.importFile(test_file)
    params = prepTest()
    doJavapredictTest("gbm",test_file,test_frame,params,separator="@",setInvNumNA=FALSE)


    test_file <- locate("smalldata/logreg/prostate_train_null_column_name.csv")
    test_frame <- h2o.importFile(test_file)
    params = prepTest()
    doJavapredictTest("gbm",test_file,test_frame,params,separator="$",setInvNumNA=TRUE)
  }

prepTest <- function() {
  # check a separator that is a special character, R does not all | to be a separator, change it to (
  training_file <- test_file <- locate("smalldata/logreg/prostate_train_null_column_name.csv")
  training_frame <- h2o.importFile(training_file)

  params                 <- list()
  params$ntrees          <- 20
  params$max_depth       <- 3
  params$x               <- 2:8
  params$y               <- "CAPSULE"
  params$training_frame  <- training_frame
  params$seed            <- 42
  params$learn_rate       <-0.1
  params$min_rows        <-10
  params$distribution    <-"bernoulli"
  return(params)
}

doTest("pubdev-4531: PredictCsv test", test)
