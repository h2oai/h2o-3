setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost <- function() {
  expect_true(h2o.xgboost.available())

  print("about to parse train")
  train.hex <- h2o.uploadFile(locate("bigdata/laptop/usecases/cup98LRN_z.csv"), destination_frame="cup98LRN_z.hex")
  print("about to parse test")
  test.hex  <- h2o.uploadFile(locate("bigdata/laptop/usecases/cup98VAL_z.csv"), destination_frame="cup98VAL_z.hex")

  # Train H2O XGBoost Model:
  train.hex$TARGET_B <- as.factor(train.hex$TARGET_B)
  y = "TARGET_B"
  excluded_column_names = c("", y, "TARGET_D", "CONTROLN")
  x = setdiff(colnames(train.hex), excluded_column_names)
  print("about to run xgboost")
  model <- h2o.xgboost(training_frame = train.hex, y = y, x = x,
                   distribution = "multinomial", ntrees = 5)
  print("done running xgboost")

  
}

doTest("XGBoost Test: KDD cup 98, test 01", test.XGBoost)
