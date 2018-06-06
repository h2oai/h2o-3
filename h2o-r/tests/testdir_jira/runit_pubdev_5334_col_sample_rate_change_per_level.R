setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################################
# This is to test that an error is thrown only when col_sample_rate_change_per_level
# <= 0 or > 2.0
######################################################################################
pubdev_5334_test <-
function() {

  tval <- runif(1,min=-3,max=3)
  print("col_sample_rate_change_per_level is set to ")
  tval <- -1
  print(tval)

  e <- tryCatch({
    training1_data <- h2o.importFile(locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    y_index <- h2o.ncol(training1_data)
    x_indices <- c(1:(y_index-1))
    training1_data["C14"] <- as.factor(training1_data["C14"])
    model <- h2o.gbm(x=x_indices, y=y_index, training_frame=training1_data, ntree=5,
    col_sample_rate_change_per_level=tval)
  }, error=function(error_message) {
    if (!grepl(pattern='_col_sample_rate_change_per_level', error_message) && ((tval <= 0) || (tval > 2)))
      FAIL(error_message)
    }
  )
}

doTest("Perform the test for pubdev 5334", pubdev_5334_test)
