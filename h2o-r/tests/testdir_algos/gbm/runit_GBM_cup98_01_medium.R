setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM <- function() {

  train.hex <- h2o.uploadFile(h2oTest.locate("bigdata/laptop/usecases/cup98LRN_z.csv"), destination_frame="cup98LRN_z.hex")
  test.hex  <- h2o.uploadFile(h2oTest.locate("bigdata/laptop/usecases/cup98VAL_z.csv"), destination_frame="cup98VAL_z.hex")

  # Train H2O GBM Model:
  train.hex$TARGET_B <- as.factor(train.hex$TARGET_B)
  y = "TARGET_B"
  excluded_column_names = c("", y, "TARGET_D", "CONTROLN")
  x = setdiff(colnames(train.hex), excluded_column_names)
  model <- h2o.gbm(training_frame = train.hex, y = y, x = x,
                   distribution = "multinomial", ntrees = 5)

  
}

h2oTest.doTest("GBM Test: KDD cup 98, test 01", test.GBM)
