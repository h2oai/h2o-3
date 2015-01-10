setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM <- function(conn) {
  # setwd("~/0xdata/ws/h2o-dev/h2o-r/tests/testdir_algos/gbm")
  # conn = h2o.init(startH2O = FALSE)
  
  train.hex <- h2o.uploadFile(conn, h2o.locate("bigdata/laptop/usecases/cup98LRN_z.csv"), key="cup98LRN_z.hex")
  test.hex  <- h2o.uploadFile(conn, h2o.locate("bigdata/laptop/usecases/cup98VAL_z.csv"), key="cup98VAL_z.hex")
  
  # Train H2O GBM Model:
  y = "TARGET_B"
  excluded_column_names = c("", y, "TARGET_D", "CONTROLN")
  x = setdiff(colnames(train.hex), excluded_column_names)
  model <- h2o.gbm(training_frame = train.hex, y = y, x = x,
                   loss = "AUTO", ntrees = 5)

  testEnd()
}

doTest("GBM Test: KDD cup 98, test 01", test.GBM)
