setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.one.node.gbm <- function() {
  h2oTest.logInfo("Loading data and building models...")
  airs.hex <- h2o.importFile(h2oTest.locate("smalldata/airlines/allyears2k.zip"))

  e = tryCatch({
    gbm.sing <- h2o.gbm(x = 1:30, y = 31, training_frame = airs.hex,
      build_tree_one_node = T, seed = 1234)
    gbm.mult <- h2o.gbm(x = 1:30, y = 31, training_frame = airs.hex,
      build_tree_one_node = F, seed = 1234)
    NULL
    },
    error = function(err) { err }
  )

  if (!is.null(e[[1]])) {
    expect_match(e[[1]], "Cannot run on a single node in client mode")
  } else {
    h2oTest.logInfo("Multi Node:")
    print(paste("MSE:", h2o.mse(gbm.mult)))
    print(paste("AUC:", h2o.auc(gbm.mult)))
    print(paste("R^2:", h2o.r2(gbm.mult)))
    h2oTest.logInfo("Single Node:")
    print(paste("MSE:", h2o.mse(gbm.sing)))
    print(paste("AUC:", h2o.auc(gbm.sing)))
    print(paste("R^2:", h2o.r2(gbm.sing)))

    h2oTest.logInfo("MSE, AUC, and R2 should be the same...")
    expect_equal(h2o.mse(gbm.sing), h2o.mse(gbm.mult))
    expect_equal(h2o.auc(gbm.sing), h2o.auc(gbm.mult))
    expect_equal(h2o.r2(gbm.sing), h2o.r2(gbm.mult))
  }

  
}

h2oTest.doTest("Testing One Node vs Multi Node GBM", test.one.node.gbm)
