setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.xgboost.h.input.validation <- function() {
  # Check if XGBoost is available
  expect_true(h2o.xgboost.available())
  
  # Load prostate dataset
  prostate.hex <- h2o.importFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$GLEASON <- as.factor(prostate.hex$GLEASON)  # Make GLEASON categorical
  
  # Train an XGBoost model
  xgb.model <- h2o.xgboost(seed = 1234, 
                           x = c('AGE', 'PSA', 'DPROS', 'DCAPS', 'VOL', 'GLEASON'), 
                           y = "CAPSULE", 
                           training_frame = prostate.hex, 
                           ntrees = 5, 
                           max_depth = 5)
  
  # Test 1: NULL vars parameter
  Log.info("Test 1: NULL vars parameter should raise error")
  tryCatch({
    h2o.h(xgb.model, prostate.hex, NULL)
    stop("Should have raised an error for NULL vars parameter")
  }, error = function(e) {
    Log.info(paste("Test 1 passed:", e$message))
    expect_true(grepl("vars|variable|null|empty", e$message, ignore.case = TRUE))
  })
  
  # Test 2: Empty vars parameter
  Log.info("Test 2: Empty vars parameter should raise error")
  tryCatch({
    h2o.h(xgb.model, prostate.hex, c())
    stop("Should have raised an error for empty vars parameter")
  }, error = function(e) {
    Log.info(paste("Test 2 passed:", e$message))
    expect_true(grepl("vars|empty", e$message, ignore.case = TRUE))
  })
  
  # Test 3: Non-existent column
  Log.info("Test 3: Non-existent column should raise error")
  tryCatch({
    h2o.h(xgb.model, prostate.hex, c('AGE', 'NONEXISTENT_COLUMN'))
    stop("Should have raised an error for non-existent column")
  }, error = function(e) {
    Log.info(paste("Test 3 passed:", e$message))
    expect_true(grepl("does not exist|nonexistent", e$message, ignore.case = TRUE))
  })
  
  # Test 4: Non-numeric (categorical) column
  Log.info("Test 4: Non-numeric column should raise error")
  tryCatch({
    h2o.h(xgb.model, prostate.hex, c('AGE', 'GLEASON'))
    stop("Should have raised an error for non-numeric column")
  }, error = function(e) {
    Log.info(paste("Test 4 passed:", e$message))
    expect_true(grepl("not numeric|categorical", e$message, ignore.case = TRUE))
  })
  
  # Test 5: Valid case - should not raise error
  Log.info("Test 5: Valid input should work correctly")
  h_val <- h2o.h(xgb.model, prostate.hex, c('AGE', 'PSA'))
  Log.info(paste("H statistic value:", h_val))
  expect_true(h_val >= 0.0, info = "H statistic should be non-negative")
  
  Log.info("All input validation tests passed for XGBoost!")
}

doTest("XGBoost H statistic input validation test", test.xgboost.h.input.validation)
